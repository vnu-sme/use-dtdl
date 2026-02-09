package org.tzi.use.dtdl.gui.instance;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

import org.tzi.use.dtdl.DTDLModel.Schema.Map.Map;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;
import org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType;

public class MapInputPanel extends JPanel implements ValueProvider {

    private static class Row {
        JCheckBox select;
        JComponent key;
        JComponent value;
        JPanel panel;
    }

    private final List<Row> rows = new ArrayList<>();
    private final JPanel rowsPanel = new JPanel();
    private final Map schema;
    private final Interface iface;
    private final SchemaInputAdapter adapter;

    public MapInputPanel(Map schema, Interface iface, SchemaInputAdapter adapter) {
        super(new BorderLayout());
        this.schema = schema;
        this.iface = iface;
        this.adapter = adapter;

        rowsPanel.setLayout(new BoxLayout(rowsPanel, BoxLayout.Y_AXIS));
        add(new JScrollPane(rowsPanel), BorderLayout.CENTER);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 3));
        JButton add = new JButton("Add entry");
        JButton remove = new JButton("Remove selected");
        buttons.add(add);
        buttons.add(remove);
        add(buttons, BorderLayout.SOUTH);

        add.addActionListener(e -> addRow());
        remove.addActionListener(e -> removeSelected());
    }

    private void addRow() {
        Row r = new Row();
        r.select = new JCheckBox();

        Schema keySchema = schema.getMapKey() != null ? schema.getMapKey().getSchema() : null;
        if (keySchema instanceof PrimitiveType p) {
            r.key = adapter.createInputForSchema(p, iface);
        } else {
            r.key = new JTextField(10);
        }

        Schema valSchema = schema.getMapValue() != null ? schema.getMapValue().getSchema() : null;
        r.value = adapter.createInputForSchema(valSchema, iface);

        r.panel = new JPanel(new GridLayout(1, 5, 4, 0));
        r.panel.add(r.select);
        r.panel.add(new JLabel("key"));
        r.panel.add(r.key);
        r.panel.add(new JLabel("value"));
        r.panel.add(r.value);

        rows.add(r);
        rowsPanel.add(r.panel);
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    private void removeSelected() {
        for (int i = rows.size() - 1; i >= 0; i--) {
            if (rows.get(i).select.isSelected()) {
                rowsPanel.remove(rows.get(i).panel);
                rows.remove(i);
            }
        }
        rowsPanel.revalidate();
        rowsPanel.repaint();
    }

    public Object getValues() {
        java.util.Map<Object, Object> out = new LinkedHashMap<>();
        for (Row r : rows) {
            Object rawK = adapter.extractValueFromInput(r.key);
            Object rawV = adapter.extractValueFromInput(r.value);

            // propagate inner-panel invalid
            if (rawK == InputValidation.INVALID || rawV == InputValidation.INVALID) return InputValidation.INVALID;

            // keep raw values (authoritative coercion later)
            if (rawK != null && rawV != null) {
                out.put(rawK, rawV);
            }
        }
        return out.isEmpty() ? null : out;
    }

    @Override
    public Object getValue() {
        return getValues();
    }
}
