package org.example;

import java.awt.*;

public class Instrument {
    private String name;
    private int damage;
    private String soundType;
    private int channel;
    private int program; // MIDI Program Change
    private EffectType effectType; // 特效类型

    public enum EffectType { FIREWORK, RIPPLE }

    public Instrument(String name, int damage, String soundType, int channel, int program, EffectType effectType) {
        this.name = name;
        this.damage = damage;
        this.soundType = soundType;
        this.channel = channel;
        this.program = program;
        this.effectType = effectType;
    }

    public String getName() { return name; }
    public int getDamage() { return damage; }
    public String getSoundType() { return soundType; }
    public int getChannel() { return channel; }
    public int getProgram(){ return program; }
    public EffectType getEffectType(){ return effectType; }

    public Color getColor() {
        switch(channel) {
            case 0: return Color.RED;
            case 1: return Color.BLUE;
            case 2: return Color.GREEN;
            case 3: return Color.YELLOW;
            case 4: return Color.MAGENTA;
            default: return Color.WHITE;
        }
    }
}
