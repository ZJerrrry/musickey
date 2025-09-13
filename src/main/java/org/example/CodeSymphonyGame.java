package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.logging.Logger;

import org.example.AttackEffects.*;

public class CodeSymphonyGame extends JFrame {
    private static final int WIDTH = 900;
    private static final int HEIGHT = 700;
    private static final Logger LOGGER = Logger.getLogger(CodeSymphonyGame.class.getName());

    private BossEntity boss; // 改为接口
    private List<BossEntity> bosses = new ArrayList<>();
    private int currentBossIndex = 0;
    private long totalScore = 0; // 汇总分数

    // 新增连击&特效字段
    private int comboCount = 0;
    private long lastTriggerTime = 0;
    private static final long COMBO_WINDOW_MS = 800; // 连击时间窗口
    private double comboMultiplier = 1.0;

    // 自动连击：按住键重复���发
    private final Map<Integer, javax.swing.Timer> holdTimers = new LinkedHashMap<>();
    private final Map<Integer, Boolean> keyHolding = new LinkedHashMap<>();

    // 屏幕抖动 & 低频光晕
    private double shakeIntensity = 0; // 0~1
    private double bassPulseAmp = 0;   // 低频脉冲幅度
    private double bassPulsePhase = 0; // 相位
    private final Random randFX = new Random();

    // 技能条 & 超级技能
    private double skillCharge = 0; // 0-100
    private boolean skillReady = false;
    private static final double SKILL_THRESHOLD = 100.0;
    private static final double SKILL_GAIN_PER_HIT = 6.5; // 基础获得
    private static final double SKILL_COMBO_BONUS = 0.25; // 每连击额外百分比

    // Boss 阶段反击
    private int bossPhase = 0; // 0..3
    private boolean counterActive = false;
    private long counterEndTime = 0;
    private boolean counterResolved = false;
    private static final long COUNTER_WINDOW_MS = 1500; // 格挡时间

    // 记录最后一���血量阶段
    private double lastBossHealthPct = 1.0;

    // 减速 & 暗化
    private double slowFactor = 1.0; // <1 表示减速（延长触发间隔）
    private long slowEndTime = 0;
    private float darkAlpha = 0f; // 屏幕暗化层

    // 缺失字段补充
    private Map<Integer, Instrument> instruments; // 乐器表
    private JPanel gamePanel; // 主画布
    private AudioEngine audioEngine; // 音频引擎
    private final List<AttackEffect> activeEffects = new ArrayList<>(); // 特效列表
    private long lastUpdate = System.currentTimeMillis(); // 上次帧时间
    private int score = 0; // 当前Boss内得分
    private int lastUsedInstrumentIndex = -1; // 最近使用乐器索引

    // Boss专属技能状态
    private enum BossSkillType { NONE, ABSORB, REFLECT, CORE_PULSE }
    private BossSkillType bossSkill = BossSkillType.NONE;
    private long bossSkillEnd = 0;
    private boolean reflectActive = false;
    private double absorbAccum = 0; // 吸收期间累计伤害
    private long nextSkillTime = 0; // 下次技能触发时间

    // 终极技能屏幕标记
    private boolean ultimateActive = false;
    private long ultimateEnd = 0;

    public CodeSymphonyGame() {
        setTitle("音乐编程大作战 - 代码交响曲");
        setSize(WIDTH, HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setResizable(false);

        audioEngine = new AudioEngine();
        initGame();
        initUI();
        setupKeyBindings();
        startAnimationLoop();

        setVisible(true);
    }

    private void initGame() {
        createBosses();
        boss = bosses.get(0);
        instruments = new LinkedHashMap<>(); // 保��顺序

        // 新构造: name, damage, soundType, channel, program, effectType
        // 通道分配：鼓用9（打击乐），其它乐器各自独立通道
        instruments.put(0, new Instrument("循环鼓 (A)", 10000, "鼓", 9, 0, Instrument.EffectType.RIPPLE));
        instruments.put(1, new Instrument("函数琴 (S)", 16000, "钢琴", 0, 0, Instrument.EffectType.FIREWORK));
        instruments.put(2, new Instrument("变量提琴 (D)", 20000, "小提琴", 1, 40, Instrument.EffectType.RIPPLE));
        instruments.put(3, new Instrument("递归号 (F)", 24000, "萨克斯", 2, 65, Instrument.EffectType.FIREWORK));
        instruments.put(4, new Instrument("并发贝斯 (G)", 30000, "贝斯", 3, 33, Instrument.EffectType.RIPPLE));
    }

    private void createBosses(){
        bosses.clear();
        bosses.add(new BugBoss(12_000_000, "code"));
        bosses.add(new MatrixBoss(18_000_000, "matrix"));
        bosses.add(new NeuralCoreBoss(25_000_000, "core"));
    }

    private void initUI() {
        gamePanel = new GamePanel();
        add(gamePanel, BorderLayout.CENTER);

        JPanel controlPanel = new JPanel(new GridLayout(1, instruments.size()));
        for (int i = 0; i < instruments.size(); i++) {
            final int idx = i;
            JButton button = new JButton(instruments.get(i).getName());
            button.addActionListener(e -> triggerInstrument(idx));
            controlPanel.add(button);
        }
        add(controlPanel, BorderLayout.SOUTH);
    }

    private void setupKeyBindings() {
        char[] keys = {'A','S','D','F','G'};
        for (int i = 0; i < instruments.size(); i++) {
            final int idx = i;
            char key = keys[i];
            // 按下（触发 + 启动长按计时器）
            addKeyBinding(gamePanel, KeyStroke.getKeyStroke(key, 0, false), "PRESS_"+key, () -> handlePress(idx));
            addKeyBinding(gamePanel, KeyStroke.getKeyStroke(Character.toLowerCase(key), 0, false), "PRESS_LOW_"+key, () -> handlePress(idx));
            // 释放（停止长按）
            addKeyBinding(gamePanel, KeyStroke.getKeyStroke(KeyEvent.getExtendedKeyCodeForChar(key), 0, true), "RELEASE_"+key, () -> handleRelease(idx));
            addKeyBinding(gamePanel, KeyStroke.getKeyStroke(KeyEvent.getExtendedKeyCodeForChar(Character.toLowerCase(key)), 0, true), "RELEASE_LOW_"+key, () -> handleRelease(idx));
        }
        // 超级技能 Q
        addKeyBinding(gamePanel, KeyStroke.getKeyStroke('Q'), "SUPER_Q", this::triggerSuperSkill);
        addKeyBinding(gamePanel, KeyStroke.getKeyStroke('q'), "SUPER_q", this::triggerSuperSkill);
        // 反击格挡 SPACE
        addKeyBinding(gamePanel, KeyStroke.getKeyStroke(KeyEvent.VK_SPACE,0), "COUNTER_SPACE", this::attemptCounterResolve);
        // BPM 调整 上下键
        addKeyBinding(gamePanel, KeyStroke.getKeyStroke(KeyEvent.VK_UP,0), "BPM_UP", () -> changeBpm(4));
        addKeyBinding(gamePanel, KeyStroke.getKeyStroke(KeyEvent.VK_DOWN,0), "BPM_DOWN", () -> changeBpm(-4));

        gamePanel.setFocusable(true);
        gamePanel.requestFocusInWindow();
    }

    private void addKeyBinding(JComponent comp, KeyStroke ks, String id, Runnable run){
        InputMap im = comp.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = comp.getActionMap();
        im.put(ks, id);
        am.put(id, new AbstractAction(){
            @Override public void actionPerformed(ActionEvent e){ run.run(); }
        });
    }

    // 动态��复间隔（减速后更长）
    private int computeRepeatIntervalMs(){
        double base = 160; // 原周期间隔
        if (slowFactor < 1.0) {
            return (int)(base / slowFactor); // slowFactor=0.5 => 320ms
        }
        return (int)base;
    }

    private void handlePress(int idx){
        keyHolding.put(idx, true);
        triggerInstrument(idx);
        javax.swing.Timer t = new javax.swing.Timer(computeRepeatIntervalMs(), e -> {
            if(Boolean.TRUE.equals(keyHolding.get(idx))) {
                triggerInstrument(idx);
            }
        });
        t.setInitialDelay((int)(computeRepeatIntervalMs()*1.6));
        t.start();
        holdTimers.put(idx, t);
    }

    private void handleRelease(int idx){
        keyHolding.put(idx, false);
        javax.swing.Timer t = holdTimers.remove(idx);
        if (t != null) t.stop();
    }

    private void attemptCounterResolve(){
        if (counterActive && !counterResolved) {
            counterResolved = true; // 成功格挡
            // 奖励：增加技能能量与轻微抖动
            skillCharge = Math.min(SKILL_THRESHOLD, skillCharge + 25);
            shakeIntensity = Math.min(1.0, shakeIntensity + 0.3);
        }
    }

    private void updateBossPhaseIfNeeded(){
        double hpPct = (double)boss.getHealth()/boss.getMaxHealth();
        int phase = hpPct > 0.7 ? 0 : hpPct > 0.4 ? 1 : hpPct > 0.15 ? 2 : 3;
        if (phase != bossPhase) {
            bossPhase = phase;
            launchCounterAttack();
        }
    }

    private void launchCounterAttack(){
        counterActive = true; counterResolved = false; counterEndTime = System.currentTimeMillis() + COUNTER_WINDOW_MS;
        synchronized (activeEffects){ activeEffects.add(new AttackEffects.ShockwaveEffect(getWidth(), getHeight())); }
    }

    private void finishCounterIfTimeout(){
        if (counterActive && System.currentTimeMillis() > counterEndTime) {
            counterActive = false;
            if (!counterResolved) {
                // 未格挡惩罚：清空部分连击与技能能量
                comboCount = 0;
                comboMultiplier = 1.0;
                skillCharge = Math.max(0, skillCharge - 30);
                shakeIntensity = Math.min(1.0, shakeIntensity + 0.2);
                // 反击失败附加：减速4秒 + 暗化
                slowFactor = 0.5;
                slowEndTime = System.currentTimeMillis() + 4000;
                darkAlpha = 0.65f;
            }
        }
        if (counterActive && counterResolved) {
            // 成功后立即结束窗口
            counterActive = false;
        }
    }

    private void changeBpm(int delta){
        int newBpm = Math.max(60, Math.min(180, audioEngine.getBpm() + delta));
        audioEngine.setBpm(newBpm);
    }

    private void triggerSuperSkill() {
        if (!skillReady) return;
        skillReady = false; skillCharge = 0;
        Instrument last = instruments.get(lastUsedInstrumentIndex >=0 ? lastUsedInstrumentIndex : 1);
        // 屏幕覆盖与音乐
        synchronized (activeEffects) { activeEffects.add(new AttackEffects.UltimateOverlayEffect(last.getColor())); }
        audioEngine.playUltimateSequence();
        ultimateActive = true; ultimateEnd = System.currentTimeMillis() + 1800;
        AttackEffect eff = (last.getEffectType()== Instrument.EffectType.FIREWORK)
                ? new AttackEffects.SuperFireworkEffect(getWidth(), getHeight(), last.getColor())
                : new AttackEffects.FullScreenRippleEffect(getWidth(), getHeight(), last.getColor());
        synchronized (activeEffects) { activeEffects.add(eff); }
        int bonusDmg = (int)(boss.getMaxHealth() * 0.02 + comboCount * 500);
        applyBossDamage(bonusDmg);
        shakeIntensity = Math.min(1.0, shakeIntensity + 0.5);
    }

    private void scheduleNextBossSkill(){
        long now = System.currentTimeMillis();
        // 每 8~14 秒之间触发一次（取随机+放松体验）
        nextSkillTime = now + 8000 + new Random().nextInt(6000);
    }

    private void maybeActivateBossSkill(){
        long now = System.currentTimeMillis();
        if (now < nextSkillTime || bossSkill != BossSkillType.NONE || counterActive) return;
        // 根据Boss类型选择技能
        if (boss instanceof BugBoss) {
            bossSkill = BossSkillType.ABSORB; // 吸收波��
            bossSkillEnd = now + 3000;
            absorbAccum = 0;
        } else if (boss instanceof MatrixBoss) {
            bossSkill = BossSkillType.REFLECT; // 反射
            bossSkillEnd = now + 2500;
            reflectActive = true;
        } else if (boss instanceof NeuralCoreBoss) {
            bossSkill = BossSkillType.CORE_PULSE; // 脉冲
            bossSkillEnd = now + 3200;
            synchronized (activeEffects){ activeEffects.add(new AttackEffects.CorePulseEffect(getWidth(), getHeight(), new Color(120,200,255))); }
        }
    }

    private void updateBossSkillState(){
        long now = System.currentTimeMillis();
        if (bossSkill != BossSkillType.NONE && now > bossSkillEnd){
            // 技能结束效果
            if (bossSkill == BossSkillType.ABSORB && absorbAccum > 0){
                // 吸收后释放，转化为伤害返还（可被 SPACE 格挡，使用与反击共通��
                boss.takeDamage(-(int)Math.min(boss.getMaxHealth()*0.01, absorbAccum*0.5)); // 吸收给自己回血上限1%
            }
            if (bossSkill == BossSkillType.REFLECT){
                reflectActive = false;
            }
            bossSkill = BossSkillType.NONE;
            scheduleNextBossSkill();
        }
    }

    private void applyBossDamage(int dmg){
        // 处理技能态：吸收/反射
        if (bossSkill == BossSkillType.ABSORB){
            absorbAccum += dmg;
            shakeIntensity = Math.min(1.0, shakeIntensity + dmg / (double)boss.getMaxHealth());
            return; // 不直接伤害
        }
        if (bossSkill == BossSkillType.REFLECT && reflectActive){
            // 反射：伤害打回来（可被空间格挡? 简化为直接扣连击）
            comboCount = Math.max(0, comboCount - 5);
            return;
        }
        boss.takeDamage(dmg);
        score += dmg; totalScore += dmg;
        if (boss.getHealth() <= 0) onBossDefeated();
    }

    private void triggerInstrument(int instrumentIndex) {
        Instrument instrument = instruments.get(instrumentIndex);
        if (instrument == null) return;
        if (slowFactor < 1.0 && System.currentTimeMillis() > slowEndTime) slowFactor = 1.0;
        maybeActivateBossSkill();
        long now = System.currentTimeMillis();
        if (now - lastTriggerTime <= COMBO_WINDOW_MS) comboCount++; else comboCount = 1;
        lastTriggerTime = now;
        comboMultiplier = 1.0 + Math.min(1.5, comboCount * 0.05);
        audioEngine.playPattern(instrument);
        int baseDamage = instrument.getDamage();
        int finalDamage = (int)Math.round(baseDamage * comboMultiplier * slowFactor);
        applyBossDamage(finalDamage);
        shakeIntensity = Math.min(1.0, shakeIntensity + finalDamage / 6_000_000.0);
        if (instrument.getSoundType().contains("鼓") || instrument.getSoundType().contains("贝斯")) bassPulseAmp = Math.min(1.0, bassPulseAmp + 0.35);
        spawnEffectForInstrument(instrument);
        double gain = SKILL_GAIN_PER_HIT + comboCount * SKILL_COMBO_BONUS;
        skillCharge = Math.min(SKILL_THRESHOLD, skillCharge + gain);
        if (skillCharge >= SKILL_THRESHOLD) skillReady = true;
        lastUsedInstrumentIndex = instrumentIndex;
    }

    private void onBossDefeated(){
        comboCount = 0; comboMultiplier = 1.0; skillCharge = 0; skillReady = false; bossSkill = BossSkillType.NONE; reflectActive=false; absorbAccum=0;
        String msg = "击败Boss: " + boss.getName() + "\n当前总分: " + totalScore + "\n是否继续下一个Boss?";
        int option = JOptionPane.showOptionDialog(this, msg, "Boss 击败", JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null, new Object[]{"下一关","退出"}, "下一关");
        if (option == JOptionPane.YES_OPTION) {
            currentBossIndex++;
            if (currentBossIndex < bosses.size()) {
                boss = bosses.get(currentBossIndex);
                activeEffects.clear(); darkAlpha = 0f; slowFactor = 1.0; shakeIntensity = 0; bassPulseAmp = 0; scheduleNextBossSkill();
                gamePanel.requestFocusInWindow();
            } else {
                JOptionPane.showMessageDialog(this, "全部Boss已通关! 总分: " + totalScore);
                audioEngine.shutdown(); System.exit(0);
            }
        } else { audioEngine.shutdown(); System.exit(0); }
    }

    private void spawnEffectForInstrument(Instrument instrument) {
        int centerX = getWidth()/2;
        int centerY = getHeight()/3; // Boss中心附近
        Random r = new Random();
        int offsetX = -140 + r.nextInt(280);
        int offsetY = -80 + r.nextInt(160);
        int x = centerX + offsetX;
        int y = centerY + offsetY;

        AttackEffect effect;
        if (instrument.getEffectType() == Instrument.EffectType.FIREWORK) {
            effect = new AttackEffects.FireworkEffect(x, y, instrument.getColor());
        } else {
            effect = new AttackEffects.RippleEffect(centerX, centerY, instrument.getColor());
        }
        synchronized (activeEffects) {
            activeEffects.add(effect);
        }
    }

    private void startAnimationLoop() {
        scheduleNextBossSkill();
        javax.swing.Timer timer = new javax.swing.Timer(16, e -> {
            long now = System.currentTimeMillis();
            long dt = now - lastUpdate; lastUpdate = now;
            updateEffects(dt);
            shakeIntensity *= 0.90; if (shakeIntensity < 0.001) shakeIntensity = 0;
            bassPulseAmp *= 0.92; bassPulsePhase += dt / 1000.0 * 2 * Math.PI * 1.2;
            boss.update(dt);
            updateBossPhaseIfNeeded();
            finishCounterIfTimeout();
            updateBossSkillState();
            if (slowFactor < 1.0 && System.currentTimeMillis() > slowEndTime) slowFactor = 1.0;
            if (darkAlpha > 0f) darkAlpha *= 0.92f;
            if (ultimateActive && now > ultimateEnd) ultimateActive = false;
            gamePanel.repaint();
        });
        timer.start();
    }

    private void updateEffects(long dt) {
        synchronized (activeEffects) {
            Iterator<AttackEffect> it = activeEffects.iterator();
            while (it.hasNext()) {
                AttackEffect ef = it.next();
                ef.update(dt);
                if (!ef.isAlive()) it.remove();
            }
        }
    }

    private class GamePanel extends JPanel {
        public GamePanel() {
            setBackground(Color.BLACK);
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2d = (Graphics2D) g;
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // 背景
            Paint old = g2d.getPaint();
            GradientPaint bg = new GradientPaint(0,0,new Color(10,10,25),0,getHeight(),new Color(30,0,50));
            g2d.setPaint(bg); g2d.fillRect(0,0,getWidth(),getHeight()); g2d.setPaint(old);

            // 低频光晕 (全屏中心渐变叠加)
            if (bassPulseAmp > 0.02) {
                float pulse = (float)(bassPulseAmp * (0.6 + 0.4 * Math.sin(bassPulsePhase)));
                int radius = (int)(Math.min(getWidth(), getHeight()) * (0.45 + 0.15 * pulse));
                RadialGradientPaint glow = new RadialGradientPaint(new Point(getWidth()/2, getHeight()/2), radius,
                        new float[]{0f,0.6f,1f}, new Color[]{
                                new Color(120,40,180,(int)(120*pulse)),
                                new Color(80,0,100,(int)(60*pulse)),
                                new Color(30,0,50,0)});
                g2d.setPaint(glow);
                g2d.fillOval(getWidth()/2 - radius, getHeight()/2 - radius, radius*2, radius*2);
                g2d.setPaint(old);
            }

            // 星点
            g2d.setColor(new Color(255,255,255,40));
            for(int i=0;i<60;i++){
                int sx = (i*73)%getWidth();
                int sy = (i*149)%getHeight();
                g2d.fillRect(sx, sy, 2,2);
            }

            // 计算抖动偏移
            int shakeX = 0, shakeY = 0;
            if (shakeIntensity > 0) {
                double mag = shakeIntensity * 12.0;
                shakeX = (int)((randFX.nextDouble()-0.5)*mag*2);
                shakeY = (int)((randFX.nextDouble()-0.5)*mag*2);
            }
            g2d.translate(shakeX, shakeY);

            // Boss
            boss.draw(g2d, getWidth()/2, getHeight()/3);

            // 还原平移用于UI
            g2d.translate(-shakeX, -shakeY);

            // 血条
            int barW = 500; int barX = getWidth()/2 - barW/2; int barY = 40;
            double hpPct = (double)boss.getHealth()/boss.getMaxHealth();
            g2d.setColor(new Color(70,0,90)); g2d.fillRoundRect(barX, barY, barW, 24, 12,12);
            g2d.setColor(new Color(180,40,220)); g2d.fillRoundRect(barX, barY, (int)(barW*hpPct), 24, 12,12);
            g2d.setColor(Color.WHITE); g2d.drawRoundRect(barX, barY, barW, 24, 12,12);
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g2d.drawString("BOSS(" + (currentBossIndex+1) + "/" + bosses.size() + ") " + boss.getName() + " HP: " + boss.getHealth() + " / " + boss.getMaxHealth(), barX+10, barY+17);
            // 显示减速状态
            if (slowFactor < 1.0) {
                g2d.setColor(new Color(255,200,120));
                g2d.drawString("减速中", barX + barW - 70, barY + 17);
            }
            // 总分
            g2d.drawString("TOTAL: " + totalScore, 20, getHeight()-50);
            // 暗化覆盖层
            if (darkAlpha > 0.02f) {
                g2d.setColor(new Color(0,0,0, Math.min(200, (int)(darkAlpha*255))));
                g2d.fillRect(0,0,getWidth(),getHeight());
            }

            // 分数 & 连击
            g2d.drawString("SCORE: "+score, 20, getHeight()-30);
            if (comboCount > 1) {
                String comboStr = comboCount + " COMBO x" + String.format("%.2f", comboMultiplier);
                g2d.setFont(new Font("Monospaced", Font.BOLD, 22));
                float alpha = (float)Math.min(1.0, 0.3 + comboCount/30.0);
                g2d.setColor(new Color(255, 220, 120, (int)(alpha*255)));
                g2d.drawString(comboStr, 20, getHeight()-60);
            }

            // ��示
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 14));
            g2d.drawString("按 A S D F G (可长按) 触发乐器攻击 | 连击窗口: " + COMBO_WINDOW_MS + "ms", 20, getHeight()-80);

            // 技能条
            int skillBarW = 260; int skillBarH = 16; int skillX = getWidth()-skillBarW-30; int skillY = getHeight()-60;
            g2d.setColor(new Color(40,40,60));
            g2d.fillRoundRect(skillX, skillY, skillBarW, skillBarH, 10,10);
            double scPct = skillCharge / SKILL_THRESHOLD;
            g2d.setColor(skillReady ? new Color(255,200,60) : new Color(120,140,255));
            g2d.fillRoundRect(skillX, skillY, (int)(skillBarW*scPct), skillBarH, 10,10);
            g2d.setColor(Color.WHITE);
            g2d.drawRoundRect(skillX, skillY, skillBarW, skillBarH, 10,10);
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 12));
            g2d.drawString(skillReady?"Q 释放超级技能":"技能: "+(int)skillCharge+"%", skillX+10, skillY+12);

            // 反击提示
            if (counterActive) {
                long remain = Math.max(0, counterEndTime - System.currentTimeMillis());
                String txt = counterResolved?"已格挡":"SPACE 格挡反击:" + remain + "ms";
                g2d.setColor(counterResolved?new Color(120,255,160):new Color(255,120,120));
                g2d.drawString(txt, skillX, skillY - 20);
            }

            // 拍点指示（屏幕顶部小节进度条）
            double beatProg = audioEngine.progressToNextBeat();
            int beatBarW = 160; int beatBarH = 6; int beatX = getWidth()/2 - beatBarW/2; int beatY = 12;
            g2d.setColor(new Color(50,50,70));
            g2d.fillRoundRect(beatX, beatY, beatBarW, beatBarH, 8,8);
            g2d.setColor(new Color(200,220,255));
            g2d.fillRoundRect(beatX, beatY, (int)(beatBarW * beatProg), beatBarH, 8,8);
            g2d.setColor(Color.WHITE);
            g2d.drawRoundRect(beatX, beatY, beatBarW, beatBarH, 8,8);

            // ���效
            synchronized (activeEffects) {
                for (AttackEffect ef : activeEffects) { ef.draw(g2d); }
            }

            // Boss技能状态提示
            if (bossSkill != BossSkillType.NONE) {
                String skillTxt = bossSkill==BossSkillType.ABSORB?"Boss吸收中": bossSkill==BossSkillType.REFLECT?"Boss反射中": "核心脉冲";
                g2d.setColor(new Color(255,240,180));
                g2d.drawString(skillTxt, getWidth()-180, 30);
            }
            // 显示BPM
            g2d.setColor(Color.WHITE);
            g2d.drawString("BPM:"+audioEngine.getBpm()+" ↑↓调整", getWidth()-170, getHeight()-30);
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        audioEngine.shutdown();
    }
}
