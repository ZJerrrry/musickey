package org.example;

import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
        // 启动音乐编程大作战游戏
        SwingUtilities.invokeLater(() -> new CodeSymphonyGame());
    }
}