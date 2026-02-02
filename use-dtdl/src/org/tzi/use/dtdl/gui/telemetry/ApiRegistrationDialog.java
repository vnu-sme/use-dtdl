package org.tzi.use.dtdl.gui.telemetry;

import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.telemetry.HttpPollingAdapter;
import org.tzi.use.dtdl.telemetry.TelemetryAdapter;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Swing dialog to register remote APIs that mimic devices and start polling them.
 *
 * The dialog will use DTDLPluginState.telemetryEngine() and attach HttpPollingAdapter instances.
 * It displays a list of registered adapters, allows adding new ones and stopping/removing them.
 */
public class ApiRegistrationDialog extends JDialog {
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> adapterList = new JList<>(listModel);

    private final JTextField urlField = new JTextField();
    private final JTextField intervalField = new JTextField("2000");
    private final JTextField dtmiField = new JTextField();
    private final JTextField telemetryNameField = new JTextField();
    private final JTextField deviceIdField = new JTextField();
    private final JTextField objectNameField = new JTextField();
    private final JTextField valuePathField = new JTextField();

    public ApiRegistrationDialog(Window owner) {
        super(owner, "Register Device APIs", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8,8));
        JPanel content = new JPanel(new BorderLayout(8,8));
        content.setBorder(new EmptyBorder(8,8,8,8));
        add(content, BorderLayout.CENTER);

        // top: form
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        form.add(new JLabel("URL"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        form.add(urlField, gbc);

        gbc.gridx = 0; gbc.gridy++; gbc.weightx = 0;
        form.add(new JLabel("Interval ms"), gbc);
        gbc.gridx = 1; form.add(intervalField, gbc);

        gbc.gridx = 0; gbc.gridy++; form.add(new JLabel("DTMI"), gbc);
        gbc.gridx = 1; form.add(dtmiField, gbc);

        gbc.gridx = 0; gbc.gridy++; form.add(new JLabel("Telemetry Name"), gbc);
        gbc.gridx = 1; form.add(telemetryNameField, gbc);

        gbc.gridx = 0; gbc.gridy++; form.add(new JLabel("Device ID"), gbc);
        gbc.gridx = 1; form.add(deviceIdField, gbc);

        gbc.gridx = 0; gbc.gridy++; form.add(new JLabel("Object Name"), gbc);
        gbc.gridx = 1; form.add(objectNameField, gbc);

        gbc.gridx = 0; gbc.gridy++; form.add(new JLabel("Value Path (dot)"), gbc);
        gbc.gridx = 1; form.add(valuePathField, gbc);

        content.add(form, BorderLayout.NORTH);

        JPanel center = new JPanel(new BorderLayout(4,4));
        center.add(new JLabel("Registered adapters:"), BorderLayout.NORTH);
        adapterList.setVisibleRowCount(8);
        center.add(new JScrollPane(adapterList), BorderLayout.CENTER);
        content.add(center, BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addBtn = new JButton("Add & Start");
        JButton stopBtn = new JButton("Stop Selected");
        JButton removeBtn = new JButton("Remove Selected");
        JButton closeBtn = new JButton("Close");

        btns.add(addBtn);
        btns.add(stopBtn);
        btns.add(removeBtn);
        btns.add(closeBtn);
        add(btns, BorderLayout.SOUTH);

        addBtn.addActionListener(e -> onAdd());
        stopBtn.addActionListener(e -> onStopSelected());
        removeBtn.addActionListener(e -> onRemoveSelected());
        closeBtn.addActionListener(e -> dispose());

        pack();
        setSize(720, 420);
        setLocationRelativeTo(owner);

        // on close: detach adapters
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                reloadAdapterList();
            }
        });
    }

    private void onAdd() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) { JOptionPane.showMessageDialog(this, "URL required"); return; }
        long interval;
        try { interval = Long.parseLong(intervalField.getText().trim()); } catch (Exception ex) { JOptionPane.showMessageDialog(this, "Invalid interval"); return; }
        String dtmi = blankToNull(dtmiField.getText());
        String tele = blankToNull(telemetryNameField.getText());
        String dev = blankToNull(deviceIdField.getText());
        String obj = blankToNull(objectNameField.getText());
        String path = blankToNull(valuePathField.getText());

        String id = "api-" + UUID.randomUUID().toString().substring(0,8);
        HttpPollingAdapter adapter = new HttpPollingAdapter(id, url, interval, dtmi, tele, dev, obj, path);

        try {
            DTDLPluginState.registerAndAttachAdapter(adapter);
            listModel.addElement(renderAdapterLine(adapter));
            JOptionPane.showMessageDialog(this, "Adapter started: " + id);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to attach adapter:\n" + ex.getMessage());
        }
    }

    private void onStopSelected() {
        List<String> sel = adapterList.getSelectedValuesList();
        if (sel.isEmpty()) { JOptionPane.showMessageDialog(this, "Select one or more adapters to stop"); return; }
        for (String line : sel) {
            String id = parseIdFromLine(line);
            DTDLPluginState.detachAndUnregisterAdapter(id);
            listModel.removeElement(line);
        }
    }

    private void onRemoveSelected() {
        onStopSelected();
    }

    private String blankToNull(String s) { if (s == null) return null; s = s.trim(); return s.isEmpty() ? null : s; }

    private String renderAdapterLine(TelemetryAdapter a) {
        return a.id() + " | " + a.getClass().getSimpleName() + " | url=" + (a instanceof HttpPollingAdapter ? ((HttpPollingAdapter)a).url() : "<n/a>")
                + " | tele=" + (a instanceof HttpPollingAdapter ? (((HttpPollingAdapter)a).telemetryName() == null ? "<any>" : ((HttpPollingAdapter)a).telemetryName()) : "<any>");
    }

    private String parseIdFromLine(String line) {
        if (line == null) return null;
        int sp = line.indexOf(' ');
        if (sp < 0) return line;
        return line.substring(0, sp);
    }

    private void reloadAdapterList() {
        listModel.clear();
        Map<String, ? extends TelemetryAdapter> regs = DTDLPluginState.getRegisteredAdapters();
        for (TelemetryAdapter a : regs.values()) {
            listModel.addElement(renderAdapterLine(a));
        }
    }

    public static void showDialog(Component parent) {
        Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        ApiRegistrationDialog d = new ApiRegistrationDialog(w);
        d.setVisible(true);
    }
}
