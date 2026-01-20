package org.tzi.use.dtdl.gui;

import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Property.Property;
import org.tzi.use.dtdl.DTDLModel.Component.Component;
import org.tzi.use.dtdl.DTDLModel.Schema.PrimitiveType;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue;
import org.tzi.use.dtdl.runtime.DTDLInstance;
import org.tzi.use.dtdl.runtime.DTDLSystem;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.main.Session;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.gui.util.CloseOnEscapeKeyListener;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

public class CreateInstanceDialog extends JDialog {
    private final Session session;
    private final MainWindow parent;
    private final JComboBox<String> ifaceCombo;
    private final JTextField nameField;
    private final JPanel dynamicPanel;
    private final JButton createBtn;
    private final JLabel statusLabel;
    private final Map<String, String> comboToId = new LinkedHashMap<>();
    private final Map<String, JComboBox<String>> compInputs = new LinkedHashMap<>();
    private final Map<String, JComboBox<String>> relInputs = new LinkedHashMap<>();

    // top-level simple property inputs (primitive / enum / map / array text fields)
    private final Map<String, JComponent> propInputs = new LinkedHashMap<>();
    // nested object property inputs: propertyName -> (fieldName -> component)
    private final Map<String, Map<String, JComponent>> nestedPropInputs = new LinkedHashMap<>();

    public CreateInstanceDialog(Session session, MainWindow parent) {
        super(parent, "Create DTDL Instance", true);
        this.session = session;
        this.parent = parent;
        setLayout(new BorderLayout());
        setResizable(true);

        ifaceCombo = new JComboBox<>();
        nameField = new JTextField(20);
        dynamicPanel = new JPanel(new GridBagLayout());
        createBtn = new JButton("Create & Start");
        JButton cancelBtn = new JButton("Cancel");
        statusLabel = new JLabel(" ");

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Interface:"));
        top.add(ifaceCombo);
        top.add(new JLabel("Instance name (optional):"));
        top.add(nameField);
        add(top, BorderLayout.NORTH);

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setBorder(BorderFactory.createTitledBorder("Initial values"));
        centerWrap.add(new JScrollPane(dynamicPanel), BorderLayout.CENTER);
        add(centerWrap, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        JPanel btns = new JPanel();
        btns.add(createBtn);
        btns.add(cancelBtn);
        south.add(btns, BorderLayout.NORTH);
        south.add(statusLabel, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);

        populateInterfaces();

        ifaceCombo.addActionListener(e -> rebuildFormForSelectedInterface());
        createBtn.addActionListener(this::onCreate);
        cancelBtn.addActionListener(e -> { setVisible(false); dispose(); });

        pack();
        setLocationRelativeTo(parent);
        addKeyListener(new CloseOnEscapeKeyListener(this));
    }

    private void populateInterfaces() {
        comboToId.clear();
        ifaceCombo.removeAllItems();
        DTDLModelRegistry registry = DTDLPluginState.registry();
        if (registry == null) {
            ifaceCombo.addItem("(no registry)");
            ifaceCombo.setEnabled(false);
            createBtn.setEnabled(false);
            return;
        }
        org.tzi.use.dtdl.DTDLModel.DTDLModel canonical = registry.getCanonicalModel();
        if (canonical == null || canonical.getInterfaces().isEmpty()) {
            ifaceCombo.addItem("(no registered interfaces)");
            ifaceCombo.setEnabled(false);
            createBtn.setEnabled(false);
            return;
        }
        ifaceCombo.setEnabled(true);
        createBtn.setEnabled(true);
        for (Interface iface : canonical.getInterfaces().values()) {
            String label = iface.getDisplayName() != null ? iface.getDisplayName() : iface.getId();
            String show = label + " (" + iface.getId() + ")";
            comboToId.put(show, iface.getId());
            ifaceCombo.addItem(show);
        }
        if (ifaceCombo.getItemCount() > 0) {
            ifaceCombo.setSelectedIndex(0);
            rebuildFormForSelectedInterface();
        }
    }

    private void rebuildFormForSelectedInterface() {
        dynamicPanel.removeAll();
        propInputs.clear();
        nestedPropInputs.clear();
        compInputs.clear();


        Object sel = ifaceCombo.getSelectedItem();
        if (sel == null) {
            dynamicPanel.revalidate();
            dynamicPanel.repaint();
            pack();
            return;
        }
        String show = sel.toString();
        String ifaceId = comboToId.get(show);
        if (ifaceId == null) {
            dynamicPanel.revalidate();
            dynamicPanel.repaint();
            pack();
            return;
        }
        DTDLModelRegistry registry = DTDLPluginState.registry();
        Interface iface = registry.getCanonicalModel().getInterface(ifaceId);
        if (iface == null) {
            dynamicPanel.revalidate();
            dynamicPanel.repaint();
            pack();
            return;
        }
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 0;
        int row = 0;

        List<org.tzi.use.dtdl.DTDLModel.ContentElement> contents = iface.getContents();
        List<Property> props = contents.stream().filter(c -> c instanceof Property).map(c -> (Property)c).collect(Collectors.toList());
        List<Component> comps = contents.stream().filter(c -> c instanceof Component).map(c -> (Component)c).collect(Collectors.toList());

        // Properties initial values
        if (!props.isEmpty()) {
            JLabel ph = new JLabel("Properties:");
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1.0;
            dynamicPanel.add(ph, gbc);
            row++;
            for (Property p : props) {
                gbc.gridwidth = 1;
                gbc.weightx = 0;
                gbc.gridx = 0; gbc.gridy = row;
                JLabel lbl = new JLabel(p.getName() + ":");
                dynamicPanel.add(lbl, gbc);

                gbc.gridx = 1; gbc.weightx = 1.0;
                JComponent input = createInputForProperty(p, iface);

                // only register top-level simple inputs in propInputs; object panels are kept only in nestedPropInputs
                if (!nestedPropInputs.containsKey(p.getName())) {
                    propInputs.put(p.getName(), input);
                }

                dynamicPanel.add(input, gbc);
                row++;
            }
        }

        // Select component with existing interfaces
        if (!comps.isEmpty()) {
            JLabel ch = new JLabel("Components (select existing instance or leave none):");
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1.0;
            dynamicPanel.add(ch, gbc);
            row++;
            for (Component c : comps) {
                gbc.gridwidth = 1;
                gbc.weightx = 0;
                gbc.gridx = 0; gbc.gridy = row;
                JLabel lbl = new JLabel(c.getName() + ":");
                dynamicPanel.add(lbl, gbc);

                gbc.gridx = 1; gbc.weightx = 1.0;
                JComboBox<String> combo = new JComboBox<>();
                combo.addItem("(none)");

                // Resolve target interface id (may be null if conversion didn't set schemaInterface)
                String targetIfaceId;
                if (c.getSchemaInterface() != null) {
                    targetIfaceId = c.getSchemaInterface().getId();
                } else {
                    targetIfaceId = null;
                }

                if (targetIfaceId == null) {
                    // Schema unresolved: show hint so user (and developer) know why combo is empty
                    combo.addItem("(schema unresolved)");
                    combo.setToolTipText("Component schema unresolved; fix model conversion to enable selecting instances.");
                } else {
                    DTDLSystem sys = DTDLPluginState.system();
                    boolean foundAny = false;
                    if (sys != null) {
                        List<DTDLInstance> instances = sys.listInstances().stream()
                                .filter(i -> i.getInterface() != null && targetIfaceId.equals(i.getInterface().getId()))
                                .collect(Collectors.toList());
                        for (DTDLInstance inst : instances) {
                            combo.addItem(inst.getName() + " [" + inst.getId() + "]");
                            foundAny = true;
                        }
                    }
                    if (!foundAny) {
                        combo.addItem("(no matching instances)");
                        combo.setToolTipText("No existing instances for interface " + targetIfaceId);
                    }
                }

                compInputs.put(c.getName(), combo);
                dynamicPanel.add(combo, gbc);
                row++;
            }
        }

        List<org.tzi.use.dtdl.DTDLModel.Relationship.Relationship> rels = iface.getContents().stream()
                .filter(c -> c.getClass().getSimpleName().equals("Relationship"))
                .map(c -> (org.tzi.use.dtdl.DTDLModel.Relationship.Relationship)c)
                .collect(Collectors.toList());

        if (!rels.isEmpty()) {
            JLabel rh = new JLabel("Relationships (link to existing instance or leave none):");
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1.0;
            dynamicPanel.add(rh, gbc);
            row++;
            for (var r : rels) {
                gbc.gridwidth = 1;
                gbc.weightx = 0;
                gbc.gridx = 0; gbc.gridy = row;
                JLabel lbl = new JLabel(r.getName() + ":");
                dynamicPanel.add(lbl, gbc);

                gbc.gridx = 1; gbc.weightx = 1.0;
                JComboBox<String> combo = new JComboBox<>();
                combo.addItem("(none)");
                String targetIfaceId = (r.getTarget() != null ? (r.getTarget() instanceof Interface ? ((Interface) r.getTarget()).getId() : r.getTarget().toString()) : null);

                if (targetIfaceId == null) {
                    combo.addItem("(target unresolved)");
                } else {
                    DTDLSystem sys = DTDLPluginState.system();
                    boolean foundAny = false;
                    if (sys != null) {
                        for (DTDLInstance inst : sys.listInstances()) {
                            if (inst.getInterface() != null && targetIfaceId.equals(inst.getInterface().getId())) {
                                combo.addItem(inst.getName() + " [" + inst.getId() + "]");
                                foundAny = true;
                            }
                        }
                    }
                    if (!foundAny) combo.addItem("(no matching instances)");
                }

                relInputs.put(r.getName(), combo);
                dynamicPanel.add(combo, gbc);
                row++;
            }
        }

        dynamicPanel.revalidate();
        dynamicPanel.repaint();
        pack();
    }

    private JComponent createInputForProperty(Property p, Interface iface) {
        org.tzi.use.dtdl.DTDLModel.Schema.Schema s = resolveNamedSchema(p.getSchema(), iface);
        if (s instanceof PrimitiveType pt) {
            String t = pt.getTypeName();
            switch (t) {
                case "boolean" -> { JCheckBox cb = new JCheckBox(); return cb; }
                case "integer" -> {
                    NumberFormatter fmt = new NumberFormatter(NumberFormat.getIntegerInstance());
                    fmt.setValueClass(Integer.class);
                    fmt.setAllowsInvalid(true);
                    JFormattedTextField tf = new JFormattedTextField(fmt);
                    tf.setColumns(10);
                    return tf;
                }
                case "long" -> {
                    NumberFormatter fmt = new NumberFormatter(NumberFormat.getIntegerInstance());
                    fmt.setValueClass(Long.class);
                    fmt.setAllowsInvalid(true);
                    JFormattedTextField tf = new JFormattedTextField(fmt);
                    tf.setColumns(12);
                    return tf;
                }
                case "double", "float" -> {
                    NumberFormatter fmt = new NumberFormatter(new DecimalFormat());
                    fmt.setValueClass(Double.class);
                    fmt.setAllowsInvalid(true);
                    JFormattedTextField tf = new JFormattedTextField(fmt);
                    tf.setColumns(12);
                    return tf;
                }
                default -> {
                    JTextField tf = new JTextField(20);
                    return tf;
                }
            }
        }
        else if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object obj) {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createEtchedBorder());

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2,2,2,2);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;

            int r = 0;
            Map<String, JComponent> fieldInputs = new LinkedHashMap<>();
            for (org.tzi.use.dtdl.DTDLModel.Schema.Object.Field f : obj.getFields()) {
                gbc.gridx = 0;
                gbc.gridy = r;
                gbc.weightx = 0;
                panel.add(new JLabel(f.getName() + ":"), gbc);

                gbc.gridx = 1;
                gbc.weightx = 1.0;
                JComponent fieldInput = createInputForSchema(f.getSchema(), iface);
                panel.add(fieldInput, gbc);

                // store nested input in dedicated map
                fieldInputs.put(f.getName(), fieldInput);
                r++;
            }
            nestedPropInputs.put(p.getName(), fieldInputs);
            return panel;
        } else if (s instanceof Enum e) {
            JComboBox<String> cb = new JComboBox<>();
            cb.addItem("(none)");
            for (EnumValue ev : e.getValues()) {
                if (ev != null) cb.addItem(ev.getName());
            }
            return cb;
        } else {
            JTextField tf = new JTextField(30);
            return tf;
        }
    }

    private org.tzi.use.dtdl.DTDLModel.Schema.Schema resolveNamedSchema(org.tzi.use.dtdl.DTDLModel.Schema.Schema s, Interface iface) {
        if (s == null) return null;
        // NamedSchema class in your model:
        if (s.getClass().getSimpleName().equals("NamedSchema")) {
            try {
                // cast by class name to avoid import craziness if you prefer:
                var ns = (org.tzi.use.dtdl.DTDLModel.NamedSchema) s;
                String nm = ns.getName();
                if (nm != null) {
                    var resolved = iface.getSchemas().get(nm);
                    if (resolved != null) return resolved;
                }
            } catch (ClassCastException ignored) {}
        }
        return s;
    }

    private JComponent createInputForSchema(org.tzi.use.dtdl.DTDLModel.Schema.Schema s, Interface iface) {
        s = resolveNamedSchema(s, iface);
        if (s instanceof PrimitiveType pt) {
            return createPrimitiveInput(pt);
        }
        if (s instanceof Enum e) {
            JComboBox<String> cb = new JComboBox<>();
            cb.addItem("(none)");
            for (EnumValue ev : e.getValues()) cb.addItem(ev.getName());
            return cb;
        }
        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object obj) {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2,2,2,2);
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            int r = 0;
            for (var f : obj.getFields()) {
                gbc.gridx = 0; gbc.gridy = r; gbc.weightx = 0;
                panel.add(new JLabel(f.getName()), gbc);
                gbc.gridx = 1; gbc.weightx = 1.0;
                panel.add(createInputForSchema(f.getSchema(), iface), gbc);
                r++;
            }
            return panel;
        }
        return new JTextField(20); // fallback (temporary)
    }

    private void onCreate(ActionEvent ev) {
        Object sel = ifaceCombo.getSelectedItem();
        if (sel == null) { JOptionPane.showMessageDialog(this, "No interface selected.", "Error", JOptionPane.ERROR_MESSAGE); return; }
        String show = sel.toString();
        String ifaceId = comboToId.get(show);
        if (ifaceId == null) { JOptionPane.showMessageDialog(this, "Invalid selection.", "Error", JOptionPane.ERROR_MESSAGE); return; }
        DTDLModelRegistry registry = DTDLPluginState.registry();
        Interface iface = registry.getCanonicalModel().getInterface(ifaceId);
        if (iface == null) { JOptionPane.showMessageDialog(this, "Selected interface not available.", "Error", JOptionPane.ERROR_MESSAGE); return; }

        String name = nameField.getText();
        if (name != null && name.trim().isEmpty()) name = null;

        Map<String,Object> props = new LinkedHashMap<>();
        for (ContentElement ce : iface.getContents()) {
            if (!(ce instanceof Property p)) continue;
            String key = p.getName();
            JComponent topComp = propInputs.get(key);
            Object raw;
            if (topComp != null) {
                raw = extractValueFromInput(topComp);
            } else if (nestedPropInputs.containsKey(key)) {
                Map<String,JComponent> fields = nestedPropInputs.get(key);
                Map<String,Object> obj = new LinkedHashMap<>();
                for (Map.Entry<String,JComponent> fe : fields.entrySet()) {
                    String fname = fe.getKey();
                    JComponent fieldComp = fe.getValue();
                    Object fraw = extractValueFromInput(fieldComp);
                    if (fraw != null) obj.put(fname, fraw);
                }
                raw = obj.isEmpty() ? null : obj;
            } else {
                raw = null; // nothing provided (optional)
            }

            org.tzi.use.dtdl.DTDLModel.Schema.Schema sch = p.getSchema();
            sch = resolveNamedSchema(sch, iface);
            Object coerced = tryCoerceToSchema(raw, sch);
            if (raw != null && coerced == null) {
                JOptionPane.showMessageDialog(this, "Invalid value for property " + key, "Validation error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (coerced != null) props.put(key, coerced);
        }

        DTDLSystem system = DTDLPluginState.system();
        DTDLInstance inst = system.createInstance(iface, name, props);
        if (inst == null) { JOptionPane.showMessageDialog(this, "Failed to create instance.", "Error", JOptionPane.ERROR_MESSAGE); return; }

        for (Map.Entry<String, JComboBox<String>> en : compInputs.entrySet()) {
            String compName = en.getKey();
            JComboBox<String> combo = en.getValue();
            Object selItem = combo.getSelectedItem();
            if (selItem == null) continue;
            String s = selItem.toString();
            if ("(none)".equals(s)) continue;
            int idx = s.lastIndexOf('[');
            int idx2 = s.lastIndexOf(']');
            if (idx >= 0 && idx2 > idx) {
                String id = s.substring(idx+1, idx2);
                system.setComponent(inst.getId(), compName, id);
            }
        }

        for (Map.Entry<String, JComboBox<String>> en : relInputs.entrySet()) {
            String relName = en.getKey();
            JComboBox<String> combo = en.getValue();
            Object selItem = combo.getSelectedItem();
            if (selItem == null) continue;
            String s = selItem.toString();
            if ("(none)".equals(s) || "(no matching instances)".equals(s) || "(target unresolved)".equals(s)) continue;
            int idx = s.lastIndexOf('[');
            int idx2 = s.lastIndexOf(']');
            if (idx >= 0 && idx2 > idx) {
                String targetId = s.substring(idx+1, idx2);
                boolean ok = system.linkRelationship(inst.getId(), relName, targetId);
                if (!ok) {
                    // optionally warn or log; but keep it non-blocking for now
                    System.err.println("Failed to link relationship " + relName + " -> " + targetId);
                }
            }
        }

        boolean started = system.startInstance(inst.getId());
        statusLabel.setText("Created instance id=" + inst.getId() + (started ? " (started)" : " (stopped)"));

        inst.printToStdout();

        JOptionPane.showMessageDialog(this, "Instance created: id=" + inst.getId() + "\nInterface=" + iface.getId(), "Instance Created", JOptionPane.INFORMATION_MESSAGE);
    }

    private Object tryCoerceToSchema(Object v, org.tzi.use.dtdl.DTDLModel.Schema.Schema schema) {
        if (schema == null) return v;
        if (v == null) return null;
        if (schema instanceof PrimitiveType pt) {
            String t = pt.getTypeName();
            if (v instanceof String vs) {
                String s = vs.trim();
                switch (t) {
                    case "boolean": if ("true".equalsIgnoreCase(s)) return Boolean.TRUE; if ("false".equalsIgnoreCase(s)) return Boolean.FALSE; return null;
                    case "integer": try { return Integer.parseInt(s); } catch(Exception ex) { return null; }
                    case "long": try { return Long.parseLong(s); } catch(Exception ex) { return null; }
                    case "double": case "float": try { return Double.parseDouble(s); } catch(Exception ex) { return null; }
                    case "string": return s;
                    default: return s;
                }
            } else if (v instanceof Number n) {
                switch (t) {
                    case "integer": return n.intValue();
                    case "long": return n.longValue();
                    case "double": case "float": return n.doubleValue();
                    default: return v;
                }
            } else if (v instanceof Boolean && "boolean".equals(t)) return v;
            else return null;
        }
        if (schema instanceof Enum e) {
            if (v instanceof String vs) {
                for (EnumValue ev : e.getValues()) if (ev != null && ev.getName().equals(vs)) return vs;
            }
            return null;
        }
        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object) {
            if (v instanceof Map) return v;
            return null;
        }
        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Array.Array) {
            if (v instanceof List) return v;
            return null;
        }
        return v;
    }

    private JComponent createPrimitiveInput(PrimitiveType pt) {
        String t = pt.getTypeName();
        switch (t) {
            case "boolean": {
                return new JCheckBox();
            }
            case "integer": {
                NumberFormatter fmt = new NumberFormatter(NumberFormat.getIntegerInstance());
                fmt.setValueClass(Integer.class);
                fmt.setAllowsInvalid(true);
                JFormattedTextField tf = new JFormattedTextField(fmt);
                tf.setColumns(10);
                return tf;
            }
            case "long": {
                NumberFormatter fmt = new NumberFormatter(NumberFormat.getIntegerInstance());
                fmt.setValueClass(Long.class);
                fmt.setAllowsInvalid(true);
                JFormattedTextField tf = new JFormattedTextField(fmt);
                tf.setColumns(12);
                return tf;
            }
            case "double":
            case "float": {
                NumberFormatter fmt = new NumberFormatter(new DecimalFormat());
                fmt.setValueClass(Double.class);
                fmt.setAllowsInvalid(true);
                JFormattedTextField tf = new JFormattedTextField(fmt);
                tf.setColumns(12);
                return tf;
            }
            default: {
                JTextField tf = new JTextField(20);
                return tf;
            }
        }
    }

    private Object extractValueFromInput(JComponent comp) {
        if (comp instanceof JCheckBox cb) return cb.isSelected();
        if (comp instanceof JFormattedTextField ft) {
            Object v = ft.getValue();
            return v != null ? v : ft.getText();
        }
        if (comp instanceof JTextField tf) return tf.getText();
        if (comp instanceof JComboBox<?> combo) {
            Object sel = combo.getSelectedItem();
            if (sel == null || "(none)".equals(sel.toString())) return null;
            return sel.toString();
        }
        // panel with nested inputs -> not handled here (we store nested primitives separately)
        return null;
    }
}
