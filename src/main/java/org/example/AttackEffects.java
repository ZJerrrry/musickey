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

    /** 超级烟花特效 */
    public static class SuperFireworkEffect extends AttackEffect {
        private final List<FireworkEffect> bursts = new ArrayList<>();
        private long elapsed = 0;
        private final int durationMs = 1600;
        private final Random r = new Random();
        private final int w, h;
        private final Color base;
        public SuperFireworkEffect(int w, int h, Color base){
            this.w = w; this.h = h; this.base = base;
            // 初始三组
            for(int i=0;i<3;i++) bursts.add(new FireworkEffect(r.nextInt(w), r.nextInt(h/2)+h/8, base));
        }
        @Override public void update(long dt){
            elapsed += dt;
            if (elapsed < durationMs) {
                if (r.nextDouble() < 0.18) {
                    bursts.add(new FireworkEffect(r.nextInt(w), r.nextInt(h/2)+h/8, base));
                }
            }
            Iterator<FireworkEffect> it = bursts.iterator();
            while(it.hasNext()){
                FireworkEffect f = it.next();
                f.update(dt);
                if(!f.isAlive()) it.remove();
            }
            if (elapsed >= durationMs && bursts.isEmpty()) alive = false;
        }
        @Override public void draw(Graphics2D g2d){
            for(FireworkEffect f: bursts) f.draw(g2d);
        }
    }

    /** 全屏波纹特效 */
    public static class FullScreenRippleEffect extends AttackEffect {
        private final int cx, cy, maxR;
        private double r = 10;
        private final Color base;
        public FullScreenRippleEffect(int width, int height, Color base){
            this.cx = width/2; this.cy = height/2; this.maxR = (int)(Math.hypot(width, height)/1.2); this.base = base;
        }
        @Override public void update(long dt){
            r += dt * 0.55; // 更快扩散
            if (r >= maxR) alive = false;
        }
        @Override public void draw(Graphics2D g2d){
            float alpha = (float)(1 - r / maxR);
            alpha = Math.max(0, alpha);
            g2d.setStroke(new BasicStroke(6f));
            g2d.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), (int)(alpha*200)));
            g2d.drawOval((int)(cx - r), (int)(cy - r), (int)(r*2), (int)(r*2));
        }
    }

    /** 反击冲击波特效 */
    public static class ShockwaveEffect extends AttackEffect {
        private final int cx, cy; private double r=5; private final int maxR; private double thickness=22;
        public ShockwaveEffect(int width, int height){
            this.cx = width/2; this.cy = height/3; this.maxR = (int)Math.max(width, height)*3/4;
        }
        @Override public void update(long dt){
            r += dt * 0.7; thickness = Math.max(2, thickness - dt*0.03);
            if (r >= maxR) alive = false;
        }
        @Override public void draw(Graphics2D g2d){
            float alpha = (float)(1 - r/maxR);
            g2d.setStroke(new BasicStroke((float)thickness));
            g2d.setColor(new Color(255,80,80,(int)(alpha*180)));
            g2d.drawOval((int)(cx - r), (int)(cy - r), (int)(r*2), (int)(r*2));
        }
    }

    /** Core脉冲特效 */
    public static class CorePulseEffect extends AttackEffect {
        private double r=10; private final int cx,cy; private final int maxR; private final Color c;
        public CorePulseEffect(int w,int h,Color c){ this.cx=w/2; this.cy=h/3; this.maxR=(int)(Math.max(w,h)*0.9); this.c=c; }
        @Override public void update(long dt){ r += dt*0.6; if(r>=maxR) alive=false; }
        @Override public void draw(Graphics2D g){ float a=(float)(1-r/maxR); g.setColor(new Color(c.getRed(),c.getGreen(),c.getBlue(),(int)(a*200))); g.setStroke(new BasicStroke(10f)); g.drawOval((int)(cx-r),(int)(cy-r),(int)(r*2),(int)(r*2)); }
    }
    /** 终极技能覆盖特效 */
    public static class UltimateOverlayEffect extends AttackEffect {
        private long life=0; private final long maxLife=1800; private final Color base;
        public UltimateOverlayEffect(Color base){ this.base=base; }
        @Override public void update(long dt){ life+=dt; if(life>maxLife) alive=false; }
        @Override public void draw(Graphics2D g){ float p = Math.min(1f, life/(float)maxLife); int alpha = (int)(255*(1-Math.abs(0.5f-p)*2)); g.setColor(new Color(base.getRed(),base.getGreen(),base.getBlue(), Math.min(200,alpha))); g.fillRect(0,0,g.getClipBounds().width,g.getClipBounds().height); }
    }
}
