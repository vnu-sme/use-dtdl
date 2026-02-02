package org.tzi.use.dtdl.gui.instance;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

import org.tzi.use.dtdl.DTDLModel.Schema.Schema;
import org.tzi.use.dtdl.DTDLModel.Interface;

public class ArrayInputPanel extends JPanel implements ValueProvider {

    private final List<JComponent> itemInputs = new ArrayList<>();
    private final JPanel itemsPanel = new JPanel();
    private final Schema elementSchema;
    private final Interface iface;
    private final SchemaInputAdapter adapter;

    public ArrayInputPanel(Schema elementSchema, Interface iface, SchemaInputAdapter adapter) {
        super(new BorderLayout());
        this.elementSchema = elementSchema;
        this.iface = iface;
        this.adapter = adapter;

        itemsPanel.setLayout(new BoxLayout(itemsPanel, BoxLayout.Y_AXIS));
        JScrollPane sp = new JScrollPane(itemsPanel);
        sp.setPreferredSize(new Dimension(350, 120));
        add(sp, BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JButton add = new JButton("Add item");
        JButton remove = new JButton("Remove selected");
        buttons.add(add);
        buttons.add(remove);
        add(buttons, BorderLayout.SOUTH);

        add.addActionListener(e -> addItem());
        remove.addActionListener(e -> removeSelected());
    }

    private void addItem() {
        JPanel row = new JPanel(new BorderLayout());

        JComponent input = adapter.createInputForSchema(elementSchema, iface);
        JCheckBox select = new JCheckBox();

        row.add(select, BorderLayout.WEST);
        row.add(input, BorderLayout.CENTER);

        itemsPanel.add(row);
        itemInputs.add(input);

        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    private void removeSelected() {
        Component[] rows = itemsPanel.getComponents();
        for (int i = rows.length - 1; i >= 0; i--) {
            JPanel row = (JPanel) rows[i];
            JCheckBox cb = (JCheckBox) row.getComponent(0);
            if (cb.isSelected()) {
                itemInputs.remove(row.getComponent(1));
                itemsPanel.remove(i);
            }
        }
        itemsPanel.revalidate();
        itemsPanel.repaint();
    }

    public Object getValues() {
        List<Object> out = new ArrayList<>();
        for (JComponent c : itemInputs) {
            Object raw = adapter.extractValueFromInput(c);
            // If inner input panel signals INVALID, propagate
            if (raw == InputValidation.INVALID) return InputValidation.INVALID;
            if (raw != null) out.add(raw);
        }
        return out.isEmpty() ? null : out;
    }


    @Override
    public Object getValue() {
        return getValues();
    }
}
