package org.tzi.use.dtdl.gui.operations;

import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.integration.operations.OperationCatalog;
import org.tzi.use.dtdl.integration.operations.OperationExecutionRule;
import org.tzi.use.dtdl.integration.operations.OperationExecutionService;
import org.tzi.use.dtdl.integration.operations.OperationResult;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.uml.mm.MClassInvariant;
import org.tzi.use.uml.sys.MObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.List;

public final class OperationSetupDialog extends JDialog {
    private final Session session;
    private final MainWindow parent;
    private final OperationExecutionService service;
    private final OperationCatalog catalog;

    private final JComboBox<String> manualObjectCombo = new JComboBox<>();
    private final JComboBox<String> manualOperationCombo = new JComboBox<>();
    private final JTextArea manualLogArea = new JTextArea(8, 50);

    private final JComboBox<String> ruleObjectCombo = new JComboBox<>();
    private final JComboBox<String> ruleOperationCombo = new JComboBox<>();
    private final JComboBox<String> ruleConstraintCombo = new JComboBox<>();
    private final JComboBox<String> ruleTruthCombo = new JComboBox<>(new String[]{"true", "false"});
    private final JTextArea ruleLogArea = new JTextArea(12, 50);

    public OperationSetupDialog(MainWindow parent, Session session) {
        super(parent, "Operation Setup", true);
        this.parent = parent;
        this.session = session;
        this.service = DTDLPluginState.operationService(session);
        this.catalog = DTDLPluginState.operationCatalog();

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(920, 640));
        setLayout(new BorderLayout());

        add(buildHeader(), BorderLayout.NORTH);

        JTabbedPane tabs = new JTabbedPane();
        tabs.setBorder(new EmptyBorder(10, 10, 10, 10));
        tabs.addTab("Manual Run", buildManualPanel());
        tabs.addTab("Constraint Run", buildRulePanel());

        add(tabs, BorderLayout.CENTER);
        add(buildBottomPanel(), BorderLayout.SOUTH);

        refreshObjects();
        refreshConstraints();
        refreshAllLogs();

        pack();
        setLocationRelativeTo(parent);
    }

    private JComponent buildHeader() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(new EmptyBorder(14, 16, 10, 16));

        JLabel title = new JLabel("Operation Setup");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 20f));

        JLabel subtitle = new JLabel("Run USE operations manually or bind them to constraint conditions.");
        subtitle.setForeground(new Color(100, 100, 100));

        JPanel text = new JPanel(new GridLayout(2, 1, 0, 3));
        text.setOpaque(false);
        text.add(title);
        text.add(subtitle);

        panel.add(text, BorderLayout.WEST);
        return panel;
    }

    private JPanel buildManualPanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new TitledBorder("Manual execution"));
        form.setBackground(Color.WHITE);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.25;
        form.add(new JLabel("Object"), c);
        c.gridx = 1;
        c.weightx = 0.75;
        form.add(manualObjectCombo, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0.25;
        form.add(new JLabel("Operation"), c);
        c.gridx = 1;
        c.weightx = 0.75;
        form.add(manualOperationCombo, c);

        JButton runButton = new JButton("Run operation");
        runButton.addActionListener(e -> runManualOperation());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(runButton);

        manualObjectCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                refreshManualOperations();
            }
        });

        manualLogArea.setEditable(false);
        manualLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        manualLogArea.setLineWrap(true);
        manualLogArea.setWrapStyleWord(true);

        JScrollPane logScroll = new JScrollPane(manualLogArea);
        logScroll.setBorder(new TitledBorder("Execution log"));

        panel.add(form, BorderLayout.NORTH);
        panel.add(buttonRow, BorderLayout.CENTER);
        panel.add(logScroll, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildRulePanel() {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(new EmptyBorder(12, 12, 12, 12));

        JPanel form = new JPanel(new GridBagLayout());
        form.setBorder(new TitledBorder("Constraint-triggered rule"));
        form.setBackground(Color.WHITE);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1.0;

        c.gridx = 0;
        c.gridy = 0;
        c.weightx = 0.25;
        form.add(new JLabel("Object"), c);
        c.gridx = 1;
        c.weightx = 0.75;
        form.add(ruleObjectCombo, c);

        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0.25;
        form.add(new JLabel("Operation"), c);
        c.gridx = 1;
        c.weightx = 0.75;
        form.add(ruleOperationCombo, c);

        c.gridx = 0;
        c.gridy = 2;
        c.weightx = 0.25;
        form.add(new JLabel("Constraint"), c);
        c.gridx = 1;
        c.weightx = 0.75;
        form.add(ruleConstraintCombo, c);

        c.gridx = 0;
        c.gridy = 3;
        c.weightx = 0.25;
        form.add(new JLabel("Fire when"), c);
        c.gridx = 1;
        c.weightx = 0.75;
        form.add(ruleTruthCombo, c);

        JButton addButton = new JButton("Register rule");
        addButton.addActionListener(e -> registerRule());

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        buttonRow.setOpaque(false);
        buttonRow.add(addButton);

        ruleObjectCombo.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                refreshRuleOperations();
            }
        });

        ruleLogArea.setEditable(false);
        ruleLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        ruleLogArea.setLineWrap(true);
        ruleLogArea.setWrapStyleWord(true);

        JScrollPane logScroll = new JScrollPane(ruleLogArea);
        logScroll.setBorder(new TitledBorder("Rule status and history"));

        panel.add(form, BorderLayout.NORTH);
        panel.add(buttonRow, BorderLayout.CENTER);
        panel.add(logScroll, BorderLayout.SOUTH);

        return panel;
    }

    private JPanel buildBottomPanel() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        panel.setBorder(new EmptyBorder(8, 8, 10, 8));

        JButton refreshButton = new JButton("Refresh");
        JButton closeButton = new JButton("Close");

        refreshButton.addActionListener(e -> {
            refreshObjects();
            refreshConstraints();
            refreshAllLogs();
        });

        closeButton.addActionListener(e -> dispose());

        panel.add(refreshButton);
        panel.add(closeButton);
        return panel;
    }

    private void refreshObjects() {
        String previousManual = (String) manualObjectCombo.getSelectedItem();
        String previousRule = (String) ruleObjectCombo.getSelectedItem();

        manualObjectCombo.removeAllItems();
        ruleObjectCombo.removeAllItems();

        if (session.system() != null) {
            for (MObject obj : session.system().state().allObjects()) {
                manualObjectCombo.addItem(obj.name());
                ruleObjectCombo.addItem(obj.name());
            }
        }

        restoreSelection(manualObjectCombo, previousManual);
        restoreSelection(ruleObjectCombo, previousRule);

        refreshManualOperations();
        refreshRuleOperations();
    }

    private void refreshConstraints() {
        String previous = (String) ruleConstraintCombo.getSelectedItem();
        ruleConstraintCombo.removeAllItems();

        if (session.system() != null) {
            for (MClassInvariant inv : session.system().model().classInvariants()) {
                if (inv != null) {
                    ruleConstraintCombo.addItem(inv.qualifiedName() != null ? inv.qualifiedName() : inv.name());
                }
            }
        }

        restoreSelection(ruleConstraintCombo, previous);
    }

    private void refreshManualOperations() {
        manualOperationCombo.removeAllItems();

        String objectName = (String) manualObjectCombo.getSelectedItem();
        if (objectName == null || session.system() == null) {
            return;
        }

        MObject obj = session.system().state().objectByName(objectName);
        if (obj == null || obj.cls() == null) {
            return;
        }

        String className = obj.cls().name();
        for (OperationCatalog.OperationDescriptor d : catalog.operationsForClass(className)) {
            manualOperationCombo.addItem(d.operationName);
        }

        if (manualOperationCombo.getItemCount() == 0) {
            for (String op : classOperationNames(obj.cls())) {
                manualOperationCombo.addItem(op);
            }
        }
    }

    private void refreshRuleOperations() {
        ruleOperationCombo.removeAllItems();

        String objectName = (String) ruleObjectCombo.getSelectedItem();
        if (objectName == null || session.system() == null) {
            return;
        }

        MObject obj = session.system().state().objectByName(objectName);
        if (obj == null || obj.cls() == null) {
            return;
        }

        String className = obj.cls().name();
        for (OperationCatalog.OperationDescriptor d : catalog.operationsForClass(className)) {
            ruleOperationCombo.addItem(d.operationName);
        }

        if (ruleOperationCombo.getItemCount() == 0) {
            for (String op : classOperationNames(obj.cls())) {
                ruleOperationCombo.addItem(op);
            }
        }
    }

    private List<String> classOperationNames(MClass cls) {
        try {
            var m = cls.getClass().getMethod("operations");
            Object r = m.invoke(cls);
            if (r instanceof java.util.Collection<?> c) {
                java.util.ArrayList<String> out = new java.util.ArrayList<>();
                for (Object o : c) {
                    if (o == null) continue;
                    try {
                        var nameMethod = o.getClass().getMethod("name");
                        Object v = nameMethod.invoke(o);
                        if (v != null) out.add(String.valueOf(v));
                    } catch (Throwable ignored) {
                        try {
                            var field = o.getClass().getDeclaredField("fName");
                            field.setAccessible(true);
                            Object v = field.get(o);
                            if (v != null) out.add(String.valueOf(v));
                        } catch (Throwable ignored2) {
                        }
                    }
                }
                return out;
            }
        } catch (Throwable ignored) {
        }
        return List.of();
    }

    private void runManualOperation() {
        String objectName = (String) manualObjectCombo.getSelectedItem();
        String opName = (String) manualOperationCombo.getSelectedItem();

        if (objectName == null || opName == null) {
            JOptionPane.showMessageDialog(this, "Select object and operation first", "Missing input", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int ok = JOptionPane.showConfirmDialog(
                this,
                "Run operation " + opName + " on object " + objectName + "?",
                "Confirm",
                JOptionPane.YES_NO_OPTION
        );

        if (ok != JOptionPane.YES_OPTION) {
            return;
        }

        OperationResult result = service.executeManual(objectName, opName);
        manualLogArea.append(result.toString() + "\n");
        refreshAllLogs();
    }

    private void registerRule() {
        String objectName = (String) ruleObjectCombo.getSelectedItem();
        String opName = (String) ruleOperationCombo.getSelectedItem();
        String constraintName = (String) ruleConstraintCombo.getSelectedItem();
        String truth = (String) ruleTruthCombo.getSelectedItem();

        if (objectName == null || opName == null || constraintName == null || truth == null) {
            JOptionPane.showMessageDialog(this, "Select object, operation, constraint and trigger value first", "Missing input", JOptionPane.ERROR_MESSAGE);
            return;
        }

        MObject obj = session.system().state().objectByName(objectName);
        if (obj == null || obj.cls() == null) {
            JOptionPane.showMessageDialog(this, "Selected object is not valid", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        OperationExecutionRule rule = new OperationExecutionRule(
                objectName,
                obj.cls().name(),
                opName,
                constraintName,
                Boolean.parseBoolean(truth)
        );
        service.registerRule(rule);
        ruleLogArea.append("Registered: " + rule + "\n");
        refreshAllLogs();
    }

    private void refreshAllLogs() {
        StringBuilder sb = new StringBuilder();
        sb.append("Rules\n");
        for (OperationExecutionRule r : service.rules()) {
            sb.append(r).append('\n');
            sb.append("  status=").append(r.lastStatus).append('\n');
            sb.append("  message=").append(r.lastMessage).append('\n');
            sb.append("  fires=").append(r.fireCount).append('\n');
        }
        sb.append('\n');
        sb.append("Recent results\n");
        for (OperationResult r : service.history()) {
            sb.append(r).append('\n');
        }
        ruleLogArea.setText(sb.toString());
    }

    private static void restoreSelection(JComboBox<String> combo, String value) {
        if (value == null) {
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (value.equals(combo.getItemAt(i))) {
                combo.setSelectedIndex(i);
                return;
            }
        }
    }
}