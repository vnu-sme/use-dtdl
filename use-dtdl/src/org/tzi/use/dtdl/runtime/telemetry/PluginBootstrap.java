package org.tzi.use.dtdl.runtime.telemetry;

import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.runtime.DTDLSystem;
import org.tzi.use.dtdl.runtime.telemetry.adapters.PollingHttpAdapter;
import org.tzi.use.dtdl.runtime.telemetry.adapters.SimulatorAdapter;

import java.net.URI;

/**
 * Call PluginBootstrap.init(...) from your plugin startup code once.
 * It will create and start TelemetryManager and register a simulator adapter + optional poller.
 */
public final class PluginBootstrap {
    private PluginBootstrap() {}

    public static TelemetryManager initWithDefaults() {
        DTDLSystem system = DTDLPluginState.system();
        DeadLetterSink dead = new DeadLetterSink("telemetry-deadletter.log");

        // sensible defaults for tests: small queue, 2 workers, 3 retries
        TelemetryManager mgr = new TelemetryManager(system, 1024, 2, 3, 200, dead);

        // register a small simulator adapter (for local testing)
        SimulatorAdapter sim = new SimulatorAdapter("sim-room-env", "dtmi:com:example:Room;1", "environmentState", null, 5);
        mgr.registerAdapter(sim);

        // example: add a remote poller if you want (comment if not used)
        // mgr.addPollingHttpAdapter("weather", URI.create("https://api.example.com/weather"), 30);

        // start manager (starts adapters too)
        mgr.start();

        // store manager in plugin state for later retrieval (optional)
        DTDLPluginState.setTelemetryManager(mgr);

        return mgr;
    }
}