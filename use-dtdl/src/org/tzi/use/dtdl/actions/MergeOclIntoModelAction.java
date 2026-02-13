package org.tzi.use.dtdl.actions;

import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.dtdl.gui.ocl.MergeOclDialog;

import javax.swing.*;

public class MergeOclIntoModelAction implements IPluginActionDelegate {

    @Override
    public void performAction(IPluginAction pluginAction) {
        Session session = pluginAction.getSession();
        MainWindow parent = pluginAction.getParent();

        // sanity
        if (session == null) return;
        // UI must run on EDT
        SwingUtilities.invokeLater(() -> {
            MergeOclDialog dlg = new MergeOclDialog(parent, session);
            dlg.setVisible(true);
        });
    }

    @Override
    public boolean shouldBeEnabled(IPluginAction action) {
        try {
            // enabled only if there is an active system/model
            return action.getSession() != null && action.getSession().hasSystem();
        } catch (Throwable t) {
            return false;
        }
    }
}
