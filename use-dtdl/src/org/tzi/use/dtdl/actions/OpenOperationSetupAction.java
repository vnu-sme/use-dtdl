package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.gui.operations.OperationSetupDialog;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

public class OpenOperationSetupAction implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        Session session = pluginAction.getSession();
        MainWindow parent = pluginAction.getParent();
        DTDLPluginState.bindSession(session);
        OperationSetupDialog dlg = new OperationSetupDialog(parent, session);
        dlg.setVisible(true);
    }
}