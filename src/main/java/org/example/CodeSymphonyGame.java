package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.geom.CubicCurve2D;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
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

    // 自动连击：按住键重复触发
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
    private static final double SKILL_THRESHOLD = 300.0; // 三倍蓄力
    private static final double SKILL_GAIN_PER_HIT = 6.5; // 基础获得不变但阈值提高意味着更久
    private static final double SKILL_COMBO_BONUS = 0.25; // 补回每连击额外百分比

    // Boss 阶段反击
    private int bossPhase = 0; // 0..3
    private boolean counterActive = false;
    private long counterEndTime = 0;
    private boolean counterResolved = false;
    private static final long COUNTER_WINDOW_MS = 1500; // 格挡时间

    // 记录最后一次血量阶段
    private double lastBossHealthPct = 1.0;

    // 减速 & 暗化
    private double slowFactor = 1.0; // <1 表示减速（延长触发间隔）
    private long slowEndTime = 0;
    private float darkAlpha = 0f; // 屏幕暗化层

    // 缺失字段补充
    private Map<Integer, Instrument> instruments; // 乐器表
    private JPanel gamePanel; // 主画布
    private AudioEngine audioEngine; // 音频引擎
    // 改为无锁集合 -> 再改回普通 ArrayList 提升频繁增删性能
    private final java.util.List<AttackEffect> activeEffects = new java.util.ArrayList<>();
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

    // 新增：终极技能连击倍率加倍状态
    private boolean ultimateComboBoost = false;
    private long ultimateComboEnd = 0;

    // Telegraph 提示相关
    private AttackEffects.CircleTelegraphEffect pendingTelegraph = null;
    private boolean telegraphSatisfied = false;
    private String telegraphKey = "SPACE";

    // 新增：Boss 切换保护，防止同帧多次判定
    private boolean bossSwitching = false;

    // 当前存档数据引用
    private SaveManager.SaveData saveData;

    // 可调上限
    private int bpmMax = 180;
    private Font uiFont;

    // 字体缓存
    private Font fontMono14, fontMono16, fontMono22Bold, fontMono12;

    // 星点缓存
    private int[] starX, starY; private int starCount=70;

    private static class Projectile {
        double x,y; final double tx,ty; final double speed; final int damage; final Color color; boolean hit=false; Instrument inst;
        final java.util.Deque<Point> trail = new java.util.ArrayDeque<>(); // 拖尾
        Projectile(double x,double y,double tx,double ty,double speed,int damage,Color c, Instrument inst){this.x=x;this.y=y;this.tx=tx;this.ty=ty;this.speed=speed;this.damage=damage;this.color=c;this.inst=inst;}
    }
    private final java.util.List<Projectile> projectiles = new CopyOnWriteArrayList<>();

    // 性能自适应
    private enum QualityLevel { HIGH, MED, LOW }
    private QualityLevel quality = QualityLevel.HIGH;
    private double avgFrameMs = 16.0; // 平滑帧时间
    private long perfLastAdjust = 0;
    private static final double PERF_SMOOTH = 0.08; // 平滑因子
    // 调试 HUD 开关
    private boolean showDebugHud = false;
    // 终极技能强制质量
    private QualityLevel qualityBeforeUltimate = QualityLevel.HIGH;
    private boolean qualityForced = false;
    // 低质量模式投射物节流计数
    private int lowQualityProjectileSkipCounter = 0;

    // ====== 新增性能相关字段 ======
    private boolean frameSkipToggle = false; // 低质量帧跳过
    private int frameCounter = 0; // 用于降低部分绘制频率
    private boolean projectileUpdateToggle = false; // LOW 模式抛射物逻辑隔帧
    // ===========================

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
        double[] maxHealths = bosses.stream().mapToDouble(BossEntity::getMaxHealth).toArray();
        SaveManager.SaveData loaded = SaveManager.load(bosses.size(), maxHealths);
        if (loaded != null) { this.saveData = loaded; currentBossIndex = loaded.currentBossIndex; bpmMax = loaded.bpmMax; }
        boss = bosses.get(currentBossIndex);
        if (loaded != null && loaded.bossHealths != null && currentBossIndex < loaded.bossHealths.length) {
            boss.setHealth((int)loaded.bossHealths[currentBossIndex]);
            totalScore = loaded.totalScore; audioEngine.setBpm(Math.min(loaded.bpm, bpmMax)); audioEngine.setVolume(loaded.volume);
            skillCharge = loaded.skillCharge; comboCount = loaded.comboCount;
            if (loaded.ultimateComboRemainMs > 0) { ultimateComboBoost = true; ultimateComboEnd = System.currentTimeMillis() + loaded.ultimateComboRemainMs; }
        }
        instruments = new LinkedHashMap<>();
        instruments.put(0, new Instrument("循环鼓 (A)", 10000, "鼓", 9, 0, Instrument.EffectType.RIPPLE));
        instruments.put(1, new Instrument("函数琴 (S)", 16000, "钢琴", 0, 0, Instrument.EffectType.FIREWORK));
        instruments.put(2, new Instrument("变量提琴 (D)", 20000, "小提琴", 1, 40, Instrument.EffectType.RIPPLE));
        instruments.put(3, new Instrument("递归号 (F)", 24000, "萨克斯", 2, 65, Instrument.EffectType.FIREWORK));
        instruments.put(4, new Instrument("并发贝斯 (G)", 30000, "贝斯", 3, 33, Instrument.EffectType.RIPPLE));
        audioEngine.startBackgroundMelody();
        initFonts();
        buildMenu();
    }

    private void initFonts(){
        String[] preferred = {"KaiTi", "Kaiti", "STKaiti", "楷体", "STSong", "SimKai", "JetBrains Mono", "Consolas", "Menlo", "Monaco", "SansSerif"};
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        java.util.Set<String> available = new java.util.HashSet<>(java.util.Arrays.asList(ge.getAvailableFontFamilyNames()));
        String chosen = null;
        for(String f: preferred){ if(available.contains(f)){ chosen=f; break; } }
        if(chosen==null) chosen="SansSerif";
        fontMono14 = new Font(chosen, Font.PLAIN, 14);
        fontMono16 = new Font(chosen, Font.PLAIN, 16);
        fontMono22Bold = new Font(chosen, Font.BOLD, 22);
        fontMono12 = new Font(chosen, Font.PLAIN, 12);
        uiFont = fontMono14;
    }

    private void initStars(){
        int w = getWidth()>0?getWidth():WIDTH; int h=getHeight()>0?getHeight():HEIGHT;
        starX = new int[starCount]; starY = new int[starCount]; java.util.Random r = new java.util.Random();
        for(int i=0;i<starCount;i++){ starX[i]=r.nextInt(w); starY[i]=r.nextInt(h); }
    }

    @Override public void addNotify(){
        super.addNotify();
        if(starX==null) initStars();
    }

    private void buildMenu(){
        JMenuBar bar = new JMenuBar();
        JMenu settings = new JMenu("设置");
        JMenuItem open = new JMenuItem("音量 / BPM 上限...");
        open.addActionListener(e -> openSettingsDialog());
        settings.add(open);
        bar.add(settings);
        setJMenuBar(bar);
    }

    private void openSettingsDialog(){
        JDialog dlg = new JDialog(this, "设置", true);
        dlg.setLayout(new BorderLayout());
        JPanel panel = new JPanel(new GridLayout(3,2,8,8));
        JLabel volLabel = new JLabel("音量:"+audioEngine.getVolume());
        JSlider vol = new JSlider(0,100,audioEngine.getVolume());
        vol.addChangeListener(ev->{ volLabel.setText("音量:"+vol.getValue()); });
        JLabel bpmLabel = new JLabel("最大BPM:"+bpmMax);
        JSlider bpmSlider = new JSlider(120,240,bpmMax);
        bpmSlider.addChangeListener(ev->{ bpmLabel.setText("最大BPM:"+bpmSlider.getValue()); });
        panel.add(volLabel); panel.add(vol);
        panel.add(bpmLabel); panel.add(bpmSlider);
        JButton apply = new JButton("应用");
        apply.addActionListener(ev->{ audioEngine.setVolume(vol.getValue()); bpmMax = bpmSlider.getValue(); if(audioEngine.getBpm()>bpmMax) audioEngine.setBpm(bpmMax); saveProgress(); dlg.dispose(); });
        panel.add(new JLabel()); panel.add(apply);
        dlg.add(panel, BorderLayout.CENTER);
        dlg.pack(); dlg.setLocationRelativeTo(this); dlg.setVisible(true);
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
        // F3 切换性能 HUD
        addKeyBinding(gamePanel, KeyStroke.getKeyStroke(KeyEvent.VK_F3,0), "TOGGLE_DEBUG_HUD", () -> { showDebugHud = !showDebugHud; });

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

    // 动态重复间隔（减速后更长）
    private int computeRepeatIntervalMs(){
        double base = 160;
        if (slowFactor < 1.0) return (int)(base / slowFactor);
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
            skillCharge = Math.min(SKILL_THRESHOLD, skillCharge + 25);
            shakeIntensity = Math.min(1.0, shakeIntensity + 0.3);
            telegraphSatisfied = true;
        }
        if (pendingTelegraph != null) pendingTelegraph.alive=false;
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
        activeEffects.add(new AttackEffects.ShockwaveEffect(getWidth(), getHeight()));
        // 中央显示格挡提示 telegraph
        int cx = getWidth()/2; int cy = getHeight()/3;
        activeEffects.add(new AttackEffects.CircleTelegraphEffect(cx, cy, 300, COUNTER_WINDOW_MS, "SPACE"));
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
        int newBpm = Math.max(60, Math.min(bpmMax, audioEngine.getBpm() + delta));
        audioEngine.setBpm(newBpm); saveProgress();
    }

    private void triggerSuperSkill() {
        if (!skillReady) return; skillReady = false; skillCharge = 0;
        // 记录并强制高质量
        if(!qualityForced){ qualityBeforeUltimate = quality; qualityForced = true; }
        quality = QualityLevel.HIGH;
        Instrument last = instruments.get(lastUsedInstrumentIndex >=0 ? lastUsedInstrumentIndex : 1);
        activeEffects.add(new AttackEffects.UltimateOverlayEffect(last.getColor(), 5000));
        activeEffects.add(new AttackEffects.HackOverlayEffect());
        audioEngine.playUltimateSequence();
        ultimateActive = true; ultimateEnd = System.currentTimeMillis() + 5000;
        ultimateComboBoost = true; ultimateComboEnd = System.currentTimeMillis() + 10000;
        AttackEffect eff = (last.getEffectType()== Instrument.EffectType.FIREWORK)
                ? new AttackEffects.SuperFireworkEffect(getWidth(), getHeight(), last.getColor(), getEffectDensity())
                : new AttackEffects.FullScreenRippleEffect(getWidth(), getHeight(), last.getColor());
        activeEffects.add(eff);
        int bonusDmg = (int)(boss.getMaxHealth() * 0.03 + comboCount * 800);
        applyBossDamage(bonusDmg);
        shakeIntensity = Math.min(1.0, shakeIntensity + 0.7);
    }

    private void scheduleNextBossSkill(){
        long now = System.currentTimeMillis();
        // 每 8~14 秒之间触发一次（取随机+放松体验）
        nextSkillTime = now + 8000 + new Random().nextInt(6000);
    }

    // 移除 v2 临时方法：将逻辑合并为正式方法 (保留当前这一份)
    private void maybeActivateBossSkill(){
        long now = System.currentTimeMillis();
        if (now < nextSkillTime || bossSkill != BossSkillType.NONE || counterActive) return;
        if (boss instanceof BugBoss) {
            bossSkill = BossSkillType.ABSORB; bossSkillEnd = now + 3000; absorbAccum = 0;
            spawnTelegraph("", 1500);
        } else if (boss instanceof MatrixBoss) {
            bossSkill = BossSkillType.REFLECT; bossSkillEnd = now + 2500; reflectActive = true; spawnTelegraph("", 1600);
        } else if (boss instanceof NeuralCoreBoss) {
            bossSkill = BossSkillType.CORE_PULSE; bossSkillEnd = now + 3200; activeEffects.add(new AttackEffects.CorePulseEffect(getWidth(), getHeight(), new Color(120,200,255))); // 不再提示 SPACE
            spawnTelegraph("", 2000);
        }
    }

    // Telegraph 辅助
    private void spawnTelegraph(String key, long duration){
        telegraphKey = key; telegraphSatisfied = false; pendingTelegraph = null;
        int cx = getWidth()/2; int cy = getHeight()/3; if(cx==0) {cx=WIDTH/2; cy=HEIGHT/3;}
        Color c = boss instanceof BugBoss? new Color(255,140,80): boss instanceof MatrixBoss? new Color(255,230,90): new Color(140,210,255);
        activeEffects.add(new AttackEffects.BlurTelegraphEffect(cx, cy, 260, duration, c.brighter(), true));
        if(key != null && !key.isEmpty()) activeEffects.add(new AttackEffects.CircleTelegraphEffect(cx, cy, 240, duration, key));
    }

    // 统一保存方法 (仅此一份)
    private void saveProgress() {
        SaveManager.SaveData d = new SaveManager.SaveData();
        d.currentBossIndex = currentBossIndex;
        d.totalScore = totalScore;
        d.bpm = audioEngine.getBpm();
        d.skillCharge = skillCharge;
        d.comboCount = comboCount;
        d.ultimateComboRemainMs = ultimateComboBoost?Math.max(0, ultimateComboEnd-System.currentTimeMillis()):0;
        d.bossHealths = bosses.stream().mapToDouble(BossEntity::getHealth).toArray();
        d.volume = audioEngine.getVolume();
        d.bpmMax = bpmMax;
        SaveManager.save(d);
        this.saveData=d;
    }

    private void adaptiveQuality(long now){
        if(qualityForced && ultimateActive) return;
        if(now - perfLastAdjust < 1500) return;
        perfLastAdjust = now;
        if(avgFrameMs > 28 && quality != QualityLevel.LOW){ quality = QualityLevel.LOW; trimEffectsForLow(); }
        else if(avgFrameMs > 20 && avgFrameMs <=28 && quality==QualityLevel.HIGH){ quality = QualityLevel.MED; }
        else if(avgFrameMs < 14 && quality!=QualityLevel.HIGH){ quality = QualityLevel.HIGH; }
    }
    private void trimEffectsForLow(){
        if(activeEffects.size()>12){ int excess = activeEffects.size()-12; for(int i=0;i<excess;i++){ activeEffects.remove(0); } }
        for(Projectile p: projectiles){ while(p.trail.size()>5) p.trail.removeLast(); }
    }

    private void updateEffects(long dt) {
        int skipMod = (quality==QualityLevel.LOW?2:1);
        // 原地更新 + 逆序移除
        for(int i=activeEffects.size()-1;i>=0;i--){
            AttackEffect ef = activeEffects.get(i);
            if(skipMod>1 && (i % skipMod)==1) { continue; }
            ef.update(dt);
            if(!ef.isAlive()) activeEffects.remove(i);
        }
    }

    private void startAnimationLoop() {
        scheduleNextBossSkill();
        javax.swing.Timer timer = new javax.swing.Timer(16, e -> {
            long now = System.currentTimeMillis(); long dt = now - lastUpdate; lastUpdate = now; if(dt<=0) dt=1;
            // 平滑帧时间
            avgFrameMs = avgFrameMs + (dt - avgFrameMs)*PERF_SMOOTH;
            adaptiveQuality(now);
            updateEffects(dt);
            // 投射物逻辑低质量隔帧
            if(quality==QualityLevel.LOW){ projectileUpdateToggle = !projectileUpdateToggle; if(!projectileUpdateToggle) updateProjectiles(dt); }
            else updateProjectiles(dt);
            shakeIntensity *= 0.90; if(shakeIntensity < 0.001) shakeIntensity = 0;
            bassPulseAmp *= 0.92; bassPulsePhase += dt / 1000.0 * 2 * Math.PI * 1.2;
            boss.update(dt);
            updateBossPhaseIfNeeded(); finishCounterIfTimeout(); updateBossSkillState();
            if (slowFactor < 1.0 && System.currentTimeMillis() > slowEndTime) slowFactor = 1.0;
            if (darkAlpha > 0f) darkAlpha *= 0.92f;
            if (ultimateActive && now > ultimateEnd) { ultimateActive = false; if(qualityForced){ quality = qualityBeforeUltimate; qualityForced=false; } }
            if(quality==QualityLevel.LOW){ frameSkipToggle = !frameSkipToggle; if(frameSkipToggle) return; }
            frameCounter++; gamePanel.repaint();
        });
        timer.start();
    }

    // === 新增：科技背景绘制方法 ===
    private void drawTechBackground(Graphics2D g2d){
        int w = getWidth(); int h = getHeight();
        long t = System.currentTimeMillis();
        // 背景渐变基底
        GradientPaint gp = new GradientPaint(0,0,new Color(6,10,25),0,h,new Color(12,30,60));
        g2d.setPaint(gp); g2d.fillRect(0,0,w,h);
        // 低质量直接返回
        if(quality==QualityLevel.LOW){ return; }
        // 垂直律动光柱
        for(int x=0;x<w;x+=60){
            double pulse = 0.5 + 0.5*Math.sin(t/450.0 + x*0.12);
            int a = (int)(40 + pulse*110);
            g2d.setColor(new Color(0,170,255, Math.min(255,a)));
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.20f + (float)pulse*0.25f));
            g2d.drawLine(x,0,x,h);
        }
        // 扫描横条
        int scanY = (int)((t/9) % h);
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.55f));
        g2d.setPaint(new GradientPaint(0,scanY,new Color(0,255,200,120),0, Math.min(h, scanY+90), new Color(0,255,200,0)));
        g2d.fillRect(0, scanY, w, 90);
        // 连接节点 (电路点)
        g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER,0.32f));
        g2d.setColor(new Color(0,255,190,180));
        int gapX = 120, gapY = 130;
        for(int x=40;x<w;x+=gapX){
            for(int y=60;y<h;y+=gapY){
                if( ((x+y)>>6) % 3 == 0){
                    int r = 4;
                    g2d.fillOval(x-r,y-r,r*2,r*2);
                    // 与邻点连线 (少量)
                    if(x+gapX < w) g2d.drawLine(x,y,x+gapX,y);
                    if(y+gapY < h) g2d.drawLine(x,y,x,y+gapY);
                }
            }
        }
        g2d.setComposite(AlphaComposite.SrcOver);
    }

    private class GamePanel extends JPanel {
        public GamePanel(){ setBackground(Color.BLACK); setDoubleBuffered(true); }
        @Override protected void paintComponent(Graphics g){
            super.paintComponent(g); Graphics2D g2d=(Graphics2D)g; g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if(uiFont!=null) g2d.setFont(uiFont);
            // 新科技感背景
            if(quality==QualityLevel.LOW){
                g2d.setColor(new Color(10,12,22)); g2d.fillRect(0,0,getWidth(),getHeight());
            } else {
                drawTechBackground(g2d);
            }
            // 星点保留（叠加少量深空点）
            if(starX!=null && quality!=QualityLevel.LOW){ g2d.setColor(new Color(255,255,255,25)); for(int i=0;i<starX.length;i+=2){ g2d.fillRect(starX[i], starY[i], 2,2);} }
            // 低频光晕
            if (bassPulseAmp > 0.02) {
                Paint oldGlow = g2d.getPaint();
                float pulse = (float)(bassPulseAmp * (0.6 + 0.4 * Math.sin(bassPulsePhase)));
                int radius = (int)(Math.min(getWidth(), getHeight()) * (0.45 + 0.15 * pulse));
                RadialGradientPaint glow = new RadialGradientPaint(new Point(getWidth()/2, getHeight()/2), radius,
                        new float[]{0f,0.6f,1f}, new Color[]{
                                new Color(120,40,180,(int)(120*pulse)),
                                new Color(80,0,100,(int)(60*pulse)),
                                new Color(30,0,50,0)});
                g2d.setPaint(glow);
                g2d.fillOval(getWidth()/2 - radius, getHeight()/2 - radius, radius*2, radius*2);
                g2d.setPaint(oldGlow);
            }
            // 抖动
            int shakeX = 0, shakeY = 0;
            if (shakeIntensity > 0) {
                double mag = shakeIntensity * 12.0;
                shakeX = (int)((randFX.nextDouble()-0.5)*mag*2);
                shakeY = (int)((randFX.nextDouble()-0.5)*mag*2);
            }
            g2d.translate(shakeX, shakeY);
            boss.draw(g2d, getWidth()/2, getHeight()/3);
            drawBossTentacles(g2d, getWidth()/2, getHeight()/3, (int)System.currentTimeMillis());
            // 投射物 + 拖尾
            for(Projectile p: projectiles){
                int i=0; for(Point pt: p.trail){ float a = 1f - i/12f; g2d.setColor(new Color(p.color.getRed(), p.color.getGreen(), p.color.getBlue(), (int)(a*160))); g2d.fillOval(pt.x-4, pt.y-4,8,8); i++; }
                g2d.setColor(p.color); g2d.fillOval((int)p.x-6,(int)p.y-6,12,12);
            }
            g2d.translate(-shakeX, -shakeY);
            // 血条
            int barW = 500; int barX = getWidth()/2 - barW/2; int barY = 40; double hpPct = (double)boss.getHealth()/boss.getMaxHealth();
            g2d.setColor(new Color(70,0,90)); g2d.fillRoundRect(barX, barY, barW, 24, 12,12);
            g2d.setColor(new Color(180,40,220)); g2d.fillRoundRect(barX, barY, (int)(barW*hpPct), 24, 12,12);
            g2d.setColor(Color.WHITE); g2d.drawRoundRect(barX, barY, barW, 24, 12,12);
            g2d.setFont(fontMono16);
            g2d.drawString("BOSS(" + (currentBossIndex+1) + "/" + bosses.size() + ") " + boss.getName() + " HP: " + boss.getHealth() + " / " + boss.getMaxHealth(), barX+10, barY+17);
            if (slowFactor < 1.0) { g2d.setColor(new Color(255,200,120)); g2d.drawString("减速中", getWidth()/2 + 180, barY+17); }
            g2d.setColor(Color.WHITE); g2d.drawString("TOTAL: " + totalScore, 20, getHeight()-50);
            if (darkAlpha > 0.02f) { g2d.setColor(new Color(0,0,0, Math.min(200, (int)(darkAlpha*255)))); g2d.fillRect(0,0,getWidth(),getHeight()); }
            // 连击
            if (comboCount > 1) { g2d.setFont(fontMono22Bold); String comboStr = comboCount + " COMBO x" + String.format("%.2f", comboMultiplier); g2d.setColor(new Color(255, 220, 120, (int)(Math.min(1.0, 0.3 + comboCount/30.0) * 255))); g2d.drawString(comboStr, 20, getHeight()-60); }
            // 技能条
            int skillBarW = 260; int skillBarH = 16; int skillX = getWidth()-skillBarW-30; int skillY = getHeight()-60; double scPct = skillCharge / SKILL_THRESHOLD;
            g2d.setColor(new Color(40,40,60)); g2d.fillRoundRect(skillX, skillY, skillBarW, skillBarH, 10,10);
            g2d.setColor(skillReady ? new Color(255,200,60) : new Color(120,140,255)); g2d.fillRoundRect(skillX, skillY, (int)(skillBarW*scPct), skillBarH, 10,10);
            g2d.setColor(Color.WHITE); g2d.drawRoundRect(skillX, skillY, skillBarW, skillBarH, 10,10);
            g2d.setFont(fontMono12); g2d.drawString(skillReady?"Q 释放超级技能":"技能: "+(int)skillCharge+"%", skillX+10, skillY+12);
            // 反击提示
            if (counterActive) { long remain = Math.max(0, counterEndTime - System.currentTimeMillis()); String txt = counterResolved?"已格挡":"SPACE 格挡攻击:" + remain + "ms"; g2d.setColor(counterResolved?new Color(120,255,160):new Color(255,120,120)); g2d.drawString(txt, skillX, skillY - 20); }
            // 拍点条
            double beatProg = audioEngine.progressToNextBeat(); int beatBarW = 160; int beatBarH = 6; int beatX = getWidth()/2 - beatBarW/2; int beatY = 12;
            g2d.setColor(new Color(50,50,70)); g2d.fillRoundRect(beatX, beatY, beatBarW, beatBarH, 8,8);
            g2d.setColor(new Color(200,220,255)); g2d.fillRoundRect(beatX, beatY, (int)(beatBarW * beatProg), beatBarH, 8,8);
            g2d.setColor(Color.WHITE); g2d.drawRoundRect(beatX, beatY, beatBarW, beatBarH, 8,8);
            // 特效
            for (AttackEffect ef : activeEffects) ef.draw(g2d);
            // Boss技能状态
            if (bossSkill != BossSkillType.NONE) { String skillTxt = bossSkill==BossSkillType.ABSORB?"Boss吸收中": bossSkill==BossSkillType.REFLECT?"Boss反射中": "核心脉冲充能"; g2d.setColor(new Color(255,240,180)); g2d.drawString(skillTxt, getWidth()-180, 30); }
            // BPM 显示
            g2d.setColor(Color.WHITE); g2d.drawString("BPM:"+audioEngine.getBpm()+" ↑↓调整", getWidth()-170, getHeight()-30);
            // 反射HUD
            if(reflectActive && bossSkill==BossSkillType.REFLECT){ g2d.setColor(new Color(255,255,120,180)); int size=28; int x=8,y=8; g2d.fillRoundRect(x,y,size,size,8,8); g2d.setColor(Color.DARK_GRAY); g2d.drawRoundRect(x,y,size,size,8,8); g2d.setColor(Color.BLACK); g2d.setFont(fontMono12); g2d.drawString("R", x+9, y+18); }
            if (ultimateComboBoost){ g2d.setColor(new Color(255,240,90)); g2d.drawString("终极连击 x2", 20, 70); }
            // 在原绘制逻辑末尾添加 Boss 技能柔和字幕
            if(bossSkill != BossSkillType.NONE){
                String subt = switch(bossSkill){
                    case ABSORB -> "能量聚合";
                    case REFLECT -> "反射屏障";
                    case CORE_PULSE -> "核心脉冲充能";
                    default -> ""; };
                if(!subt.isEmpty()){
                    g2d.setFont(fontMono16);
                    Composite oldComp = g2d.getComposite(); // 避免与上方 old 重名
                    float alpha = 0.55f + 0.45f*(float)Math.sin(System.currentTimeMillis()/400.0);
                    g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, Math.min(1f,Math.max(0f,alpha))));
                    FontMetrics fm = g2d.getFontMetrics(); int sw = fm.stringWidth(subt);
                    int x = getWidth()/2 - sw/2; int y = getHeight()/3 - 70;
                    g2d.setColor(new Color(0,0,0,120)); g2d.fillRoundRect(x-14,y-fm.getAscent()-4, sw+28, fm.getHeight()+8, 18,18);
                    g2d.setColor(new Color(255,230,200)); g2d.drawString(subt,x,y);
                    g2d.setComposite(oldComp);
                }
            }
            // 质量等级提示（可通过 F3 显示/隐藏）
            if(showDebugHud){
                g2d.setFont(fontMono12);
                g2d.setColor(new Color(200,200,200,160));
                g2d.drawString("Q:"+quality+" FPSms:"+String.format("%.1f",avgFrameMs), 10, 18);
            }
        }
    }

    private void drawBossTentacles(Graphics2D g2d, int cx, int cy, int time){
        g2d.setStroke(new BasicStroke(4f));
        for(int i=0;i<2;i++){
            double dir = (i==0? -1:1);
            int baseX = cx + (int)(dir*130); int baseY = cy+40;
            int midX = baseX + (int)(dir* (40 + 20*Math.sin(time/500.0 + i)));
            int midY = baseY + 60;
            int endX = baseX + (int)(dir* (70 + 30*Math.cos(time/430.0 + i)));
            int endY = baseY + 140 + (int)(20*Math.sin(time/300.0 + i));
            g2d.setColor(new Color(200,160,255,140));
            CubicCurve2D c = new CubicCurve2D.Double(baseX,baseY, midX,midY, midX,endY-40, endX,endY);
            g2d.draw(c);
        }
    }


    @Override
    public void dispose() {
        saveProgress();
        super.dispose();
        audioEngine.shutdown();
    }

    // ====== 重新补回缺失的核心方法（被之前编辑覆盖） ======
    private void applyBossDamage(int dmg){
        if(bossSwitching) return; // 正在切换不再处理伤害
        if (bossSkill == BossSkillType.ABSORB){
            absorbAccum += dmg;
            shakeIntensity = Math.min(1.0, shakeIntensity + dmg / (double)boss.getMaxHealth());
            return;
        }
        if (bossSkill == BossSkillType.REFLECT && reflectActive){
            comboCount = Math.max(0, comboCount - 5);
            damageFlashEdge();
            return;
        }
        boss.takeDamage(dmg);
        if(boss.getHealth() < 0) boss.setHealth(0); // clamp
        score += dmg; totalScore += dmg;
        if (boss.getHealth() <= 0 && !bossSwitching){
            bossSwitching = true;
            onBossDefeated();
        }
    }

    private void spawnProjectile(Instrument inst, int dmg){
        if(quality==QualityLevel.LOW){
            // 限制最大投射物数量
            if(projectiles.size() > 14) return; // 直接丢弃新投射物
        }
        int w = getWidth()>0?getWidth():WIDTH; int h=getHeight()>0?getHeight():HEIGHT;
        double startX = 50 + Math.random()*(w-100);
        double startY = h + 20;
        double targetX = w/2.0 + (Math.random()-0.5)*120;
        double targetY = h/3.0 - 40;
        double speed = 600;
        projectiles.add(new Projectile(startX,startY,targetX,targetY,speed,dmg, inst.getColor(), inst));
    }

    private void updateProjectiles(long dt){
        if(projectiles.isEmpty()) return;
        double dtSec = dt/1000.0;
        for(Projectile p: projectiles){
            if(p.hit) continue;
            double dx = p.tx - p.x; double dy = p.ty - p.y; double dist = Math.sqrt(dx*dx+dy*dy);
            double step = p.speed * dtSec;
            if(step >= dist){
                p.x = p.tx; p.y = p.ty; p.hit = true; onProjectileHit(p);
            } else {
                p.x += dx/dist * step; p.y += dy/dist * step;
            }
            // 拖尾长度根据质量等级
            int maxTrail = (quality==QualityLevel.LOW?5: quality==QualityLevel.MED?9:12);
            p.trail.addFirst(new Point((int)p.x,(int)p.y));
            while(p.trail.size()>maxTrail) p.trail.removeLast();
        }
        projectiles.removeIf(pr -> pr.hit);
    }

    private void onProjectileHit(Projectile p){
        activeEffects.add(new AttackEffects.SuperFireworkEffect(getWidth(), getHeight()/2, p.color, getEffectDensity()));
        double remainGain = (SKILL_GAIN_PER_HIT + comboCount * SKILL_COMBO_BONUS) * 0.6;
        skillCharge = Math.min(SKILL_THRESHOLD, skillCharge + remainGain);
        if(skillCharge >= SKILL_THRESHOLD) skillReady = true;
        applyBossDamage(p.damage);
        saveProgress();
    }

    private void updateBossSkillState(){
        long now = System.currentTimeMillis();
        if (bossSkill != BossSkillType.NONE && now > bossSkillEnd){
            if (bossSkill == BossSkillType.ABSORB && absorbAccum > 0){
                activeEffects.add(new AttackEffects.HealingBurstEffect(getWidth(), getHeight(), new Color(255,140,90), 0.35, 1200));
                activeEffects.add(new AttackEffects.HealingBurstEffect(getWidth(), getHeight(), new Color(255,200,140), 0.5, 900));
                activeEffects.add(new AttackEffects.HealingBurstEffect(getWidth(), getHeight(), new Color(255,255,200), 0.7, 700));
                boss.takeDamage(-(int)Math.min(boss.getMaxHealth()*0.01, absorbAccum*0.5));
            }
            if (bossSkill == BossSkillType.REFLECT) reflectActive = false;
            bossSkill = BossSkillType.NONE;
            scheduleNextBossSkill();
            saveProgress();
        }
    }

    private void damageFlashEdge(){ darkAlpha = 0.4f; }

    private void triggerInstrument(int instrumentIndex) {
        Instrument instrument = instruments.get(instrumentIndex); if (instrument == null) return;
        if (slowFactor < 1.0 && System.currentTimeMillis() > slowEndTime) slowFactor = 1.0;
        audioEngine.playHitNote(instrument); // 即时反馈
        maybeActivateBossSkill();
        long now = System.currentTimeMillis();
        if (now - lastTriggerTime <= COMBO_WINDOW_MS) comboCount++; else comboCount = 1;
        lastTriggerTime = now;
        double comboBase = 1.0 + Math.min(1.5, comboCount * 0.05);
        if (ultimateComboBoost && System.currentTimeMillis()<ultimateComboEnd) comboMultiplier = comboBase * 2.0; else { comboMultiplier = comboBase; if (ultimateComboBoost && System.currentTimeMillis()>=ultimateComboEnd) ultimateComboBoost=false; }
        audioEngine.playPattern(instrument);
        int baseDamage = instrument.getDamage(); int projectedDamage = (int)Math.round(baseDamage * comboMultiplier * slowFactor);
        // 低质量模式下跳过一半投射物创建以降低 CPU / 绘制负载，仍然直接应用伤害
        if(quality==QualityLevel.LOW && (lowQualityProjectileSkipCounter++ % 2)==1){
            applyBossDamage(projectedDamage);
            double gain = SKILL_GAIN_PER_HIT + comboCount * SKILL_COMBO_BONUS; skillCharge = Math.min(SKILL_THRESHOLD, skillCharge + gain * 0.4);
            if (skillCharge >= SKILL_THRESHOLD) skillReady = true;
            lastUsedInstrumentIndex = instrumentIndex; saveProgress(); return;
        }
        spawnProjectile(instrument, projectedDamage);
        double gain = SKILL_GAIN_PER_HIT + comboCount * SKILL_COMBO_BONUS; skillCharge = Math.min(SKILL_THRESHOLD, skillCharge + gain * 0.4);
        if (skillCharge >= SKILL_THRESHOLD) skillReady = true;
        lastUsedInstrumentIndex = instrumentIndex;
        saveProgress();
    }
    // ====== 核心方法补齐结束 ======

    private void onBossDefeated(){
        int defeatedIndex = currentBossIndex; // 当前被打败的索引
        currentBossIndex++;
        // 打败第一个 Boss (index 0) 提示
        if(defeatedIndex==0 && currentBossIndex < bosses.size()){
            int opt = JOptionPane.showOptionDialog(this, "已击败第一个Boss，是否继续挑战下一个?", "进度",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new Object[]{"继续", "退出"}, "继续");
            if(opt!=0){ dispose(); System.exit(0); return; }
        }
        // 第二到第三前提示 (打败第二个: defeatedIndex==1)
        if(defeatedIndex==1 && currentBossIndex < bosses.size()){
            int opt = JOptionPane.showOptionDialog(this, "已击败第二个Boss，是否继续挑战最终Boss?", "进度",
                    JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new Object[]{"继续", "退出"}, "继续");
            if(opt!=0){ dispose(); System.exit(0); return; }
        }
        if(currentBossIndex >= bosses.size()){
            // 全部击败
            int opt = JOptionPane.showOptionDialog(this, "所有 Boss 已被击败! 总分: "+ totalScore +"\n是否重新开始?", "胜利",
                    JOptionPane.YES_NO_OPTION, JOptionPane.INFORMATION_MESSAGE, null,
                    new Object[]{"重新开始","退出"}, "重新开始");
            if(opt==0){ restartGame(); return; }
            dispose(); System.exit(0); return;
        }
        boss = bosses.get(currentBossIndex);
        comboCount = 0; comboMultiplier = 1.0; skillCharge = 0; skillReady = false;
        ultimateActive = false; ultimateComboBoost = false; qualityForced = false;
        reflectActive = false; bossSkill = BossSkillType.NONE; absorbAccum = 0; counterActive = false;
        darkAlpha = 0f; slowFactor = 1.0; shakeIntensity = 0; bassPulseAmp = 0;
        bossSwitching = false; // 切换完毕
        scheduleNextBossSkill();
        saveProgress();
    }
    private void restartGame(){
        for(BossEntity b: bosses){ b.setHealth((int)b.getMaxHealth()); }
        currentBossIndex = 0; boss = bosses.get(0);
        totalScore = 0; score = 0; comboCount = 0; comboMultiplier = 1.0; skillCharge = 0; skillReady = false;
        ultimateActive=false; ultimateComboBoost=false; qualityForced=false; reflectActive=false; bossSkill=BossSkillType.NONE; absorbAccum=0; counterActive=false;
        darkAlpha=0f; slowFactor=1.0; shakeIntensity=0; bassPulseAmp=0; projectiles.clear(); activeEffects.clear();
        bossSwitching = false; // 重置标志
        scheduleNextBossSkill(); saveProgress();
    }

    private double getEffectDensity(){
        return switch(quality){
            case HIGH -> 1.0;
            case MED -> 0.75;
            case LOW -> 0.45;
        };
    }
}
