package org.tzi.use.dtdl.gui;

import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.runtime.telemetry.TelemetryManager;
import org.tzi.use.dtdl.runtime.telemetry.adapters.PollingHttpAdapter;
import org.tzi.use.dtdl.runtime.telemetry.TelemetryMessage;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.net.URI;
import java.util.Map;

public class TelemetryControlDialog extends JDialog {
    private final Session session;
    private final MainWindow parent;
    private final TelemetryManager manager;

    private final JButton startStopBtn = new JButton("Start");
    private final JButton addPollAdapterBtn = new JButton("Add polling adapter");
    private final JButton ingestBtn = new JButton("Ingest (manual)");

    private final JTextField pollUrlField = new JTextField(40); // HTTP endpoint
    private final JTextField pollPeriodField = new JTextField("30", 6); // polling interval (seconds)

    private final JTextField instanceField = new JTextField(36);
    private final JTextField dtmiField = new JTextField(36);
    private final JTextField telemetryNameField = new JTextField(24);
    private final JTextField valueField = new JTextField(24);

    public TelemetryControlDialog(Session session, MainWindow parent) {
        super(parent, "Telemetry Control", true);
        this.session = session;
        this.parent = parent;
        this.manager = DTDLPluginState.telemetryManager();

        setLayout(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(startStopBtn);
        top.add(new JLabel("Polling URL:"));
        top.add(pollUrlField);
        top.add(new JLabel("period(s):"));
        top.add(pollPeriodField);
        top.add(addPollAdapterBtn);
        add(top, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(6,6,6,6);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.gridx = 0; gbc.gridy = 0;
        center.add(new JLabel("Instance Id (optional)"), gbc);
        gbc.gridx = 1;
        center.add(instanceField, gbc);
        gbc.gridx = 0; gbc.gridy++;
        center.add(new JLabel("DTMI (optional)"), gbc);
        gbc.gridx = 1;
        center.add(dtmiField, gbc);
        gbc.gridx = 0; gbc.gridy++;
        center.add(new JLabel("Telemetry name"), gbc);
        gbc.gridx = 1;
        center.add(telemetryNameField, gbc);
        gbc.gridx = 0; gbc.gridy++;
        center.add(new JLabel("Value"), gbc);
        gbc.gridx = 1;
        center.add(valueField, gbc);
        gbc.gridx = 1; gbc.gridy++;
        center.add(ingestBtn, gbc);
        add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton close = new JButton("Close");
        south.add(close);
        add(south, BorderLayout.SOUTH);

        startStopBtn.addActionListener(this::onStartStop);
        addPollAdapterBtn.addActionListener(this::onAddPollAdapter);
        ingestBtn.addActionListener(this::onIngest);
        close.addActionListener(e -> { setVisible(false); dispose(); });

        pack();
        setLocationRelativeTo(parent);
        updateUi();
    }

    private void onStartStop(ActionEvent ev) {
        if (!manager.isRunning()) {
            manager.start();
        } else {
            manager.stop();
        }
        updateUi();
    }

    private void onAddPollAdapter(ActionEvent ev) {
        String url = pollUrlField.getText();
        String periodS = pollPeriodField.getText();
        if (url == null || url.trim().isEmpty()) {
            JOptionPane.showMessageDialog(this, "Provide polling URL", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        long period = 30L;
        try { period = Math.max(1L, Long.parseLong(periodS.trim())); } catch (Exception ignored) {}
        try {
            PollingHttpAdapter a = new PollingHttpAdapter("poll-" + System.currentTimeMillis(), URI.create(url.trim()), period);
            manager.registerAdapter(a);
            JOptionPane.showMessageDialog(this, "Adapter registered", "Info", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to register adapter: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void onIngest(ActionEvent ev) {
        String inst = instanceField.getText(); if (inst != null && inst.trim().isEmpty()) inst = null;
        String dtmi = dtmiField.getText(); if (dtmi != null && dtmi.trim().isEmpty()) dtmi = null;
        String tname = telemetryNameField.getText(); if (tname == null || tname.trim().isEmpty()) { JOptionPane.showMessageDialog(this, "Telemetry name required", "Error", JOptionPane.ERROR_MESSAGE); return; }
        String raw = valueField.getText();
        Object value = raw;
        try {
            if (raw != null && raw.matches("^-?\\d+\\.?\\d*$")) {
                if (raw.contains(".")) value = Double.parseDouble(raw);
                else value = Long.parseLong(raw);
            }
        } catch (Exception ignored) {}
        TelemetryMessage msg = new TelemetryMessage(inst, dtmi, tname.trim(), value, Map.of("source", "manual-ui"));
        boolean queued = manager.ingest(msg);
        JOptionPane.showMessageDialog(this, "Queued: " + queued, "Info", JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateUi() {
        if (manager.isRunning()) {
            startStopBtn.setText("Stop");
            addPollAdapterBtn.setEnabled(true);
            ingestBtn.setEnabled(true);
        } else {
            startStopBtn.setText("Start");
            addPollAdapterBtn.setEnabled(true);
            ingestBtn.setEnabled(false);
        }
    }
}