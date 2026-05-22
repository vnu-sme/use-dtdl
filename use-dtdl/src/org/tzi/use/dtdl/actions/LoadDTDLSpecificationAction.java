package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.gui.DTDLForm;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

public class LoadDTDLSpecificationAction implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        Session session = pluginAction.getSession();
        MainWindow mainWindow = pluginAction.getParent();

        // Mở form chọn file DTDL
        DTDLForm form = new DTDLForm(session, mainWindow, DTDLPluginState.registry());
        form.setVisible(true);
    }

    @Override
    public boolean shouldBeEnabled(IPluginAction action) {
        return true; // enable even without .use model
    }
}
