package org.example;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/** 简单存档管理：保存/读取 JSON（手写轻量实现，无第三方依赖） */
public class SaveManager {
    private static final String FILE_NAME = System.getProperty("user.home") + "/.code_symphony_save.json";

    public static class SaveData {
        public int currentBossIndex;
        public long totalScore;
        public int bpm;
        public double skillCharge;
        public int comboCount;
        public double[] bossHealths; // 对应每个boss当前生命
        public long ultimateComboRemainMs; // 终极连击剩余
    }

    public static void save(SaveData data) {
        try {
            String json = toJson(data);
            Files.write(Paths.get(FILE_NAME), json.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {}
    }

    public static SaveData load(int bossCount, double[] maxHealths) {
        Path p = Paths.get(FILE_NAME);
        if (!Files.exists(p)) return null;
        try {
            String s = Files.readString(p, StandardCharsets.UTF_8).trim();
            if (s.isEmpty()) return null;
            return fromJson(s, bossCount, maxHealths);
        } catch (Exception e) {
            return null; // 读损坏忽略
        }
    }

    private static String esc(String v){ return v.replace("\\","\\\\").replace("\"","\\\""); }

    private static String toJson(SaveData d){
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append("\"currentBossIndex\":").append(d.currentBossIndex).append(',');
        sb.append("\"totalScore\":").append(d.totalScore).append(',');
        sb.append("\"bpm\":").append(d.bpm).append(',');
        sb.append("\"skillCharge\":").append(String.format("%.2f", d.skillCharge)).append(',');
        sb.append("\"comboCount\":").append(d.comboCount).append(',');
        sb.append("\"ultimateComboRemainMs\":").append(d.ultimateComboRemainMs).append(',');
        sb.append("\"bossHealths\":[");
        for(int i=0;i<d.bossHealths.length;i++){ if(i>0) sb.append(','); sb.append(String.format("%.0f", d.bossHealths[i])); }
        sb.append(']');
        sb.append('}');
        return sb.toString();
    }

    private static SaveData fromJson(String s, int bossCount, double[] maxHealths){
        SaveData d = new SaveData();
        // 简陋解析：按逗号与冒号拆分；不适合复杂JSON，但这里字段固定
        // 提取bossHealths数组
        int arrStart = s.indexOf("\"bossHealths\"");
        if(arrStart>=0){
            int lb = s.indexOf('[', arrStart); int rb = s.indexOf(']', lb);
            if(lb>0 && rb>lb){
                String arr = s.substring(lb+1, rb).trim();
                List<String> parts = arr.isEmpty()? List.of(): Arrays.stream(arr.split("\\s*,\\s*")).collect(Collectors.toList());
                d.bossHealths = new double[bossCount];
                for(int i=0;i<bossCount;i++){
                    if(i < parts.size()){
                        try { d.bossHealths[i] = Double.parseDouble(parts.get(i)); } catch(Exception e){ d.bossHealths[i] = maxHealths[i]; }
                    } else d.bossHealths[i] = maxHealths[i];
                }
            }
        }
        d.currentBossIndex = (int)extractNumber(s, "currentBossIndex", 0);
        d.totalScore = (long)extractNumber(s, "totalScore", 0);
        d.bpm = (int)extractNumber(s, "bpm", 120);
        d.skillCharge = extractNumber(s, "skillCharge", 0);
        d.comboCount = (int)extractNumber(s, "comboCount", 0);
        d.ultimateComboRemainMs = (long)extractNumber(s, "ultimateComboRemainMs", 0);
        if(d.bossHealths==null){
            d.bossHealths = maxHealths.clone();
        }
        // 修正索引
        if(d.currentBossIndex < 0 || d.currentBossIndex >= bossCount) d.currentBossIndex = 0;
        return d;
    }

    private static double extractNumber(String s, String key, double def){
        int idx = s.indexOf('"'+key+'"');
        if(idx<0) return def;
        int colon = s.indexOf(':', idx);
        if(colon<0) return def;
        int end = colon+1;
        while(end < s.length() && (Character.isWhitespace(s.charAt(end)) || s.charAt(end)==':' )) end++;
        int start=end;
        while(end < s.length() && "-+.0123456789".indexOf(s.charAt(end))>=0) end++;
        try { return Double.parseDouble(s.substring(start,end)); } catch(Exception e){ return def; }
    }
}

