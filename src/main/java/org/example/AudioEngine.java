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

    public void playPattern(Instrument instrument) {
        if (channels == null) return;
        patternPool.submit(() -> {
            try {
                waitForNextBeat(); // 对齐拍点启动
                if (instrument.getSoundType().contains("鼓")) {
                    playDrumPattern();
                } else if (instrument.getSoundType().contains("钢琴")) {
                    playArpPattern(instrument, new int[]{60, 64, 67, 72});
                } else if (instrument.getSoundType().contains("小提琴")) {
                    playSwellPattern(instrument, 55);
                } else if (instrument.getSoundType().contains("萨克斯")) {
                    playArpPattern(instrument, new int[]{62, 65, 69});
                } else if (instrument.getSoundType().contains("贝斯")) {
                    playBassPulse(instrument, new int[]{36, 43, 40});
                } else {
                    singleNote(instrument, 60, (int)beatLengthMs);
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "播放模式失败", e);
            }
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
        patternPool.shutdownNow();
        beatScheduler.shutdownNow();
        if (synthesizer != null && synthesizer.isOpen()) synthesizer.close();
    }
}

