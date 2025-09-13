package org.example;

import java.awt.*;
import java.util.List;
import java.util.Arrays;
import java.util.Random;

// Boss类 (代码人形)
public class BugBoss implements BossEntity {
    private int health;
    private final int maxHealth;
    private String name;
    private long animTimeMs = 0;
    private final Random rand = new Random();

    private final java.util.List<String> codeLines = Arrays.asList(
            "public class Code {",
            "  void fight(){",
            "    debug();",
            "    optimize();",
            "  }",
            "}",
            "for(int i=0;i<∞;i++){}",
            "if(bug) fix();",
            "lambda -> innovate();",
            "try{ship();}catch(e){}"
    );

    public BugBoss(int health, String name) {
        this.health = health;
        this.maxHealth = health;
        this.name = name;
    }

    public void update(long dt){
        animTimeMs += dt;
    }

    public void takeDamage(int damage) {
        health -= damage;
        if (health < 0) health = 0;
    }

    public void draw(Graphics2D g2d, int centerX, int centerY) {
        double hpPct = (double)health / maxHealth;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int bodyWidth = 260;
        int bodyHeight = 320;
        int topY = centerY - bodyHeight/2;

        // 阶段颜色设定
        Color cTop;
        Color cBottom;
        if (hpPct > 0.7) {
            cTop = new Color(30,30,60,160);
            cBottom = new Color(80,0,120,160);
        } else if (hpPct > 0.4) {
            cTop = new Color(60,0,90,170);
            cBottom = new Color(140,20,180,180);
        } else if (hpPct > 0.15) {
            cTop = new Color(90,0,30,190);
            cBottom = new Color(220,40,90,190);
        } else { // 濒危阶段：颜色脉动
            int pulse = (int)(120 + 80 * Math.sin(animTimeMs/120.0));
            cTop = new Color(120,0,pulse/2,200);
            cBottom = new Color(255,pulse,120,210);
        }

        // 轻微呼吸放缩（低血量加剧）
        double breathe = 1 + 0.02 * Math.sin(animTimeMs/400.0) + (1-hpPct)*0.05*Math.sin(animTimeMs/150.0);
        int bw = (int)(bodyWidth * breathe);
        int bh = (int)(bodyHeight * breathe);
        int topYAdj = centerY - bh/2;

        // 背景渐变
        GradientPaint gp = new GradientPaint(centerX - bw/2f, topYAdj, cTop, centerX + bw/2f, topYAdj + bh, cBottom);
        g2d.setPaint(gp);
        g2d.fillRoundRect(centerX - bw/2, topYAdj, bw, bh, 40, 40);

        // 边框闪烁
        float borderAlpha = (float)(0.55 + 0.25 * Math.sin(animTimeMs/180.0) + (1-hpPct)*0.3);
        borderAlpha = Math.min(1f, Math.max(0.2f, borderAlpha));
        g2d.setColor(new Color(200,160,255,(int)(borderAlpha*255)));
        g2d.setStroke(new BasicStroke(4f));
        g2d.drawRoundRect(centerX - bw/2, topYAdj, bw, bh, 40, 40);

        // 代码行动态（水平波动 + 随血量抖动）
        g2d.setFont(new Font("Monospaced", Font.BOLD, 14));
        int lineHeight = 26;
        int startY = topYAdj + 40;
        for (int i = 0; i < codeLines.size(); i++) {
            int y = startY + i * lineHeight;
            if (y > topYAdj + bh - 40) break;
            double wave = 12 * Math.sin((animTimeMs/300.0) + i * 0.9);
            double jitter = (1 - hpPct) * (rand.nextDouble() - 0.5) * 14; // 低血量抖动增强
            double factor = Math.sin((double)(y - topYAdj)/bh * Math.PI);
            int half = (int)(bw/2 * factor * 0.85);
            int drawX = (int)(centerX - half + 14 + wave + jitter);
            // 低血量时部分行闪烁半透明
            float alpha = 1f;
            if (hpPct < 0.4 && rand.nextDouble() < 0.08) {
                alpha = 0.4f + rand.nextFloat()*0.3f;
            }
            g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2d.setColor(new Color(220,255,255));
            g2d.drawString(codeLines.get(i), drawX, y);
        }
        g2d.setComposite(AlphaComposite.SrcOver);

        // 头部（动态脉动光球, 低血量变大 & 闪烁）
        int headRBase = 90;
        int headR = (int)(headRBase * (1 + 0.05*Math.sin(animTimeMs/250.0) + (1-hpPct)*0.25));
        int headY = topYAdj - headR/2 + 10;
        float glowPulse = (float)(0.6 + 0.3*Math.sin(animTimeMs/140.0) + (1-hpPct)*0.4);
        glowPulse = Math.min(1f, Math.max(0.2f, glowPulse));
        RadialGradientPaint rgp = new RadialGradientPaint(new Point(centerX, headY + headR/2), headR/2f,
                new float[]{0f,0.5f,1f}, new Color[]{
                        new Color(255,255,255,(int)(255*glowPulse)),
                        new Color(200,160,255,(int)(200*glowPulse)),
                        new Color(40,0,60,0)});
        g2d.setPaint(rgp);
        g2d.fillOval(centerX - headR/2, headY, headR, headR);
        g2d.setColor(new Color(200,160,255,(int)(180*glowPulse)));
        g2d.drawOval(centerX - headR/2, headY, headR, headR);

        // 眼睛（括号闪烁）
        g2d.setFont(new Font("Monospaced", Font.BOLD, 28));
        int eyeAlpha = (int)(220 + 30 * Math.sin(animTimeMs/90.0));
        g2d.setColor(new Color(255,255,255, Math.min(255, Math.max(0, eyeAlpha))));
        g2d.drawString("{ }", centerX - 30, headY + headR/2 + 10);
    }

    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public String getName() { return name; }
}
