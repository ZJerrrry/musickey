package org.example;

import javax.sound.midi.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** 音频引擎：根据乐器播放对应音色/模式（鼓点、钢琴琶音等） */
public class AudioEngine {
    private static final Logger LOGGER = Logger.getLogger(AudioEngine.class.getName());
    private Synthesizer synthesizer;
    private MidiChannel[] channels;
    private final ExecutorService patternPool = Executors.newCachedThreadPool();

    private volatile int bpm = 120; // 基准BPM
    private volatile long beatLengthMs; // 每拍毫秒
    private volatile long nextBeatTime; // 下一拍时间戳
    private final ScheduledExecutorService beatScheduler = Executors.newSingleThreadScheduledExecutor();
    private volatile long beatCounter = 0;

    private final ScheduledExecutorService bgMelodyExec = Executors.newSingleThreadScheduledExecutor();
    private volatile boolean bgRunning = false;
    private final int BG_CHANNEL = 5; // 预留后台主旋律通道
    private volatile int bgSectionIndex = 0;
    private final int[][] PAD_CHORDS = { {48,52,55,59}, {50,53,57,60}, {52,55,59,62}, {47,50,53,57} }; // 简单进行
    private final int[][] ARP_NOTES = { {72,76,79,83}, {74,77,81,84}, {76,79,83,86}, {71,74,77,81} };
    private final int[][] COUNTER_LINE = { {84,83,81,79}, {83,81,79,76}, {86,84,83,81}, {83,81,79,76} };

    private volatile double volumeScale = 1.0; // 0.0 - 1.0
    private double playerBoost = 1.35; // 玩家触发音量提升系数
    private double ultimateBoost = 1.6; // 终极技能额外提升

    // 新增：多段主旋律控制 (Sicko风格 A/B/C)
    private static final int SECTION_LENGTH_BARS = 4; // 每段4小节
    private static final int TOTAL_SECTIONS = 3; // A B C
    private volatile long barCounter = 0;
    private int barsPerChord = 1; // 和弦持续
    private int[] dropBassPattern = {36,36,36,38, 36,36,41,43};

    private Thread bgThread; // optimized background melody loop thread
    private volatile boolean paused = false; // 新增：整体暂停标志

    public AudioEngine() {
        try {
            synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();
            channels = synthesizer.getChannels();
        } catch (MidiUnavailableException e) {
            LOGGER.log(Level.SEVERE, "无法初始化合成器", e);
        }
        setBpm(bpm);
        startBeatClock();
    }

    public void setBpm(int bpm){
        this.bpm = bpm;
        this.beatLengthMs = 60000L / bpm;
        this.nextBeatTime = System.currentTimeMillis() + beatLengthMs;
    }

    private void startBeatClock(){
        beatScheduler.scheduleAtFixedRate(() -> {
            long now = System.currentTimeMillis();
            while (now >= nextBeatTime) {
                beatCounter++;
                nextBeatTime += beatLengthMs;
            }
        }, 0, 5, TimeUnit.MILLISECONDS);
    }

    private void waitForNextBeat() {
        try {
            while (true) {
                long now = System.currentTimeMillis();
                long remain = nextBeatTime - now;
                if (remain <= 2) break;
                Thread.sleep(Math.min(10, remain/2));
            }
        } catch (InterruptedException ignored) {}
    }

    public long currentBeat(){ return beatCounter; }
    public double progressToNextBeat(){
        long now = System.currentTimeMillis();
        long prevBeatStart = nextBeatTime - beatLengthMs;
        return Math.min(1.0, Math.max(0.0, (now - prevBeatStart) / (double)beatLengthMs));
    }

    public int getBpm(){ return bpm; }
    public void setVolume(int vol){ volumeScale = Math.max(0, Math.min(100, vol))/100.0; }
    public int getVolume(){ return (int)Math.round(volumeScale*100); }

    public void startBackgroundMelody(){
        if (bgRunning || channels == null) return;
        bgRunning = true;
        try{ channels[BG_CHANNEL].programChange(0); }catch(Exception ignored){}
        bgThread = new Thread(() -> {
            while(bgRunning){
                loopMainMelody();
            }
        }, "BG-Melody-Loop");
        bgThread.setDaemon(true);
        bgThread.start();
    }
    public void stopBackgroundMelody(){ bgRunning=false; }
    public void setPaused(boolean p){ this.paused = p; }

    private void loopMainMelody(){
        if(!bgRunning || channels==null) return;
        try {
            waitForNextBeat();
            if(paused){ return; }
            long currentBeat = currentBeat();
            boolean newBar = (currentBeat % 4)==0;
            if(newBar) barCounter++;
            int section = (int)((barCounter / SECTION_LENGTH_BARS) % TOTAL_SECTIONS);
            switch(section){
                case 0 -> playSectionA(currentBeat);
                case 1 -> playSectionB(currentBeat);
                case 2 -> playSectionC(currentBeat);
            }
        } catch (Exception ignored) {}
    }

    private void playSectionA(long beat) throws InterruptedException {
        // Sparse pad + occasional arp tail on bar end
        MidiChannel pad = channels[BG_CHANNEL];
        MidiChannel perc = channels[9];
        try { pad.programChange(91);}catch(Exception ignored){}
        int bar = (int)((beat/4)%SECTION_LENGTH_BARS);
        if(beat % 4 == 0){
            int root = switch(bar){
                case 0 -> 48;
                case 1 -> 50;
                case 2 -> 53;
                default -> 55;
            };
            int[] chord = {root, root+4, root+7, root+11};
            for(int n: chord) pad.noteOn(n,vs(60));
        }
        if((beat %4)==0 || (beat %4)==2){ perc.noteOn(35,vs(110)); }
        if((beat %4)==3){ int[] fill={72,76,79}; MidiChannel arp=channels[(BG_CHANNEL+1)%channels.length]; for(int n:fill){ arp.noteOn(n,vs(80)); Thread.sleep(beatLengthMs/6); arp.noteOff(n);} }
    }

    private void playSectionB(long beat) throws InterruptedException {
        // Busy syncopated arp + snare off-beat
        MidiChannel arp = channels[(BG_CHANNEL+1)%channels.length];
        MidiChannel drum = channels[9];
        try { arp.programChange(0);}catch(Exception ignored){}
        // 16分琶音循环
        int[] scale={72,74,76,79,81};
        long step = beatLengthMs/4; // 16分
        for(int i=0;i<4;i++){
            int note = scale[(int)((beat*4+i)%scale.length)];
            arp.noteOn(note,vs(90));
            Thread.sleep(step/2);
            arp.noteOff(note);
            Thread.sleep(step/2);
        }
        if((beat %4)==1 || (beat %4)==3){ drum.noteOn(38,vs(100)); }
        drum.noteOn(42,vs(50));
    }

    private void playSectionC(long beat) throws InterruptedException {
        // Drop: strong bass pulses + rapid hat + rising synth stab
        MidiChannel bass = channels[(BG_CHANNEL+2)%channels.length];
        MidiChannel drum = channels[9];
        try { bass.programChange(33);}catch(Exception ignored){}
        int idx = (int)(beat % dropBassPattern.length);
        int note = dropBassPattern[idx];
        bass.noteOn(note,vs(120));
        Thread.sleep(beatLengthMs/3);
        bass.noteOff(note);
        // trap style hats 3 subdivisions
        for(int i=0;i<3;i++){ drum.noteOn(42,vs(55)); Thread.sleep(beatLengthMs/6); }
        if((beat %2)==1){ drum.noteOn(39,vs(100)); }
    }

    /** Play an immediate short hit for player feedback (no beat wait). */
    public void playHitNote(Instrument inst){
        if(channels==null) return;
        int ch = inst.getChannel();
        if(ch<0 || ch>=channels.length) return;
        try { if(ch!=9) channels[ch].programChange(inst.getProgram()); } catch(Exception ignored){}
        int base = switch(inst.getSoundType()){
            case "鼓" -> -1; // percussion
            case "钢琴" -> 60;
            case "小提琴" -> 67;
            case "萨克斯" -> 62;
            case "贝斯" -> 40;
            default -> 60;
        };
        if(ch==9){ // percussion immediate hit
            int[] drums={35,38,42,46,49}; int note=drums[(int)(System.nanoTime()%drums.length)]; channels[9].noteOn(note, boostVel(115)); patternPool.submit(() -> { try { Thread.sleep(150); channels[9].noteOff(note);} catch(InterruptedException ignored){} }); return; }
        int note = base + (int)(System.nanoTime()%5); // simple small range
        channels[ch].noteOn(note, boostVel(105));
        // 叠加一个高八度弱音增强存在感（非鼓）
        int high = note+12; if(high<120) channels[ch].noteOn(high, boostVel(70));
        patternPool.submit(() -> { try { Thread.sleep(180); channels[ch].noteOff(note); if(high<120) channels[ch].noteOff(high);} catch(InterruptedException ignored){} });
    }

    public void playPattern(Instrument instrument) {
        if (channels == null) return;
        // 玩家触发统一为两拍低音 / 鼔点
        patternPool.submit(() -> {
            try {
                waitForNextBeat();
                if (instrument.getSoundType().contains("鼓")) {
                    playTwoBeatDrum();
                } else {
                    playTwoBeatBass(instrument);
                }
            } catch (Exception e){ LOGGER.log(Level.WARNING, "播放两拍底音失败", e); }
        });
    }

    private void playTwoBeatDrum() throws InterruptedException {
        MidiChannel drum = channels[9]; long step = beatLengthMs;
        drum.noteOn(35,boostVel(120)); drum.noteOn(42,boostVel(75)); Thread.sleep(step/2); drum.noteOff(35); drum.noteOff(42);
        drum.noteOn(42,boostVel(75)); Thread.sleep(step/2); drum.noteOff(42);
        drum.noteOn(38,boostVel(118)); drum.noteOn(46,boostVel(90)); Thread.sleep(step/2); drum.noteOff(38); drum.noteOff(46);
        drum.noteOn(42,boostVel(75)); Thread.sleep(step/2); drum.noteOff(42);
    }

    private void playTwoBeatBass(Instrument inst) throws InterruptedException {
        programIfNeeded(inst); MidiChannel ch = channels[inst.getChannel()]; int root = 36 + (inst.getChannel()*2 % 12); long dur = beatLengthMs*2; ch.noteOn(root,boostVel(115)); if(root+12<120) ch.noteOn(root+12, boostVel(70)); Thread.sleep(dur); ch.noteOff(root); if(root+12<120) ch.noteOff(root+12); }
    public void playUltimateSequence() {
        if (channels == null) return;
        patternPool.submit(() -> {
            try {
                waitForNextBeat();
                MidiChannel bass = channels[3];
                MidiChannel lead = channels[4];
                MidiChannel drum = channels[9];
                try { bass.programChange(33); lead.programChange(81);}catch(Exception ignored){}
                long total=5200; long start=System.currentTimeMillis(); long phase1 = 1600; long phase2 = 3600;
                int[] chord = {40,47,52,55};
                // Phase 1: 持续底座 + 渐进鼓
                while(System.currentTimeMillis()-start < phase1){
                    for(int n: chord) bass.noteOn(n, ultimateVel(100));
                    drum.noteOn(35, ultimateVel(127));
                    Thread.sleep(beatLengthMs/2);
                    drum.noteOn(42, ultimateVel(90));
                    Thread.sleep(beatLengthMs/2);
                    for(int n: chord) bass.noteOff(n);
                }
                // Phase 2: 快速上行炫技 + 叠加底鼓
                int[] scale={52,55,59,64,67,71,76,79,83,88}; int idx=0; long t2Start=System.currentTimeMillis();
                while(System.currentTimeMillis()-t2Start < (phase2-phase1)){
                    int note=scale[idx]; lead.noteOn(note, ultimateVel(120)); if(note+12<120) lead.noteOn(note+12, ultimateVel(95));
                    drum.noteOn(38, ultimateVel(120));
                    Thread.sleep(Math.max(40, beatLengthMs/6));
                    lead.noteOff(note); if(note+12<120) lead.noteOff(note+12);
                    idx=(idx+1)%scale.length;
                }
                // Final burst: 琶音扫弦 + 全鼓冲击
                int[] burst={52,55,59,64,67,71,76};
                for(int n: burst){ bass.noteOn(n, ultimateVel(127)); lead.noteOn(n+12<120? n+12 : n, ultimateVel(110)); Thread.sleep(60); }
                drum.noteOn(35,127); drum.noteOn(38,127); drum.noteOn(49,120); drum.noteOn(46,110);
                Thread.sleep(500);
                for(int n: burst){ bass.noteOff(n); if(n+12<120) lead.noteOff(n+12); }
            } catch (InterruptedException ignored) {}
        });
    }
    private int boostVel(int base){
        int v = (int)Math.round(base * playerBoost * volumeScale); if(v>127) v=127; return Math.max(0,v);
    }
    private int ultimateVel(int base){
        int v = (int)Math.round(base * playerBoost * ultimateBoost * volumeScale); if(v>127) v=127; return Math.max(0,v);
    }

    private void programIfNeeded(Instrument inst) {
        if (channels == null) return;
        int ch = inst.getChannel();
        if (ch >= 0 && ch < channels.length && ch != 9) { // 9 为打击乐
            channels[ch].programChange(inst.getProgram());
        }
    }

    private void singleNote(Instrument inst, int note, int durationMs) throws InterruptedException {
        programIfNeeded(inst);
        MidiChannel ch = channels[inst.getChannel()];
        ch.noteOn(note, 100);
        Thread.sleep(durationMs);
        ch.noteOff(note);
    }

    private void playArpPattern(Instrument inst, int[] notes) throws InterruptedException {
        programIfNeeded(inst);
        MidiChannel ch = channels[inst.getChannel()];
        int vel = 100;
        long step = beatLengthMs/4; // 16分音符
        for (int n : notes) {
            ch.noteOn(n, vel);
            Thread.sleep(step);
            ch.noteOff(n);
        }
    }

    private void playSwellPattern(Instrument inst, int root) throws InterruptedException {
        programIfNeeded(inst);
        MidiChannel ch = channels[inst.getChannel()];
        int[] add = {0, 7, 12};
        long step = beatLengthMs/6; // 渐强更平滑
        for (int v = 40; v <= 100; v += 15) {
            for (int a : add) ch.noteOn(root + a, v);
            Thread.sleep(step);
        }
        Thread.sleep(step*2);
        for (int a : add) ch.noteOff(root + a);
    }

    private void playBassPulse(Instrument inst, int[] seq) throws InterruptedException {
        programIfNeeded(inst);
        MidiChannel ch = channels[inst.getChannel()];
        long step = beatLengthMs/3;
        for (int n : seq) {
            ch.noteOn(n, 110);
            Thread.sleep(step);
            ch.noteOff(n);
        }
    }

    private void playDrumPattern() throws InterruptedException {
        MidiChannel drum = channels[9];
        long step = beatLengthMs/4; // 16分
        int[][] pattern = {
                {35, 120},
                {42, 80},
                {38, 110},
                {42, 80},
                {35, 120},
                {42, 80},
                {49, 100},
                {42, 70}
        };
        for (int[] ev : pattern) {
            drum.noteOn(ev[0], ev[1]);
            Thread.sleep(step);
            drum.noteOff(ev[0]);
        }
    }

    public void shutdown() {
        stopBackgroundMelody();
        patternPool.shutdownNow();
        beatScheduler.shutdownNow();
        bgMelodyExec.shutdownNow();
        if (synthesizer != null && synthesizer.isOpen()) synthesizer.close();
    }
    private int vs(int vel){ int v=(int)Math.round(vel * volumeScale); if(v>127) v=127; return Math.max(0,v); }
}
