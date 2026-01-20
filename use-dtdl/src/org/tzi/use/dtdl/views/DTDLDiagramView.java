package org.tzi.use.dtdl.views;

import java.awt.BorderLayout;
import java.awt.Graphics2D;
import java.awt.print.PageFormat;

import javax.swing.JPanel;
import javax.swing.JScrollPane;

import org.tzi.use.gui.views.View;
import org.tzi.use.gui.views.PrintableView;

@SuppressWarnings("serial")
public class DTDLDiagramView extends JPanel
        implements View, PrintableView {

    private final DTDLDiagram diagram;

    public DTDLDiagramView() {
        setLayout(new BorderLayout());
        diagram = new DTDLDiagram();
        add(new JScrollPane(diagram), BorderLayout.CENTER);
    }

    @Override
    public void detachModel() {
        // nothing yet
    }

    @Override
    public void printView(PageFormat pf) {
//        diagram.printDiagram(pf, "DTDL Diagram");
    }

    @Override
    public void export(Graphics2D g) {
        diagram.paint(g);
    }

    @Override
    public float getContentWidth() {
        return diagram.getPreferredSize().width;
    }

    @Override
    public float getContentHeight() {
        return diagram.getPreferredSize().height;
    }
}
