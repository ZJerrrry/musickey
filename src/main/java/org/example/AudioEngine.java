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

    public void startBackgroundMelody(){
        if (bgRunning || channels == null) return;
        bgRunning = true;
        try{ channels[BG_CHANNEL].programChange(0); }catch(Exception ignored){}
        bgMelodyExec.scheduleWithFixedDelay(this::loopMainMelody, 0, 1, TimeUnit.MILLISECONDS);
    }
    private void loopMainMelody(){
        if(!bgRunning || channels==null) return;
        try {
            waitForNextBeat();
            // 使用 main melody 片段
            int[][] phrase = {
                    {72,100},{74,100},{76,100},{79,110},
                    {76,100},{74,100},{72,100},{67,90},
                    {69,100},{72,108},{74,108},{72,100},
                    {67,95},{69,100},{67,90},{64,85}
            };
            long step = beatLengthMs/4;
            MidiChannel ch = channels[BG_CHANNEL];
            for(int[] n: phrase){ if(!bgRunning) break; ch.noteOn(n[0], n[1]); Thread.sleep(step); ch.noteOff(n[0]); }
        } catch (InterruptedException ignored) {}
    }

    public void stopBackgroundMelody(){ bgRunning=false; }

    public void playPattern(Instrument instrument) {
        if (channels == null) return;
        // 玩家触发统一为两拍低音/节奏
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
        MidiChannel drum = channels[9];
        long step = beatLengthMs; // 一拍
        // 两拍内：KICK + HAT | SNARE + HAT
        drum.noteOn(35,120); drum.noteOn(42,70); Thread.sleep(step/2); drum.noteOff(35); drum.noteOff(42);
        drum.noteOn(42,70); Thread.sleep(step/2); drum.noteOff(42);
        drum.noteOn(38,115); drum.noteOn(46,85); Thread.sleep(step/2); drum.noteOff(38); drum.noteOff(46);
        drum.noteOn(42,70); Thread.sleep(step/2); drum.noteOff(42);
    }

    private void playTwoBeatBass(Instrument inst) throws InterruptedException {
        programIfNeeded(inst);
        MidiChannel ch = channels[inst.getChannel()];
        int root = 36 + (inst.getChannel()*2 % 12); // 简单变调
        long dur = beatLengthMs*2; // 两拍
        ch.noteOn(root, 110);
        Thread.sleep(dur);
        ch.noteOff(root);
    }

    public void playUltimateSequence() {
        if (channels == null) return;
        patternPool.submit(() -> {
            try {
                waitForNextBeat();
                long end = System.currentTimeMillis() + 5000; // 至少5秒
                MidiChannel lead = channels[0];
                MidiChannel guitar = channels[2];
                MidiChannel bass = channels[3];
                try { lead.programChange(30); guitar.programChange(29); bass.programChange(33);} catch(Exception ignored){}
                int[] powerChord = {52,59,64};
                for(int n: powerChord) guitar.noteOn(n,120);
                bass.noteOn(40,120);
                long step = beatLengthMs/8;
                int note = 72;
                while(System.currentTimeMillis()<end){
                    lead.noteOn(note, 127);
                    Thread.sleep(step);
                    lead.noteOff(note);
                    note += (Math.random()>0.6?2:1);
                    if (note>84) note=72+(int)(Math.random()*4);
                }
                for(int n: powerChord) guitar.noteOff(n);
                bass.noteOff(40);
            } catch (InterruptedException ignored) {}
        });
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
}
