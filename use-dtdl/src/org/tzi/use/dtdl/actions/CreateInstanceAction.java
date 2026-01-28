package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.gui.instance.CreateInstanceDialog;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;

public class CreateInstanceAction implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        Session session = pluginAction.getSession();
        MainWindow parent = pluginAction.getParent();

        // show dialog (modal)
        CreateInstanceDialog dlg = new CreateInstanceDialog(session, parent);
        dlg.setVisible(true);
    }
}
