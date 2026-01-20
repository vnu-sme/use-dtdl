package org.tzi.use.dtdl.views;

import java.awt.Dimension;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JPanel;

import org.tzi.use.dtdl.views.nodes.DTDLInterfaceNode;

@SuppressWarnings("serial")
public class DTDLDiagram extends JPanel {

    private final List<DTDLInterfaceNode> interfaceNodes = new ArrayList<>();

    public DTDLDiagram() {
        setLayout(null);
        setBackground(java.awt.Color.WHITE);
        setPreferredSize(new Dimension(800, 600));

        // HARD-CODED demo nodes
        interfaceNodes.add(new DTDLInterfaceNode("Classroom", 50, 50));
        interfaceNodes.add(new DTDLInterfaceNode("Thermostat", 250, 50));
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        for (DTDLInterfaceNode node : interfaceNodes) {
            node.draw(g);
        }
    }
}
