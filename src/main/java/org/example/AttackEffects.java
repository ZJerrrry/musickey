package org.example;

import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/** 攻击特效集合：包含抽象基类与具体烟花/波纹实现 */
public class AttackEffects {
    /** 基类 */
    public static abstract class AttackEffect {
        protected boolean alive = true;
        public boolean isAlive(){ return alive; }
        public abstract void update(long dt);
        public abstract void draw(Graphics2D g2d);
    }

    /** 粒子粒状烟花 */
    public static class FireworkEffect extends AttackEffect {
        private static class P { double x,y,vx,vy,life,maxLife; Color c; double size; }
        private final List<P> ps = new ArrayList<>();
        private final Random r = new Random();
        private final int cx, cy;
        public FireworkEffect(int cx, int cy, Color base) {
            this.cx = cx; this.cy = cy;
            int count = 65 + r.nextInt(25);
            for(int i=0;i<count;i++){
                P p = new P();
                double ang = r.nextDouble()*Math.PI*2;
                double sp = 60 + r.nextDouble()*180;
                p.vx = Math.cos(ang)*sp;
                p.vy = Math.sin(ang)*sp;
                p.x = cx; p.y = cy;
                p.life = 0; p.maxLife = 600 + r.nextInt(500);
                int rr = Math.min(255, base.getRed() + r.nextInt(80));
                int gg = Math.min(255, base.getGreen() + r.nextInt(80));
                int bb = Math.min(255, base.getBlue() + r.nextInt(80));
                p.c = new Color(rr,gg,bb,255);
                p.size = 3 + r.nextDouble()*5;
                ps.add(p);
            }
        }
        @Override public void update(long dt) {
            if(ps.isEmpty()){ alive = false; return; }
            double dts = dt/1000.0;
            Iterator<P> it = ps.iterator();
            while(it.hasNext()){
                P p = it.next();
                p.life += dt;
                p.x += p.vx * dts;
                p.y += p.vy * dts;
                p.vy += 40 * dts; // 重力微下坠
                if(p.life > p.maxLife) it.remove();
            }
            if(ps.isEmpty()) alive = false;
        }
        @Override public void draw(Graphics2D g2d) {
            Composite old = g2d.getComposite();
            for(P p: ps){
                float alpha = (float)(1.0 - p.life / p.maxLife);
                alpha = Math.max(0, Math.min(1, alpha));
                g2d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
                g2d.setColor(p.c);
                g2d.fillOval((int)(p.x - p.size/2), (int)(p.y - p.size/2), (int)p.size, (int)p.size);
            }
            g2d.setComposite(old);
        }
    }

    /** 波纹扩散 */
    public static class RippleEffect extends AttackEffect {
        private final int cx, cy;
        private double radius = 10;
        private double maxRadius = 260;
        private double thickness = 8;
        private double elapsed = 0;
        private final Color base;
        public RippleEffect(int cx, int cy, Color base){ this.cx=cx; this.cy=cy; this.base=base; }
        @Override public void update(long dt){
            elapsed += dt;
            radius += dt * 0.25; // 速度
            thickness = Math.max(1, thickness - dt * 0.008);
            if(radius >= maxRadius) alive = false;
        }
        @Override public void draw(Graphics2D g2d){
            float alpha = (float)(1 - radius / maxRadius);
            alpha = Math.max(0, alpha);
            g2d.setStroke(new BasicStroke((float)thickness));
            g2d.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), (int)(alpha*180)));
            g2d.drawOval((int)(cx - radius), (int)(cy - radius), (int)(radius*2), (int)(radius*2));
            // 内部柔光
            RadialGradientPaint rg = new RadialGradientPaint(
                    new Point(cx,cy), (float)radius,
                    new float[]{0f,1f},
                    new Color[]{new Color(base.getRed(), base.getGreen(), base.getBlue(), (int)(alpha*120)), new Color(base.getRed(), base.getGreen(), base.getBlue(), 0)});
            Paint old = g2d.getPaint();
            g2d.setPaint(rg);
            g2d.fillOval((int)(cx - radius), (int)(cy - radius), (int)(radius*2), (int)(radius*2));
            g2d.setPaint(old);
        }
    }
}

