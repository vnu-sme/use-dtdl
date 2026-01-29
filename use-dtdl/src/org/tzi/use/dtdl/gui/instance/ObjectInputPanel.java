package org.tzi.use.dtdl.gui.instance;

import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Schema.Object.Field;
import org.tzi.use.dtdl.DTDLModel.Schema.Schema;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedHashMap;

import java.util.Map;

public final class ObjectInputPanel extends JPanel implements ValueProvider {

    private final Map<String, Schema> fieldSchemas = new LinkedHashMap<>();


    private final Map<String, JComponent> fields = new LinkedHashMap<>();
    private final SchemaInputAdapter adapter;

    public ObjectInputPanel(
            java.util.List<Field> schemaFields,
            Interface iface,
            SchemaInputAdapter adapter
    ) {
        super(new GridBagLayout());
        this.adapter = adapter;

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(2,2,2,2);
        gbc.fill = GridBagConstraints.HORIZONTAL;

        int r = 0;
        for (Field f : schemaFields) {
            gbc.gridx = 0; gbc.gridy = r;
            add(new JLabel(f.getName()), gbc);

            gbc.gridx = 1;
            JComponent input = adapter.createInputForSchema(f.getSchema(), iface);
            fields.put(f.getName(), input);
            fieldSchemas.put(f.getName(), f.getSchema());
            add(input, gbc);

            r++;
        }
    }

    @Override
    public Object getValue() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (var e : fields.entrySet()) {
            Schema sch = fieldSchemas.get(e.getKey());
            Object raw = adapter.extractValueFromInput(e.getValue());
            Object coerced = SchemaInputFactory.tryCoerceToSchema(raw, sch);
            if (raw != null && coerced == null) return InputValidation.INVALID;
            if (coerced != null) out.put(e.getKey(), coerced);
        }
        return out.isEmpty() ? null : out;
    }
}
