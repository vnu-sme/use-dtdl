package org.tzi.use.dtdl.gui.instance;

import org.tzi.use.api.UseApiException;
import org.tzi.use.api.UseModelApi;
import org.tzi.use.api.UseSystemApi;
import org.tzi.use.dtdl.DTDLModel.*;
import org.tzi.use.dtdl.DTDLModel.Schema.*;
import org.tzi.use.dtdl.DTDLModel.Schema.Array.Array;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue;
import org.tzi.use.dtdl.DTDLModel.Schema.Object.Field;
import org.tzi.use.dtdl.DTDLModel.Property.Property;
import org.tzi.use.dtdl.DTDLModel.Relationship.Relationship;
import org.tzi.use.dtdl.DTDLModel.Component.Component;
import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.use.UseRuntimeService;
import org.tzi.use.dtdl.util.Utils;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.gui.util.CloseOnEscapeKeyListener;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.CollectionType;
import org.tzi.use.uml.sys.MObject;
import org.tzi.use.uml.ocl.value.*;

import javax.swing.*;
import javax.swing.text.NumberFormatter;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import static org.tzi.use.dtdl.gui.instance.SchemaInputFactory.resolveNamedSchema;
import static org.tzi.use.dtdl.util.SchemaUtils.coerceToSchemaRecursive;

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
    private final Map<String, JComboBox<String>> compInputs = new LinkedHashMap<>();
    private final Map<String, JComboBox<String>> relInputs = new LinkedHashMap<>();
    private final SchemaInputAdapter adapter = new SchemaInputAdapter() {
        @Override
        public JComponent createInputForSchema(Schema s, Interface iface) {
            return CreateInstanceDialog.this.createInputForSchema(s, iface);
        }

        @Override
        public Object extractValueFromInput(JComponent c) {
            return CreateInstanceDialog.this.extractValueFromInput(c);
        }
    };

    private final UseRuntimeService useService;

    public CreateInstanceDialog(Session session, MainWindow parent) {
        super(parent, "Create USE Object", true);
        this.session = session;
        this.parent = parent;
        this.useService = new UseRuntimeService(session);

        setLayout(new BorderLayout());
        setResizable(true);

        ifaceCombo = new JComboBox<>();
        nameField = new JTextField(20);
        dynamicPanel = new JPanel(new GridBagLayout());
        createBtn = new JButton("Create");
        cancelBtn = new JButton("Cancel");
        statusLabel = new JLabel(" ");
        statusLabel.setVerticalAlignment(SwingConstants.CENTER);


        JPanel top = new JPanel(new GridBagLayout());
        top.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        GridBagConstraints tbc = new GridBagConstraints();
        tbc.insets = new Insets(4, 6, 4, 6);
        tbc.anchor = GridBagConstraints.WEST;

        tbc.gridx = 0; tbc.gridy = 0;
        top.add(new JLabel("Interface"), tbc);

        tbc.gridx = 1; tbc.fill = GridBagConstraints.HORIZONTAL; tbc.weightx = 1.0;
        ifaceCombo.setPreferredSize(new Dimension(260, 26));
        top.add(ifaceCombo, tbc);

        tbc.gridx = 0; tbc.gridy = 1; tbc.weightx = 0; tbc.fill = GridBagConstraints.NONE;
        top.add(new JLabel("Object name"), tbc);

        tbc.gridx = 1; tbc.fill = GridBagConstraints.HORIZONTAL; tbc.weightx = 1.0;
        nameField.setPreferredSize(new Dimension(260, 26));
        top.add(nameField, tbc);

        add(top, BorderLayout.NORTH);

        dynamicPanel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        JScrollPane formScroll = new JScrollPane(dynamicPanel);
        formScroll.setBorder(BorderFactory.createEmptyBorder());
        formScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        formScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);


        formScroll.setPreferredSize(new Dimension(500, 380));
        formScroll.setMinimumSize(new Dimension(500, 200));

        JPanel formWrap = new JPanel(new BorderLayout());
        formWrap.setBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createEmptyBorder(6, 10, 6, 10),
                        BorderFactory.createTitledBorder("Initial values & links")
                )
        );
        formWrap.add(formScroll, BorderLayout.CENTER);
        formWrap.setMinimumSize(new Dimension(450, 200));
        formScroll.getVerticalScrollBar().setUnitIncrement(16);


        // Status panel (small, fixed)
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createEmptyBorder(4, 10, 4, 10));
        statusPanel.add(statusLabel, BorderLayout.CENTER);
        statusPanel.setMinimumSize(new Dimension(100, 28));

        // SPLITTER
        JSplitPane split = new JSplitPane(
                JSplitPane.VERTICAL_SPLIT,
                formWrap,
                statusPanel
        );
        split.setResizeWeight(1.0);          // form gets all resizing
        split.setDividerSize(6);
        split.setBorder(null);
        split.setContinuousLayout(true);

        // Initial divider position
        split.setDividerLocation(0.85);

        add(split, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout());
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 4));
        btns.setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));

        createBtn.setPreferredSize(new Dimension(90, 28));
        cancelBtn.setPreferredSize(new Dimension(90, 28));
        createBtn.setFont(createBtn.getFont().deriveFont(Font.BOLD));

        btns.add(createBtn);
        btns.add(cancelBtn);

        south.add(btns, BorderLayout.NORTH);
        add(south, BorderLayout.SOUTH);

        populateInterfaces();

        ifaceCombo.addActionListener(e -> rebuildFormForSelectedInterface());
        createBtn.addActionListener(this::onCreate);
        cancelBtn.addActionListener(e -> { setVisible(false); dispose(); });

        pack();
        Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
        int maxHeight = (int)(screen.height * 0.75);

        Dimension d = getSize();
        if (d.height > maxHeight) {
            setSize(new Dimension(d.width, maxHeight));
        }
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
        compInputs.clear();
        relInputs.clear();

        org.tzi.use.dtdl.DTDLModel.Interface iface = getSelectedInterface();
        if (iface == null) {
            refreshEmptyForm();
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
            row = addSectionHeader("Attributes", gbc, row);

            for (Property p : props) {
                JComponent input = createInputForProperty(p, iface);
                propInputs.put(p.getName(), input);
                row = addLabeledRow(p.getName(), input, gbc, row);
            }
        }

        if (!comps.isEmpty()) {
            row = addSectionHeader("Components", gbc, row);

            for (Component c : comps) {
                String targetIfaceId = c.getSchemaInterface() != null ? c.getSchemaInterface().getId() : null;
                JComboBox<String> combo = createUseObjectCombo(targetIfaceId);
                compInputs.put(c.getName(), combo);
                row = addLabeledRow(c.getName(), combo, gbc, row);
            }
        }

        if (!rels.isEmpty()) {
            row = addSectionHeader("Relationships", gbc, row);

            for (Relationship r : rels) {
                String targetIfaceId = (r.getTarget() != null ? (r.getTarget() != null ? r.getTarget().getId() : r.getTarget().toString()) : null);
                JComboBox<String> combo = createUseObjectCombo(targetIfaceId);
                relInputs.put(r.getName(), combo);
                row = addLabeledRow(r.getName(), combo, gbc, row);
            }
        }

        dynamicPanel.revalidate();
        dynamicPanel.repaint();
        pack();
    }

    private Interface getSelectedInterface() {
        Object sel = ifaceCombo.getSelectedItem();
        if (sel == null) return null;

        String ifaceId = comboToId.get(sel.toString());
        if (ifaceId == null) return null;

        DTDLModelRegistry registry = DTDLPluginState.registry();
        return registry.getCanonicalModel().getInterface(ifaceId);
    }

    private JComponent createInputForProperty(Property p, Interface iface) {
        org.tzi.use.dtdl.DTDLModel.Schema.Schema s = resolveNamedSchema(p.getSchema(), iface);
        if (s instanceof PrimitiveType pt) {
            return createPrimitiveInput(pt);
        }

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object obj) {
            return new ObjectInputPanel(obj.getFields(), iface, adapter);
        }

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum e) {
            JComboBox<String> cb = new JComboBox<>();
            cb.addItem("(none)");
            for (EnumValue ev : e.getValues()) {
                if (ev != null) cb.addItem(ev.getName());
            }
            return cb;
        }

        if (s instanceof Array arr) {
            return new ArrayInputPanel(resolveNamedSchema(arr.getElementSchema(), iface), iface, adapter);
        }

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Map.Map map) {
            return new MapInputPanel(map, iface, adapter);
        }

        return new JTextField(20);
    }

    private JComponent createInputForSchema(Schema s, Interface iface) {
        s = resolveNamedSchema(s, iface);

        if (s instanceof PrimitiveType pt) return createPrimitiveInput(pt);

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum e) {
            JComboBox<String> cb = new JComboBox<>();
            cb.addItem("(none)");
            for (EnumValue ev : e.getValues()) if (ev != null) cb.addItem(ev.getName());
            return cb;
        }

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object obj) {
            return new ObjectInputPanel(obj.getFields(), iface, adapter);
        }

        if (s instanceof Array arr) {
            return new ArrayInputPanel(resolveNamedSchema(arr.getElementSchema(), iface), iface, adapter);
        }

        if (s instanceof org.tzi.use.dtdl.DTDLModel.Schema.Map.Map map) {
            return new MapInputPanel(map, iface, adapter);
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
        if (comp instanceof ValueProvider vp) return vp.getValue();

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

            Object raw = extractValueFromInput(propInputs.get(key));

            // panels return InputValidation.INVALID when the user entered invalid data
            if (raw == InputValidation.INVALID) {
                JOptionPane.showMessageDialog(this,
                        "Invalid value for property " + key,
                        "Validation error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            if (raw != null) {
                collected.put(key, raw);
            }
        }

        try {
            // AUTHORITATIVE COERCION STEP: validate & coerce all collected properties
            Map<String, Object> coercedForCreate = new LinkedHashMap<>();
            List<Property> propDefs = iface.getContents().stream().filter(c -> c instanceof Property).map(c -> (Property)c).collect(Collectors.toList());
            for (Property p : propDefs) {
                String key = p.getName();
                Object raw = collected.get(key);
                Schema sch = resolveNamedSchema(p.getSchema(), iface);
                Object coerced = coerceToSchemaRecursive(sch, raw);
                if (raw != null && coerced == null) {
                    JOptionPane.showMessageDialog(this,
                            "Invalid value for property " + key,
                            "Validation error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }
                if (coerced != null) coercedForCreate.put(key, coerced);
            }

            useService.ensureSystemExists();

            UseSystemApi sysApi = UseSystemApi.create(session);
            UseModelApi modelApi = new UseModelApi(session.system().model());

            Optional<String> mapped = registry.getClassNameForDtmi(ifaceId);

            if (mapped.isEmpty()) {
                throw new IllegalStateException(
                        "No class mapping found for DTMI: " + ifaceId + ". Model should have been created through the mapper.");
            }

            String className = mapped.get();

            MObject obj = sysApi.createObject(className, name);
            String createdName = obj.name();

            for (Map.Entry<String, Object> en : coercedForCreate.entrySet()) {
                String attr = en.getKey();
                Object val = en.getValue();
                MClass cls = session.system().model().getClass(obj.cls().name());
                MAttribute mAttr = cls != null ? cls.attribute(attr, true) : null;

                if (mAttr != null && mAttr.name() != null &&
                        mAttr.name().startsWith(Utils.TELEMETRY_ATTR_PREFIX)) {
                    System.err.println("[DTDL] skipping telemetry attribute on create: " + mAttr.name());
                    continue;
                }

                if (mAttr == null) {
                    // attempt to find an attribute that matches the telemetry-prefixed form (should not be set)
                    String telCandidate = Utils.TELEMETRY_ATTR_PREFIX + Utils.sanitize(attr);
                    MAttribute telAttr = cls != null ? cls.attribute(telCandidate, true) : null;
                    if (telAttr != null) {
                        System.err.println("[DTDL] refusing to set telemetry attribute (found by prefixed name): " + telAttr.name());
                        continue;
                    }
                }

                org.tzi.use.uml.ocl.type.Type attrType = mAttr != null ? mAttr.type() : null;

                System.err.println("[DEBUG] setting attribute:");
                System.err.println("  object      = " + createdName);
                System.err.println("  class       = " + (cls != null ? cls.name() : "<null>"));
                System.err.println("  attribute   = " + attr);
                System.err.println("  raw value   = " + val + " (" + (val != null ? val.getClass() : "null") + ")");
                System.err.println("  attr type   = " + attrType + " (" + (attrType != null ? attrType.getClass() : "null") + ")");

                String oclExpr = useService.toOclLiteral(val, attrType);

                System.err.println("  ocl literal = " + oclExpr);

                Value value = useService.buildUseValue(attrType, val);

                try {
                    if (mAttr == null) {
                        System.err.println("  attribute not found on class (skipping): " + attr);
                    } else {
                        sysApi.setAttributeValueEx(session.system().state().objectByName(createdName), mAttr, value);
                        System.err.println("  assignment OK via setAttributeValueEx");
                        System.err.println("  stored value = " + value);
                    }
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
                String assoc = useService.findAssociationBetweenClassesForRole(createdName, s, compName);
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
                String assoc = useService.findAssociationBetweenClassesForRole(createdName, s, relName);
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
            nameField.setText("");
            rebuildFormForSelectedInterface();
        } catch (Exception ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, "Creation failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JComboBox<String> createUseObjectCombo(String targetIfaceId) {
        JComboBox<String> combo = new JComboBox<>();
        combo.addItem("(none)");

        List<String> useObjects = useService.listUseObjectsForDtInterface(targetIfaceId);
        if (useObjects.isEmpty()) {
            combo.addItem("(no matching USE objects)");
        } else {
            for (String o : useObjects) combo.addItem(o);
        }
        return combo;
    }

    private int addLabeledRow(String label, JComponent input, GridBagConstraints gbc, int row) {
        gbc.gridwidth = 1; gbc.weightx = 0; gbc.gridx = 0; gbc.gridy = row;
        dynamicPanel.add(new JLabel(label + ":"), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        dynamicPanel.add(input, gbc);

        return row + 1;
    }

    private int addSectionHeader(String title, GridBagConstraints gbc, int row) {
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.gridwidth = 2;
        gbc.weightx = 1.0;
        dynamicPanel.add(sectionHeader(title), gbc);
        return row + 1;
    }

    private JLabel sectionHeader(String text) {
        JLabel lbl = new JLabel(text);
        lbl.setFont(lbl.getFont().deriveFont(Font.BOLD));
        lbl.setBorder(BorderFactory.createEmptyBorder(8, 0, 4, 0));
        return lbl;
    }

    private void refreshEmptyForm() {
        dynamicPanel.revalidate();
        dynamicPanel.repaint();
        pack();
    }
}
