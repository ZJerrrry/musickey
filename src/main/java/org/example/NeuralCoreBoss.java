package org.example;

import java.awt.*;
import java.util.Random;

public class NeuralCoreBoss implements BossEntity {
    private int health; private final int maxHealth; private final String name; private long t=0; private final Random r=new Random();
    public NeuralCoreBoss(int hp,String name){ this.health=hp; this.maxHealth=hp; this.name=name; }
    public void update(long dt){ t+=dt; }
    public void takeDamage(int dmg){ health=Math.max(0, health-dmg); }
    public void setHealth(int hp){ this.health = Math.max(0, Math.min(hp, maxHealth)); }
    public int getHealth(){ return health; }
    public int getMaxHealth(){ return maxHealth; }
    public String getName(){ return name; }
    public void draw(Graphics2D g,int cx,int cy){
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        double hpPct = health/(double)maxHealth;
        int radius = 150;
        // 外层脉冲环
        for(int layer=0; layer<5; layer++){
            double phase = (t/400.0 + layer*0.6)%1.0;
            int alpha = (int)(120*(1-phase));
            int rr = (int)(radius * (0.4 + phase*0.8));
            g.setColor(new Color(120, (int)(200*hpPct), 255, alpha));
            g.setStroke(new BasicStroke(3f));
            g.drawOval(cx-rr, cy-rr, rr*2, rr*2);
        }
        // 核心发光
        RadialGradientPaint rg = new RadialGradientPaint(new Point(cx,cy), radius/2f,
                new float[]{0f,0.5f,1f}, new Color[]{ new Color(255,255,255,220), new Color(100,200,255,160), new Color(10,30,50,0)});
        g.setPaint(rg);
        g.fillOval(cx-radius/2, cy-radius/2, radius, radius);
        // 网格神经线路
        g.setColor(new Color(180,240,255,150));
        int grid = 8;
        for(int i=-grid;i<=grid;i++){
            int x = cx + i*15;
            g.drawLine(x, cy-120, x, cy+120);
            int y = cy + i*15;
            g.drawLine(cx-120, y, cx+120, y);
        }
        // 随机闪点
        for(int i=0;i<25;i++){
            int px = cx + r.nextInt(240)-120;
            int py = cy + r.nextInt(240)-120;
            g.setColor(new Color(120,200,255, r.nextInt(120)+80));
            g.fillRect(px, py, 3,3);
        }
        // 名称
        g.setColor(Color.WHITE);
        g.setFont(new Font("Monospaced", Font.BOLD, 22));
        g.drawString("NEURAL CORE", cx-90, cy - radius/2 - 20);
    }
}
