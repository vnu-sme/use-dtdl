package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.gui.telemetry.visualizer.TelemetryMonitorView;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.main.ViewFrame;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

import javax.swing.*;
import java.awt.*;

public class OpenTelemetryMonitorAction implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        Session session = pluginAction.getSession();
        MainWindow parent = pluginAction.getParent();

        TelemetryMonitorView view = new TelemetryMonitorView(parent, session);
        ViewFrame frame = new ViewFrame("Telemetry Monitor", view, "command-list.png");

        JComponent content = (JComponent) frame.getContentPane();
        content.setLayout(new BorderLayout());
        content.add(view, BorderLayout.CENTER);

        parent.addViewFrame(frame, false);
    }

    @Override
    public boolean shouldBeEnabled(IPluginAction pluginAction) {
        return true;
    }
}