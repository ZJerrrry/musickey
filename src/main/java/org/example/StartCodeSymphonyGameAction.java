package org.example;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class StartCodeSymphonyGameAction extends AnAction implements DumbAware {
    private static CodeSymphonyGame existing;
    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (existing != null) {
            existing.toFront();
            existing.requestFocus();
            return;
        }
        SwingUtilities.invokeLater(() -> {
            existing = new CodeSymphonyGame(true);
            existing.addWindowListener(new java.awt.event.WindowAdapter(){
                @Override public void windowClosed(java.awt.event.WindowEvent e){ existing = null; }
                @Override public void windowClosing(java.awt.event.WindowEvent e){ existing = null; }
            });
        });
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        Presentation p = e.getPresentation();
        p.setEnabledAndVisible(true); // 始终显示
    }
}
