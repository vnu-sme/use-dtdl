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
    private static final AtomicReference<TelemetryEventListener> uiListenerRef = new AtomicReference<>();

    @Override
    public void performAction(IPluginAction action) {
        MainWindow mainWindow = action.getParent();
        Session s = action.getSession();
        try {
            // ensure engine running & available
            TelemetryEngine engine = DTDLPluginState.startTelemetryRuntime(s);

            // register UI listener
            TelemetryEngine eng = DTDLPluginState.telemetryEngine();
            uiListenerRef.updateAndGet(prev -> {
                if (prev == null) {
                    TelemetryEventListener l = (adapterId, message) -> {
                        SwingUtilities.invokeLater(() -> {
                            MainWindow mw = MainWindow.instance();
                            JOptionPane.showMessageDialog(
                                    mw,
                                    message,
                                    "Telemetry stopped",
                                    JOptionPane.WARNING_MESSAGE
                            );
                        });
                    };

                    // register listener with engine (important!)
                    if (eng != null) {
                        try {
                            eng.addListener(l);
                        } catch (Throwable t) {
                            System.err.println("[LISTENER] Failed to add listener to engine: " + t.getMessage());
                            t.printStackTrace(System.err);
                        }
                    } else {
                        System.err.println("[LISTENER] telemetry engine is null; listener not registered");
                    }

                    // return the new listener so uiListenerRef gets updated
                    return l;
                }
                return prev;
            });

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

}