package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.gui.telemetry.ApiRegistrationDialog;
import org.tzi.use.dtdl.telemetry.SimulatorAdapter;
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

            // optional demo: attach a short-lived simulator and send one event (remove if unnecessary)
//            SimulatorAdapter sim = new SimulatorAdapter("simulator-demo");
//            engine.attachAdapter(sim);
//            sim.post(
//                    "dtmi:com:example:AirConditioner;1",
//                    "device-demo-1",
//                    "AirConditioner_01",
//                    "currentTemperature",
//                    26.5,
//                    Instant.now(),
//                    Collections.singletonMap("unit", "C")
//            );

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