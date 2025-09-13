package org.example;

import java.awt.*;
import java.util.Random;

public class VisualEffect {
    private Color color;
    private int x, y;
    private int size = 1;
    private int maxSize = 100;
    private boolean growing = true;

    public VisualEffect(Color color, int x, int y) {
        this.color = color;
        this.x = x;
        this.y = y;
    }

    public void draw(Graphics2D g2d) {
        // 音符效果
        g2d.setColor(color);
        g2d.setStroke(new BasicStroke(3));

        // 随机绘制不同的音符图案
        int type = new Random().nextInt(3);
        if (type == 0) {
            // 八分音符
            g2d.fillOval(x, y, size/2, size/2);
            g2d.drawLine(x + size/4, y + size/2, x + size/4, y + size);
        } else if (type == 1) {
            // 四分音符
            g2d.fillOval(x, y, size/2, size/3);
            g2d.drawLine(x + size/2, y, x + size/2, y + size);
        } else {
            // 音符爆炸效果
            for (int i = 0; i < 8; i++) {
                double angle = Math.PI * 2 * i / 8;
                int endX = (int)(x + Math.cos(angle) * size);
                int endY = (int)(y + Math.sin(angle) * size);
                g2d.drawLine(x, y, endX, endY);
            }
        }

        // 更新大小动画
        if (growing) {
            size += 5;
            if (size >= maxSize) growing = false;
        } else {
            size -= 3;
        }
    }
}
