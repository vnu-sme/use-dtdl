package org.tzi.use.dtdl.actions;

import org.tzi.use.api.UseApiException;
import org.tzi.use.dtdl.gui.DTDLForm;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.api.UseModelApi;

public class OpenExamplePlugin implements IPluginActionDelegate {
    @Override
    public void performAction(IPluginAction pluginAction) {
        Session session = pluginAction.getSession();
        MainWindow mainWindow = pluginAction.getParent();

        // Mở form chọn file DTDL
        DTDLForm form = new DTDLForm(session, mainWindow, DTDLPluginState.registry());
        form.setVisible(true);
    }
}

//    @Override
//    public void performAction(IPluginAction pluginAction) {
//        Session session = pluginAction.getSession();
//        MainWindow mainWindow = pluginAction.getParent();
//
//        MModel mmodel = session.system().model();

//        DTDLModel dtdlModel = new DTDLModel(mmodel.name());
//        dtdlModel.createDTDLModel(mmodel);

//        DTDLForm form = new DTDLForm(session, mainWindow);
//        form.setVisible(true);

//        UseModelApi useModelApi = new UseModelApi(pluginAction.getSession().system().model());
//
//
//        try {
//            System.out.println("create new class and attribute");
//            useModelApi.createClass("Living", true);
//            useModelApi.createAttribute("Living", "Age", "String");
//        } catch (UseApiException e) {
//            e.printStackTrace();
//        }
//
//        mainWindow.getModelBrowser().setModel(session.system().model());


//    }
