package org.tzi.use.dtdl.actions;

import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.dtdl.runtime.telemetry.TelemetryManager;

import javax.swing.*;
import java.awt.*;
import java.util.Map;

public class ManageTelemetryAction implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        Session session = pluginAction.getSession();
        MainWindow parent = pluginAction.getParent();

        TelemetryManager mgr = DTDLPluginState.telemetryManager();
        if (mgr == null) {
            JOptionPane.showMessageDialog(parent, "Telemetry manager not initialized. Call PluginBootstrap.initWithDefaults() first.", "Telemetry", JOptionPane.WARNING_MESSAGE);
            return;
        }

        JDialog dlg = new JDialog(parent, "Telemetry Manager", true);
        dlg.setLayout(new BorderLayout());
        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton startBtn = new JButton("Start");
        JButton stopBtn = new JButton("Stop");
        JButton statsBtn = new JButton("Stats");
        top.add(startBtn);
        top.add(stopBtn);
        top.add(statsBtn);

        JPanel manual = new JPanel(new GridLayout(5,2));
        JTextField instanceId = new JTextField();
        JTextField dtmi = new JTextField();
        JTextField telemetryName = new JTextField();
        JTextField value = new JTextField();
        JButton send = new JButton("Send");

        manual.add(new JLabel("instanceId (optional)")); manual.add(instanceId);
        manual.add(new JLabel("dtmi (optional)")); manual.add(dtmi);
        manual.add(new JLabel("telemetryName")); manual.add(telemetryName);
        manual.add(new JLabel("value (string/number)")); manual.add(value);
        manual.add(new JLabel()); manual.add(send);

        dlg.add(top, BorderLayout.NORTH);
        dlg.add(manual, BorderLayout.CENTER);

        startBtn.addActionListener(e -> {
            mgr.start();
            JOptionPane.showMessageDialog(dlg, "Telemetry manager started.");
        });
        stopBtn.addActionListener(e -> {
            mgr.stop();
            JOptionPane.showMessageDialog(dlg, "Telemetry manager stopped.");
        });
        statsBtn.addActionListener(e -> {
            String s = "accepted=" + mgr.getAcceptedCount() + " dropped=" + mgr.getDroppedCount();
            JOptionPane.showMessageDialog(dlg, s);
        });
        send.addActionListener(e -> {
            String iid = instanceId.getText().trim(); if (iid.isEmpty()) iid = null;
            String d = dtmi.getText().trim(); if (d.isEmpty()) d = null;
            String t = telemetryName.getText().trim();
            if (t.isEmpty()) { JOptionPane.showMessageDialog(dlg, "telemetryName required"); return; }
            String vtxt = value.getText().trim();
            Object v;
            try {
                if (vtxt.contains(".")) v = Double.parseDouble(vtxt);
                else v = Integer.parseInt(vtxt);
            } catch (Exception ex) {
                v = vtxt;
            }
            boolean ok = mgr.manualIngest(iid, d, t, v, Map.of("source","manual-ui"));
            JOptionPane.showMessageDialog(dlg, ok ? "ingested" : "not accepted (dead-lettered)");
        });

        dlg.pack();
        dlg.setLocationRelativeTo(parent);
        dlg.setVisible(true);
    }
}
