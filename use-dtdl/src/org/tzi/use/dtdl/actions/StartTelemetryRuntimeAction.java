package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.gui.telemetry.ApiRegistrationDialog;
import org.tzi.use.dtdl.telemetry.TelemetryEngine;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.gui.main.MainWindow;

import javax.swing.*;
import java.time.Instant;
import java.util.Collections;


public final class StartTelemetryRuntimeAction implements IPluginActionDelegate {

    @Override
    public void performAction(IPluginAction action) {
        MainWindow mainWindow = action.getParent();
        try {
            // ensure engine running & available
            TelemetryEngine engine = DTDLPluginState.startTelemetryRuntime();

            // open the dialog (modal) so user can add/register HTTP adapters
            ApiRegistrationDialog.showDialog(mainWindow);

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