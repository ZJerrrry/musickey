package org.example;

import java.awt.Graphics2D;

public interface BossEntity {
    void update(long dt);
    void takeDamage(int dmg);
    void draw(Graphics2D g2d, int centerX, int centerY);
    int getHealth();
    int getMaxHealth();
    String getName();
    void setHealth(int hp); // 新增用于存档恢复
}
