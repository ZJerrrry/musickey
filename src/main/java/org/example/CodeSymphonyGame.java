package org.example;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
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

    private BugBoss boss;
    private Map<Integer, Instrument> instruments;
    private JPanel gamePanel;
    private int score = 0;
    private AudioEngine audioEngine;

    private final List<AttackEffect> activeEffects = new ArrayList<>();
    private long lastUpdate = System.currentTimeMillis();

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
        boss = new BugBoss(12000, "code");
        instruments = new LinkedHashMap<>(); // 保持顺序

        // 新构造: name, damage, soundType, channel, program, effectType
        // 通道分配：鼓用9（打击乐），其它乐器各自独立通道
        instruments.put(0, new Instrument("循环鼓 (A)", 100, "鼓", 9, 0, Instrument.EffectType.RIPPLE));
        instruments.put(1, new Instrument("函数琴 (S)", 160, "钢琴", 0, 0, Instrument.EffectType.FIREWORK));
        instruments.put(2, new Instrument("变量提琴 (D)", 200, "小提琴", 1, 40, Instrument.EffectType.RIPPLE));
        instruments.put(3, new Instrument("递归号 (F)", 240, "萨克斯", 2, 65, Instrument.EffectType.FIREWORK));
        instruments.put(4, new Instrument("并发贝斯 (G)", 300, "贝斯", 3, 33, Instrument.EffectType.RIPPLE));
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
        // 键位：A S D F G
        char[] keys = {'A','S','D','F','G'};
        for (int i = 0; i < instruments.size(); i++) {
            final int idx = i;
            char key = keys[i];
            addKeyBinding(gamePanel, KeyStroke.getKeyStroke(key), "PLAY_"+key, () -> triggerInstrument(idx));
            addKeyBinding(gamePanel, KeyStroke.getKeyStroke(Character.toLowerCase(key), 0), "PLAY_LOW_"+key, () -> triggerInstrument(idx));
        }
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

    private void triggerInstrument(int instrumentIndex) {
        Instrument instrument = instruments.get(instrumentIndex);
        if (instrument == null) return;

        // 播放音频模式
        audioEngine.playPattern(instrument);

        // 造成伤害
        int damage = instrument.getDamage();
        boss.takeDamage(damage);
        score += damage;

        // 生成特效
        spawnEffectForInstrument(instrument);

        // 检查结束
        if (boss.getHealth() <= 0) {
            JOptionPane.showMessageDialog(this, "你净化了 code!\n总分: " + score);
            audioEngine.shutdown();
            System.exit(0);
        }
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
        javax.swing.Timer timer = new javax.swing.Timer(16, e -> {
            long now = System.currentTimeMillis();
            long dt = now - lastUpdate;
            lastUpdate = now;
            updateEffects(dt);
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

            // 背景渐变
            Paint old = g2d.getPaint();
            GradientPaint bg = new GradientPaint(0,0,new Color(10,10,25),0,getHeight(),new Color(30,0,50));
            g2d.setPaint(bg);
            g2d.fillRect(0,0,getWidth(),getHeight());
            g2d.setPaint(old);

            // 星点
            g2d.setColor(new Color(255,255,255,40));
            for(int i=0;i<60;i++){
                int sx = (i*73)%getWidth();
                int sy = (i*149)%getHeight();
                g2d.fillRect(sx, sy, 2,2);
            }

            // Boss
            boss.draw(g2d, getWidth()/2, getHeight()/3);

            // 血条
            int barW = 500;
            int barX = getWidth()/2 - barW/2;
            int barY = 40;
            double hpPct = (double)boss.getHealth()/boss.getMaxHealth();
            g2d.setColor(new Color(70,0,90));
            g2d.fillRoundRect(barX, barY, barW, 24, 12,12);
            g2d.setColor(new Color(180,40,220));
            g2d.fillRoundRect(barX, barY, (int)(barW*hpPct), 24, 12,12);
            g2d.setColor(Color.WHITE);
            g2d.drawRoundRect(barX, barY, barW, 24, 12,12);
            g2d.setFont(new Font("Monospaced", Font.PLAIN, 16));
            g2d.drawString("HP: "+boss.getHealth()+" / "+boss.getMaxHealth(), barX+10, barY+17);

            // 分数
            g2d.drawString("SCORE: "+score, 20, getHeight()-30);

            // 提示
            g2d.drawString("按 A S D F G 触发乐器攻击 (也可点击按钮)", 20, getHeight()-50);

            // 特效
            synchronized (activeEffects) {
                for (AttackEffect ef : activeEffects) {
                    ef.draw(g2d);
                }
            }
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        audioEngine.shutdown();
    }
}
