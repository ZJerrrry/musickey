package org.example;

import java.awt.*;
import java.util.Random;

/** 第二个Boss：矩阵代码幽影（不同视觉风格） */
public class MatrixBoss implements BossEntity {
    private int health;
    private final int maxHealth;
    private final String name;
    private long timeMs = 0;
    private final Random rand = new Random();
    private final char[] glyphs = "01<>[]{};=+*/#$%^|".toCharArray();

    public MatrixBoss(int hp, String name){
        this.health = hp; this.maxHealth = hp; this.name = name;
    }

    public void update(long dt){ timeMs += dt; }
    public void takeDamage(int dmg){ health = Math.max(0, health - dmg); }
    public void setHealth(int hp){ this.health = Math.max(0, Math.min(hp, maxHealth)); }
    public int getHealth(){ return health; }
    public int getMaxHealth(){ return maxHealth; }
    public String getName(){ return name; }

    public void draw(Graphics2D g2d, int cx, int cy){
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double hpPct = (double)health / maxHealth;
        int bodyW = 300;
        int bodyH = 340;
        int topY = cy - bodyH/2;

        // 轮廓背景（绿黑渐变）
        GradientPaint gp = new GradientPaint(cx - bodyW/2f, topY, new Color(0,30,0,180), cx + bodyW/2f, topY+bodyH, new Color(0,90,40,160));
        g2d.setPaint(gp);
        g2d.fillRoundRect(cx - bodyW/2, topY, bodyW, bodyH, 30,30);
        g2d.setColor(new Color(0,200,120,200));
        g2d.setStroke(new BasicStroke(3f));
        g2d.drawRoundRect(cx - bodyW/2, topY, bodyW, bodyH, 30,30);

        // 代码雨列 - 使用行列随机字符形成身体内部闪动
        int cols = 14;
        int rows = 16;
        g2d.setFont(new Font("Monospaced", Font.BOLD, 18));
        for (int c=0;c<cols;c++){
            for (int r=0;r<rows;r++){
                double relY = r/(double)rows;
                int x = cx - bodyW/2 + 15 + c * (bodyW-30)/cols;
                int y = topY + 40 + r * (bodyH-80)/rows;
                // 根据hpPct使低血量时更多闪烁与偏移
                if (rand.nextDouble() < (0.08 + (1-hpPct)*0.25)) continue; // 空洞形成腐蚀感
                char ch = glyphs[(c*31 + r*17 + (int)(timeMs/90)) % glyphs.length];
                float alpha = (float)(0.55 + 0.45*Math.sin((timeMs/400.0)+c*0.8 + r*0.3));
                alpha = Math.min(1f, Math.max(0.15f, alpha - (float)((1-hpPct)*0.2))); // 低血更暗
                g2d.setColor(new Color(0,255,140,(int)(alpha*255)));
                g2d.drawString(String.valueOf(ch), x, y);
            }
        }

        // 头部数字能量球
        int headR = 100;
        int headY = topY - headR/2 + 10;
        float glowPulse = (float)(0.6 + 0.25*Math.sin(timeMs/170.0) + (1-hpPct)*0.3);
        RadialGradientPaint rgp = new RadialGradientPaint(new Point(cx, headY+headR/2), headR/2f,
                new float[]{0f,0.5f,1f}, new Color[]{
                new Color(200,255,200,(int)(255*glowPulse)),
                new Color(0,200,120,(int)(200*glowPulse)),
                new Color(0,60,20,0)});
        g2d.setPaint(rgp);
        g2d.fillOval(cx - headR/2, headY, headR, headR);
        g2d.setColor(new Color(0,255,160,(int)(180*glowPulse)));
        g2d.drawOval(cx - headR/2, headY, headR, headR);

        // 眼睛 (两个发光括号)
        g2d.setFont(new Font("Monospaced", Font.BOLD, 30));
        int eyeAlpha = (int)(220 + 30 * Math.sin(timeMs/110.0));
        g2d.setColor(new Color(180,255,200, eyeAlpha));
        g2d.drawString("< >", cx - 30, headY + headR/2 + 12);
    }
}
