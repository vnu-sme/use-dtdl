package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.gui.instance.CreateInstanceDialog;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MObjectState;
import org.tzi.use.uml.sys.MSystemState;

public class CreateInstanceAction implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        Session session = pluginAction.getSession();
        MainWindow parent = pluginAction.getParent();

        // show dialog (modal)
        CreateInstanceDialog dlg = new CreateInstanceDialog(session, parent);
        dlg.setVisible(true);

//        MSystemState state = session.system().state();
//
//        MObject obj = state.objectByName("c1");
//        MObjectState os = obj.state(state);
//
//        System.err.println("==== OBJECT STATE DUMP ====");
//        os.attributeValueMap().forEach((attr, value) -> {
//            System.err.println(
//                    attr.name()
//                            + " : "
//                            + attr.type()
//                            + " = "
//                            + value
//                            + "  [" + value.getClass().getSimpleName() + "]"
//            );
//        });
//        System.err.println("===========================");
    }
}
