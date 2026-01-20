package org.tzi.use.dtdl.actions;

import org.tzi.use.dtdl.views.DTDLDiagramView;
import org.tzi.use.gui.main.ViewFrame;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.runtime.gui.IPluginAction;
import org.tzi.use.runtime.gui.IPluginActionDelegate;

import javax.swing.*;
import java.awt.*;

public final class OpenDTDLDiagramAction implements IPluginActionDelegate {

    @Override
    public void performAction(IPluginAction action) {
        MainWindow mw = action.getParent();

        DTDLDiagramView view = new DTDLDiagramView();

        ViewFrame frame =
                new ViewFrame("DTDL Diagram", view, "DTDLDiagram.gif");

        JComponent content = (JComponent) frame.getContentPane();
        content.setLayout(new BorderLayout());
        content.add(view, BorderLayout.CENTER);

        mw.addNewViewFrame(frame);
    }
}
