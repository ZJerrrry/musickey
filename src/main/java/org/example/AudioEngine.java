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

    public AudioEngine() {
        try {
            synthesizer = MidiSystem.getSynthesizer();
            synthesizer.open();
            channels = synthesizer.getChannels();
        } catch (MidiUnavailableException e) {
            LOGGER.log(Level.SEVERE, "无法初始化合成器", e);
        }
    }

    public void playPattern(Instrument instrument) {
        if (channels == null) return;
        patternPool.submit(() -> {
            try {
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
                    singleNote(instrument, 60, 800);
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
        for (int n : notes) {
            ch.noteOn(n, vel);
            Thread.sleep(110);
            ch.noteOff(n);
        }
    }

    private void playSwellPattern(Instrument inst, int root) throws InterruptedException {
        programIfNeeded(inst);
        MidiChannel ch = channels[inst.getChannel()];
        int[] add = {0, 7, 12};
        for (int v = 40; v <= 100; v += 15) {
            for (int a : add) ch.noteOn(root + a, v);
            Thread.sleep(90);
        }
        Thread.sleep(200);
        for (int a : add) ch.noteOff(root + a);
    }

    private void playBassPulse(Instrument inst, int[] seq) throws InterruptedException {
        programIfNeeded(inst);
        MidiChannel ch = channels[inst.getChannel()];
        for (int n : seq) {
            ch.noteOn(n, 110);
            Thread.sleep(150);
            ch.noteOff(n);
        }
    }

    private void playDrumPattern() throws InterruptedException {
        // 使用通道9打击乐，常见号码：35 BD, 38 SN, 42 CH, 46 OH, 49 CRASH
        MidiChannel drum = channels[9];
        int[][] pattern = {
                {35, 120}, {42, 80},
                {38, 110}, {42, 80},
                {35, 120}, {42, 80},
                {49, 100}
        };
        for (int[] ev : pattern) {
            drum.noteOn(ev[0], ev[1]);
            Thread.sleep(95);
            drum.noteOff(ev[0]);
        }
    }

    public void shutdown() {
        patternPool.shutdownNow();
        if (synthesizer != null && synthesizer.isOpen()) synthesizer.close();
    }
}

