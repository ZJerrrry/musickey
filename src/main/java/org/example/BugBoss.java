package org.example;

import java.awt.*;
import java.util.List;
import java.util.Arrays;

// Boss类 (代码人形)
public class BugBoss {
    private int health;
    private final int maxHealth;
    private String name;

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

    public void takeDamage(int damage) {
        health -= damage;
        if (health < 0) health = 0;
    }

    public void draw(Graphics2D g2d, int centerX, int centerY) {
        // 绘制一个由代码文本组成的抽象人形
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setFont(new Font("Monospaced", Font.BOLD, 14));
        int bodyWidth = 260;
        int bodyHeight = 320;
        int topY = centerY - bodyHeight/2;

        // 身体背景渐变
        GradientPaint gp = new GradientPaint(centerX - bodyWidth/2f, topY, new Color(30,30,60,160), centerX + bodyWidth/2f, topY + bodyHeight, new Color(80,0,120,160));
        g2d.setPaint(gp);
        g2d.fillRoundRect(centerX - bodyWidth/2, topY, bodyWidth, bodyHeight, 40, 40);

        // 发光边框
        g2d.setColor(new Color(180,120,255,180));
        g2d.setStroke(new BasicStroke(4f));
        g2d.drawRoundRect(centerX - bodyWidth/2, topY, bodyWidth, bodyHeight, 40, 40);

        // 代码行分布在身体内
        g2d.setColor(new Color(220,255,255));
        int lineHeight = 26;
        int startY = topY + 40;
        for (int i = 0; i < codeLines.size(); i++) {
            int y = startY + i * lineHeight;
            if (y > topY + bodyHeight - 40) break;
            String line = codeLines.get(i);
            // 根据行绘制不同的x偏移形成轮廓
            double factor = Math.sin((double)(y - topY)/bodyHeight * Math.PI);
            int half = (int)(bodyWidth/2 * factor * 0.85);
            int drawX = centerX - half + 10;
            g2d.drawString(line, drawX, y);
        }

        // 头部（代码光球）
        int headR = 90;
        int headY = topY - headR/2;
        RadialGradientPaint rgp = new RadialGradientPaint(new Point(centerX, headY + headR/2), headR/2f,
                new float[]{0f,0.6f,1f}, new Color[]{new Color(255,255,255,230), new Color(160,120,255,180), new Color(40,0,60,0)});
        g2d.setPaint(rgp);
        g2d.fillOval(centerX - headR/2, headY, headR, headR);
        g2d.setColor(new Color(200,160,255,180));
        g2d.drawOval(centerX - headR/2, headY, headR, headR);

        // 眼睛（两个括号）
        g2d.setFont(new Font("Monospaced", Font.BOLD, 28));
        g2d.setColor(new Color(255,255,255,220));
        g2d.drawString("{ }", centerX - 30, headY + headR/2 + 10);
    }

    public int getHealth() { return health; }
    public int getMaxHealth() { return maxHealth; }
    public String getName() { return name; }
}
