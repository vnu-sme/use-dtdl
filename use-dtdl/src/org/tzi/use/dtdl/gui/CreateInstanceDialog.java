package org.tzi.use.dtdl.gui;

import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Schema.*;
import org.tzi.use.dtdl.DTDLModel.Schema.Array.Array;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue;
import org.tzi.use.dtdl.DTDLModel.Schema.Object.Field;
import org.tzi.use.dtdl.DTDLModel.Property.Property;
import org.tzi.use.dtdl.DTDLModel.Relationship.Relationship;
import org.tzi.use.dtdl.DTDLModel.Component.Component;
import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.semantic.SemanticAnalyzerImpl;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.gui.util.CloseOnEscapeKeyListener;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.*;
import org.tzi.use.uml.sys.MInstance;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.ocl.value.*;
import org.tzi.use.uml.ocl.type.EnumType;

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
    private final JButton cancelBtn;
    private final JLabel statusLabel;

    private final Map<String, String> comboToId = new LinkedHashMap<>();
    private final Map<String, JComponent> propInputs = new LinkedHashMap<>();
    private final Map<String, Map<String, JComponent>> nestedPropInputs = new LinkedHashMap<>();
    private final Map<String, JComboBox<String>> compInputs = new LinkedHashMap<>();
    private final Map<String, JComboBox<String>> relInputs = new LinkedHashMap<>();

    public CreateInstanceDialog(Session session, MainWindow parent) {
        super(parent, "Create USE Object", true);
        this.session = session;
        this.parent = parent;
        setLayout(new BorderLayout());
        setResizable(true);

        ifaceCombo = new JComboBox<>();
        nameField = new JTextField(20);
        dynamicPanel = new JPanel(new GridBagLayout());
        createBtn = new JButton("Create");
        cancelBtn = new JButton("Cancel");
        statusLabel = new JLabel(" ");

        JPanel top = new JPanel(new FlowLayout(FlowLayout.LEFT));
        top.add(new JLabel("Interface:"));
        top.add(ifaceCombo);
        top.add(new JLabel("Object name (optional):"));
        top.add(nameField);
        add(top, BorderLayout.NORTH);

        JPanel centerWrap = new JPanel(new BorderLayout());
        centerWrap.setBorder(BorderFactory.createTitledBorder("Initial attribute values / links"));
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
        DTDLModel canonical = registry.getCanonicalModel();
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
        relInputs.clear();

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
        org.tzi.use.dtdl.DTDLModel.Interface iface = registry.getCanonicalModel().getInterface(ifaceId);
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
        List<Relationship> rels = contents.stream()
                .filter(c -> c.getClass().getSimpleName().equals("Relationship"))
                .map(c -> (Relationship)c)
                .collect(Collectors.toList());

        if (!props.isEmpty()) {
            JLabel ph = new JLabel("Attributes:");
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
                if (!nestedPropInputs.containsKey(p.getName())) {
                    propInputs.put(p.getName(), input);
                }
                dynamicPanel.add(input, gbc);
                row++;
            }
        }

        if (!comps.isEmpty()) {
            JLabel ch = new JLabel("Components (select existing USE object or leave none):");
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
                String targetIfaceId = c.getSchemaInterface() != null ? c.getSchemaInterface().getId() : null;

                List<String> useObjects = listUseObjectsForDtInterface(targetIfaceId);
                if (useObjects.isEmpty()) {
                    combo.addItem("(no matching USE objects)");
                } else {
                    for (String o : useObjects) combo.addItem(o);
                }

                compInputs.put(c.getName(), combo);
                dynamicPanel.add(combo, gbc);
                row++;
            }
        }

        if (!rels.isEmpty()) {
            JLabel rh = new JLabel("Relationships (link to existing USE object or leave none):");
            gbc.gridx = 0; gbc.gridy = row; gbc.gridwidth = 2; gbc.weightx = 1.0;
            dynamicPanel.add(rh, gbc);
            row++;
            for (Relationship r : rels) {
                gbc.gridwidth = 1;
                gbc.weightx = 0;
                gbc.gridx = 0; gbc.gridy = row;
                JLabel lbl = new JLabel(r.getName() + ":");
                dynamicPanel.add(lbl, gbc);

                gbc.gridx = 1; gbc.weightx = 1.0;
                JComboBox<String> combo = new JComboBox<>();
                combo.addItem("(none)");
                String targetIfaceId = (r.getTarget() != null ? (r.getTarget() instanceof org.tzi.use.dtdl.DTDLModel.Interface ? ((org.tzi.use.dtdl.DTDLModel.Interface) r.getTarget()).getId() : r.getTarget().toString()) : null);

                List<String> useObjects = listUseObjectsForDtInterface(targetIfaceId);
                if (useObjects.isEmpty()) {
                    combo.addItem("(no matching USE objects)");
                } else {
                    for (String o : useObjects) combo.addItem(o);
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

    private List<String> listUseObjectsForDtInterface(String targetIfaceId) {
        if (targetIfaceId == null) return Collections.emptyList();
        DTDLModelRegistry registry = DTDLPluginState.registry();
        Optional<String> mapped = registry != null ? registry.getClassNameForDtmi(targetIfaceId) : Optional.empty();
        String targetClass = mapped.orElseGet(() -> sanitize(targetIfaceId));
        if (session == null || session.system() == null) return Collections.emptyList();
        Set<MObject> all = session.system().state().allObjects();
        List<String> names = new ArrayList<>();
        for (MObject o : all) {
            MClass cls = o.cls();
            if (cls != null && Objects.equals(cls.name(), targetClass)) {
                names.add(o.name());
            }
        }
        return names;
    }

    private JComponent createInputForProperty(Property p, org.tzi.use.dtdl.DTDLModel.Interface iface) {
        org.tzi.use.dtdl.DTDLModel.Schema.Schema s = resolveNamedSchema(p.getSchema(), iface);
        if (s instanceof PrimitiveType pt) {
            return createPrimitiveInput(pt);
        }

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object obj) {
            JPanel panel = new JPanel(new GridBagLayout());
            panel.setBorder(BorderFactory.createEtchedBorder());
            GridBagConstraints gbc = new GridBagConstraints();
            gbc.insets = new Insets(2,2,2,2);
            gbc.anchor = GridBagConstraints.WEST;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weightx = 1.0;
            int r = 0;

            Map<String, JComponent> fieldInputs = new LinkedHashMap<>();
            for (Field f : obj.getFields()) {
                gbc.gridx = 0;
                gbc.gridy = r;
                gbc.weightx = 0;
                panel.add(new JLabel(f.getName() + ":"), gbc);
                gbc.gridx = 1;
                gbc.weightx = 1.0;
                JComponent fieldInput = createInputForSchema(f.getSchema(), iface);
                panel.add(fieldInput, gbc);
                fieldInputs.put(f.getName(), fieldInput);
                r++;
            }

            nestedPropInputs.put(p.getName(), fieldInputs);
            return panel;
        }

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum e) {
            JComboBox<String> cb = new JComboBox<>();
            cb.addItem("(none)");
            for (EnumValue ev : e.getValues()) {
                if (ev != null) cb.addItem(ev.getName());
            }
            return cb;
        }

        return new JTextField(20);
    }

    private org.tzi.use.dtdl.DTDLModel.Schema.Schema resolveNamedSchema(org.tzi.use.dtdl.DTDLModel.Schema.Schema s, org.tzi.use.dtdl.DTDLModel.Interface iface) {
        if (s == null) return null;

        if (s.getClass().getSimpleName().equals("NamedSchema")) {
            try {
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

    private JComponent createInputForSchema(org.tzi.use.dtdl.DTDLModel.Schema.Schema s, org.tzi.use.dtdl.DTDLModel.Interface iface) {
        s = resolveNamedSchema(s, iface);

        if (s instanceof PrimitiveType pt) return createPrimitiveInput(pt);

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum e) {
            JComboBox<String> cb = new JComboBox<>();
            cb.addItem("(none)");
            for (EnumValue ev : e.getValues()) if (ev != null) cb.addItem(ev.getName());
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
            for (Field f : obj.getFields()) {
                gbc.gridx = 0; gbc.gridy = r; gbc.weightx = 0;
                panel.add(new JLabel(f.getName()), gbc);
                gbc.gridx = 1; gbc.weightx = 1.0;
                panel.add(createInputForSchema(f.getSchema(), iface), gbc);
                r++;
            }

            return panel;
        }

        return new JTextField(20);
    }

    private JComponent createPrimitiveInput(PrimitiveType pt) {
        String t = pt.getTypeName();
        if (t == null) t = "string";
        switch (t) {
            case "boolean": return new JCheckBox();
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
            default: return new JTextField(20);
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
        return null;
    }

    private void onCreate(ActionEvent ev) {
        Object sel = ifaceCombo.getSelectedItem();
        if (sel == null) { JOptionPane.showMessageDialog(this, "No interface selected.", "Error", JOptionPane.ERROR_MESSAGE); return; }
        String show = sel.toString();
        String ifaceId = comboToId.get(show);
        if (ifaceId == null) { JOptionPane.showMessageDialog(this, "Invalid selection.", "Error", JOptionPane.ERROR_MESSAGE); return; }
        DTDLModelRegistry registry = DTDLPluginState.registry();
        org.tzi.use.dtdl.DTDLModel.Interface iface = registry.getCanonicalModel().getInterface(ifaceId);
        if (iface == null) { JOptionPane.showMessageDialog(this, "Selected interface not available.", "Error", JOptionPane.ERROR_MESSAGE); return; }

        String name = nameField.getText();
        if (name != null) name = name.trim();
        if (name != null && name.isEmpty()) name = null;

        Map<String, Object> collected = new LinkedHashMap<>();
        for (Property p : iface.getContents().stream().filter(c -> c instanceof Property).map(c -> (Property)c).collect(Collectors.toList())) {
            String key = p.getName();
            JComponent top = propInputs.get(key);
            Object raw;

            if (top != null) {
                raw = extractValueFromInput(top);
            } else if (nestedPropInputs.containsKey(key)) {
                Map<String, JComponent> fields = nestedPropInputs.get(key);
                Map<String, Object> m = new LinkedHashMap<>();
                for (Map.Entry<String, JComponent> fe : fields.entrySet()) {
                    Object fv = extractValueFromInput(fe.getValue());
                    if (fv != null) m.put(fe.getKey(), fv);
                }
                raw = m.isEmpty() ? null : m;
            } else {
                raw = null;
            }

            org.tzi.use.dtdl.DTDLModel.Schema.Schema sch = resolveNamedSchema(p.getSchema(), iface);
            Object coerced = tryCoerceToSchema(raw, sch);
            if (raw != null && coerced == null) {
                JOptionPane.showMessageDialog(this, "Invalid value for property " + key, "Validation error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            if (coerced != null) collected.put(key, coerced);
        }

        try {
            ensureSystemExists();

            UseSystemApi sysApi = UseSystemApi.create(session);
            UseModelApi modelApi = new UseModelApi(session.system().model());

            Optional<String> mapped = registry.getClassNameForDtmi(ifaceId);
            String className = mapped.orElseGet(() -> sanitize(ifaceId));
            if (session.system().model().getClass(className) == null) {
                modelApi.createClass(className, false);
            }

            String objectName = name != null ? name : null;
            MObject obj = sysApi.createObject(className, objectName);
            String createdName = obj.name();

            for (Map.Entry<String, Object> en : collected.entrySet()) {
                String attr = en.getKey();
                Object val = en.getValue();
                MClass cls = session.system().model().getClass(obj.cls().name());
                MAttribute mAttr = cls != null ? cls.attribute(attr, true) : null;
                org.tzi.use.uml.ocl.type.Type attrType = mAttr != null ? mAttr.type() : null;

                System.err.println("[DEBUG] setting attribute:");
                System.err.println("  object      = " + createdName);
                System.err.println("  class       = " + (cls != null ? cls.name() : "<null>"));
                System.err.println("  attribute   = " + attr);
                System.err.println("  raw value   = " + val + " (" + (val != null ? val.getClass() : "null") + ")");
                System.err.println("  attr type   = " + attrType + " (" + (attrType != null ? attrType.getClass() : "null") + ")");

                String oclExpr = toOclLiteral(val, attrType);

                System.err.println("  ocl literal = " + oclExpr);

                Value value = null;
                value = buildUseValue(attrType, val);
                try {
                    sysApi.setAttributeValueEx(session.system().state().objectByName(createdName), mAttr, value);
                    System.err.println("  assignment OK via setAttributeValueEx");
                } catch (UseApiException assignEx) {
                    System.err.println("  assignment failed via setAttributeValueEx: " + assignEx.getMessage());
                }
            }

            // components: link by association (try find an association connecting classes and set link)
            for (Map.Entry<String, JComboBox<String>> en : compInputs.entrySet()) {
                String compName = en.getKey();
                JComboBox<String> combo = en.getValue();
                Object selItem = combo.getSelectedItem();
                if (selItem == null) continue;
                String s = selItem.toString();
                if ("(none)".equals(s) || "(no matching USE objects)".equals(s)) continue;
                String assoc = findAssociationBetweenClassesForRole(createdName, s, compName);
                if (assoc != null) {
                    try {
                        sysApi.createLink(assoc, new String[]{createdName, s});
                    } catch (UseApiException e) {
                        System.err.println("Failed to create component link " + assoc + ": " + e.getMessage());
                    }
                }
            }

            for (Map.Entry<String, JComboBox<String>> en : relInputs.entrySet()) {
                String relName = en.getKey();
                JComboBox<String> combo = en.getValue();
                Object selItem = combo.getSelectedItem();
                if (selItem == null) continue;
                String s = selItem.toString();
                if ("(none)".equals(s) || "(no matching USE objects)".equals(s)) continue;
                String assoc = findAssociationBetweenClassesForRole(createdName, s, relName);
                if (assoc != null) {
                    try {
                        sysApi.createLink(assoc, new String[]{createdName, s});
                    } catch (UseApiException e) {
                        System.err.println("Failed to create relationship link " + assoc + ": " + e.getMessage());
                    }
                }
            }

            session.system().ensureStateLinkSetsForModel();
            session.system().state().updateDerivedValues(true);

            statusLabel.setText("Created object: " + createdName);
            JOptionPane.showMessageDialog(this, "Created USE object: " + createdName, "Success", JOptionPane.INFORMATION_MESSAGE);
            setVisible(false);
            dispose();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Creation failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void ensureSystemExists() {
        boolean needCreate = false;
        try {
            if (session.system() == null) needCreate = true;
            else if (session.system().model() == null) needCreate = true;
        } catch (Throwable t) { needCreate = true; }
        if (needCreate) {
            String modelName = "unnamed";
            UseModelApi tmp = new UseModelApi(modelName);
            MModel m = tmp.getModel();
            MSystem s = new MSystem(m);
            session.setSystem(s);
            try {
                session.system().ensureStateLinkSetsForModel();
                session.system().state().updateDerivedValues(true);
            } catch (Throwable t) { /* ignore */ }
        }
    }

    public Value buildUseValue(org.tzi.use.uml.ocl.type.Type expectedType, Object raw) {
        if (raw == null) {
            return UndefinedValue.instance;
        }

        /* =========================
         * Primitive values
         * ========================= */
        if (raw instanceof Boolean) {
            return BooleanValue.get((Boolean) raw);
        }

        if (raw instanceof Integer) {
            return new IntegerValue((Integer) raw);
        }

        if (raw instanceof Long) {
            return new IntegerValue(((Long) raw).intValue());
        }

        if (raw instanceof Double || raw instanceof Float) {
            return new RealValue(((Number) raw).doubleValue());
        }

        /* =========================
         * Enum values
         * ========================= */
        if (expectedType instanceof EnumType && raw instanceof String) {
            return new org.tzi.use.uml.ocl.value.EnumValue((EnumType) expectedType, (String) raw);
        }

        /* =========================
         * Data type values
         * ========================= */
        if (expectedType instanceof MDataType && raw instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> rawValues = (Map<String, Object>) raw;

            Map<String, Value> values = new LinkedHashMap<>();

            MDataType dt = (MDataType) expectedType;
            for (MAttribute attr : dt.attributes()) {
                Object attrRaw = rawValues.get(attr.name());
                Value attrValue = buildUseValue(attr.type(), attrRaw);
                values.put(attr.name(), attrValue);
            }

            return new DataTypeValueValue(expectedType, null, values);
        }

        /* =========================
         * Collection values
         * ========================= */
        if (expectedType instanceof CollectionType && raw instanceof Collection) {
            CollectionType ct = (CollectionType) expectedType;
            org.tzi.use.uml.ocl.type.Type elemType = ct.elemType();

            Collection<Value> elements = new ArrayList<>();
            for (Object o : (Collection<?>) raw) {
                elements.add(buildUseValue(elemType, o));
            }

            if (ct instanceof SetType) {
                return new SetValue(elemType, elements);
            }

            if (ct instanceof SequenceType) {
                return new SequenceValue(elemType, elements);
            }

            if (ct instanceof BagType) {
                return new BagValue(elemType, elements);
            }

            if (ct instanceof OrderedSetType) {
                return new OrderedSetValue(elemType, elements);
            }
        }

        /* =========================
         * Object values
         * ========================= */
        if (expectedType instanceof MClassifier && raw instanceof MObject) {
            return new ObjectValue((MClassifier) expectedType, (MObject) raw);
        }

        throw new IllegalArgumentException(
                "Cannot build USE value for type=" + expectedType + ", raw=" + raw
        );
    }



    /**
     * Turn a runtime value into an OCL literal string appropriate for USE.
     * Uses expectedType to special-case enums.
     */
    private String toOclLiteral(Object v, org.tzi.use.uml.ocl.type.Type expectedType) {
        if (v == null) return "null";

        // Enum: render EnumTypeName::LITERAL (no quotes)
        try {
            if (expectedType instanceof EnumType et && v instanceof String) {
                String lit = ((String) v).trim();
                // best-effort: use enum type name (fallback to toString if missing)
                String enumTypeName;
                System.err.println("[DEBUG] enum type detected:");
                System.err.println("  enum name = " + et.name());
                System.err.println("  literals = " + et.getLiterals());
                try {
                    enumTypeName = et.name();
                } catch (Throwable t) {
                    enumTypeName = et.toString();
                }

                return enumTypeName + "::" + lit;
            }
        } catch (Throwable ignored) {
        }

        // Booleans
        if (v instanceof Boolean b) return b ? "true" : "false";

        // Numbers (integers, doubles, etc.)
        if (v instanceof Number n) return n.toString();

        // Strings -> single-quoted
        if (v instanceof String s) {
            String esc = s.replace("\\", "\\\\").replace("'", "\\'");
            return "'" + esc + "'";
        }

        // Maps/Lists or other complex types: we fallback to single-quoted string representation
        String esc = String.valueOf(v).replace("\\", "\\\\").replace("'", "\\'");
        return "'" + esc + "'";
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

        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum e) {
            if (v instanceof String vs) {
                for (EnumValue ev : e.getValues()) if (ev != null && ev.getName().equals(vs)) return vs;
            }
            return null;
        }

        if (schema instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object) {
            if (v instanceof Map) return v;
            return null;
        }

        if (schema instanceof Array) {
            if (v instanceof List) return v;
            return null;
        }

        return v;
    }

    private String findAssociationBetweenClassesForRole(String srcObjectName, String tgtObjectName, String roleName) {
        if (session == null || session.system() == null) return null;
        MModel mm = session.system().model();
        if (mm == null) return null;
        MObject src = session.system().state().objectByName(srcObjectName);
        MObject tgt = session.system().state().objectByName(tgtObjectName);
        if (src == null || tgt == null) return null;
        String clsA = src.cls() != null ? src.cls().name() : null;
        String clsB = tgt.cls() != null ? tgt.cls().name() : null;
        if (clsA == null || clsB == null) return null;
        for (MAssociation a : mm.associations()) {
            List<MAssociationEnd> ends = a.associationEnds();
            if (ends.size() != 2) continue;
            MAssociationEnd e1 = ends.get(0);
            MAssociationEnd e2 = ends.get(1);
            boolean match = Objects.equals(e1.cls().name(), clsA) && Objects.equals(e2.cls().name(), clsB)
                    && (roleName == null || Objects.equals(e1.name(), roleName) || Objects.equals(e2.name(), roleName));
            boolean reverse = Objects.equals(e1.cls().name(), clsB) && Objects.equals(e2.cls().name(), clsA)
                    && (roleName == null || Objects.equals(e1.name(), roleName) || Objects.equals(e2.name(), roleName));
            if (match || reverse) return a.name();
        }
        return null;
    }

    private String sanitize(String id) {
        if (id == null) return "Unnamed";
        String s = id.replaceAll("[^A-Za-z0-9_]", "_");
        if (s.isEmpty()) s = "Unnamed";
        if (Character.isDigit(s.charAt(0))) s = "_" + s;
        return s;
    }
}
