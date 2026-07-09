package com.overzealouspelican.awsui.panel;

import com.overzealouspelican.awsui.service.CodePipelineService;
import com.overzealouspelican.awsui.util.UITheme;

import javax.swing.*;
import java.awt.*;
import java.util.List;

final class CodePipelinePhaseGraphPanel extends JPanel {
    private static final int MAX_VISIBLE_ACTIONS = 5;

    CodePipelinePhaseGraphPanel() {
        setLayout(new BorderLayout());
        setBackground(UITheme.surfaceBackground());
        showEmpty();
    }

    void showEmpty() {
        showMessage("Select a pipeline to view phase states.");
    }

    void showLoading() {
        showMessage("Loading phase states...");
    }

    void showMessage(String message) {
        removeAll();
        JLabel label = new JLabel(message);
        label.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel messagePanel = new JPanel(new GridBagLayout());
        messagePanel.setOpaque(false);
        messagePanel.add(label);

        add(messagePanel, BorderLayout.CENTER);
        revalidate();
        repaint();
    }

    void setPhases(List<CodePipelineService.PhaseState> phases) {
        if (phases == null || phases.isEmpty()) {
            showMessage("No phases available.");
            return;
        }

        removeAll();

        JPanel rail = new JPanel();
        rail.setOpaque(false);
        rail.setLayout(new BoxLayout(rail, BoxLayout.X_AXIS));
        rail.setBorder(BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM));

        for (int i = 0; i < phases.size(); i++) {
            CodePipelineService.PhaseState phase = phases.get(i);
            rail.add(createPhaseCard(phase));
            if (i < phases.size() - 1) {
                rail.add(Box.createHorizontalStrut(UITheme.SPACING_SM));
                rail.add(createConnectorLabel());
                rail.add(Box.createHorizontalStrut(UITheme.SPACING_SM));
            }
        }

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.setOpaque(false);
        wrapper.add(rail, BorderLayout.WEST);

        add(wrapper, BorderLayout.NORTH);
        revalidate();
        repaint();
    }

    private JComponent createConnectorLabel() {
        JLabel label = new JLabel("→");
        label.setFont(label.getFont().deriveFont(Font.BOLD, 18f));
        label.setForeground(UIManager.getColor("Label.disabledForeground"));

        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.add(label);
        return panel;
    }

    private JComponent createPhaseCard(CodePipelineService.PhaseState phase) {
        JPanel card = new JPanel(new BorderLayout(UITheme.SPACING_SM, UITheme.SPACING_SM));
        card.setOpaque(true);
        card.setBackground(UITheme.surfaceBackground());
        card.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(198, 205, 214)),
            BorderFactory.createEmptyBorder(UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM, UITheme.SPACING_SM)
        ));
        card.setPreferredSize(new Dimension(220, 190));
        card.setMinimumSize(new Dimension(220, 190));

        JLabel title = new JLabel(phase.name());
        title.setFont(title.getFont().deriveFont(Font.BOLD, 13f));

        JLabel statusBadge = createStatusBadge(phase.status());

        JPanel header = new JPanel(new BorderLayout(UITheme.SPACING_SM, 0));
        header.setOpaque(false);
        header.add(title, BorderLayout.WEST);
        header.add(statusBadge, BorderLayout.EAST);

        JPanel actionsPanel = new JPanel();
        actionsPanel.setOpaque(false);
        actionsPanel.setLayout(new BoxLayout(actionsPanel, BoxLayout.Y_AXIS));

        List<CodePipelineService.PhaseActionState> actions = phase.actions();
        if (actions == null || actions.isEmpty()) {
            JLabel empty = new JLabel("No actions");
            empty.setForeground(UIManager.getColor("Label.disabledForeground"));
            actionsPanel.add(empty);
        } else {
            int visible = Math.min(actions.size(), MAX_VISIBLE_ACTIONS);
            for (int i = 0; i < visible; i++) {
                actionsPanel.add(createActionRow(actions.get(i)));
                if (i < visible - 1) {
                    actionsPanel.add(Box.createVerticalStrut(4));
                }
            }
            if (actions.size() > visible) {
                actionsPanel.add(Box.createVerticalStrut(4));
                JLabel more = new JLabel("+" + (actions.size() - visible) + " more");
                more.setForeground(UIManager.getColor("Label.disabledForeground"));
                actionsPanel.add(more);
            }
        }

        card.add(header, BorderLayout.NORTH);
        card.add(actionsPanel, BorderLayout.CENTER);
        return card;
    }

    private JComponent createActionRow(CodePipelineService.PhaseActionState action) {
        JPanel row = new JPanel(new BorderLayout(UITheme.SPACING_SM, 0));
        row.setOpaque(false);

        JLabel bullet = new JLabel("●");
        bullet.setForeground(colorForStatus(action.status()));
        bullet.setFont(bullet.getFont().deriveFont(10f));

        JLabel name = new JLabel(truncate(action.name(), 24));
        name.setToolTipText(action.name());

        JLabel status = new JLabel(shortStatus(action.status()));
        status.setForeground(colorForStatus(action.status()));

        JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        left.setOpaque(false);
        left.add(bullet);
        left.add(Box.createHorizontalStrut(6));
        left.add(name);

        row.add(left, BorderLayout.WEST);
        row.add(status, BorderLayout.EAST);
        return row;
    }

    private JLabel createStatusBadge(String status) {
        JLabel label = new JLabel(" " + shortStatus(status) + " ");
        label.setOpaque(true);
        label.setForeground(Color.WHITE);
        label.setBackground(colorForStatus(status));
        label.setBorder(BorderFactory.createEmptyBorder(2, 6, 2, 6));
        label.setFont(label.getFont().deriveFont(Font.BOLD, 11f));
        return label;
    }

    private String shortStatus(String status) {
        if (status == null || status.isBlank() || "-".equals(status)) {
            return "N/A";
        }
        if (status.contains("InProgress")) {
            return "Running";
        }
        if (status.contains("Succeeded")) {
            return "OK";
        }
        if (status.contains("Failed")) {
            return "Failed";
        }
        if (status.contains("Stopping")) {
            return "Stopping";
        }
        if (status.contains("Stopped")) {
            return "Stopped";
        }
        if (status.contains("Cancelled")) {
            return "Cancelled";
        }
        return status;
    }

    private Color colorForStatus(String status) {
        if (status == null || status.isBlank() || "-".equals(status)) {
            return new Color(120, 120, 120);
        }
        if (status.contains("Succeeded")) {
            return new Color(52, 153, 92);
        }
        if (status.contains("Failed")) {
            return new Color(192, 57, 57);
        }
        if (status.contains("InProgress")) {
            return new Color(52, 110, 200);
        }
        if (status.contains("Stopping") || status.contains("Stopped")) {
            return new Color(193, 132, 39);
        }
        if (status.contains("Cancelled")) {
            return new Color(110, 110, 110);
        }
        return new Color(86, 101, 115);
    }

    private String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }
        return text.substring(0, Math.max(0, max - 1)) + "…";
    }
}