package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.gui.telemetry.ApiRegistrationDialog;
import org.tzi.use.dtdl.telemetry.TelemetryEngine;
import org.tzi.use.dtdl.telemetry.TelemetryEventListener;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.gui.main.MainWindow;

import javax.swing.*;
import java.time.Instant;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;


public final class StartTelemetryRuntimeAction implements IPluginActionDelegate {
    private static TelemetryEventListener uiListener;

    @Override
    public void performAction(IPluginAction action) {
        MainWindow mainWindow = action.getParent();
        Session s = action.getSession();
        try {
            // ensure engine running & available
            TelemetryEngine eng = DTDLPluginState.startTelemetryRuntime(s);

            // register UI listener
            TelemetryEngine engine = DTDLPluginState.telemetryEngine();
            if (uiListener == null) {
                uiListener = createUiListener();
                if (engine != null) {
                    engine.addListener(uiListener);
                }
            }

            // open the dialog (modal) so user can add/register HTTP adapters
            ApiRegistrationDialog.showDialog(mainWindow, s);

        } catch (Exception e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(
                    mainWindow,
                    "Failed to start telemetry runtime:\n" + e.getMessage(),
                    "DTDL Telemetry",
                    JOptionPane.ERROR_MESSAGE
            );
        }
    }

    private TelemetryEventListener createUiListener() {
        return (adapterId, message) -> SwingUtilities.invokeLater(() ->
                JOptionPane.showMessageDialog(
                        MainWindow.instance(),
                        message,
                        "Telemetry stopped",
                        JOptionPane.WARNING_MESSAGE
                )
        );
    }

}