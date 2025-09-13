package org.example;

import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

public class CodeSymphonyToolWindowFactory implements ToolWindowFactory, DumbAware {
    private CodeSymphonyGame embeddedInstance;

    @Override
    public void createToolWindowContent(@NotNull Project project, @NotNull ToolWindow toolWindow) {
        if (embeddedInstance == null) {
            embeddedInstance = new CodeSymphonyGame(true, true); // pluginMode + embedded
        }
        JPanel panel = embeddedInstance.getRootPanel();
        ContentFactory cf = ContentFactory.getInstance();
        Content content = cf.createContent(panel, "Game", false);
        toolWindow.getContentManager().addContent(content);
    }

    @Override
    public boolean shouldBeAvailable(@NotNull Project project) { return true; }
}

