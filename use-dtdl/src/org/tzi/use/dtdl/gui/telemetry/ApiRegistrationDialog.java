package org.tzi.use.dtdl.gui.telemetry;

import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.Schema.Array.Array;
import org.tzi.use.dtdl.DTDLModel.Telemetry.Telemetry;
import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.dtdl.gui.instance.SchemaInputFactory;
import org.tzi.use.dtdl.telemetry.BindingRegistry;
import org.tzi.use.dtdl.telemetry.HttpPollingAdapter;
import org.tzi.use.dtdl.telemetry.TelemetryAdapter;
import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Schema.*;
import org.tzi.use.dtdl.DTDLModel.Schema.Object.Field;
import org.tzi.use.dtdl.DTDLModel.Schema.Enum.EnumValue;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.use.UseRuntimeService;
import org.tzi.use.dtdl.util.Utils;
import org.tzi.use.main.Session;
import org.tzi.use.uml.sys.MObject;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.time.Instant;
import java.util.*;
import java.util.List;
import java.util.UUID;

/**
 * ApiRegistrationDialog (instance-first)
 * - Constructor accepts a USE Session
 * - User picks a USE object (instance) -> we resolve its interface (DTMI) and list telemetry entries
 * - For object telemetry we list fields for per-field JSON path input
 * - On Add & Start we create HttpPollingAdapter and register bindings that target the chosen instance
 */
public class ApiRegistrationDialog extends JDialog {
    private final DefaultListModel<String> listModel = new DefaultListModel<>();
    private final JList<String> adapterList = new JList<>(listModel);

    private final Session session;
    private final UseRuntimeService useService;

    private final JTextField urlField = new JTextField();
    private final JComboBox<String> methodComboBox = new JComboBox<>(new String[]{"GET", "POST"});
    private final JTextField intervalField = new JTextField("2000");
    private final JComboBox<String> instanceCombo = new JComboBox<>();
    private final JTextField deviceIdField = new JTextField();
    private final JTextField objectNameField = new JTextField(); // kept for manual override
    private final JPanel telemetryPanel = new JPanel(new GridBagLayout());

    // data structures to collect telemetry mapping inputs
    // For primitive telemetry: telemetryName -> JTextField (path)
    private final Map<String, JTextField> primitivePathFields = new LinkedHashMap<>();
    // For object telemetry: telemetryName -> (fieldName -> JTextField)
    private final Map<String, Map<String, JTextField>> objectFieldPathFields = new LinkedHashMap<>();

    public ApiRegistrationDialog(Window owner, Session session) {
        super(owner, "Register Device APIs (instance-first)", ModalityType.APPLICATION_MODAL);
        this.session = Objects.requireNonNull(session, "session required");
        this.useService = new UseRuntimeService(session);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout(8,8));
        JPanel content = new JPanel(new BorderLayout(8,8));
        content.setBorder(new EmptyBorder(8,8,8,8));
        add(content, BorderLayout.CENTER);

        // top: form
        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4,4,4,4);
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.gridx = 0; gbc.gridy = 0; gbc.weightx = 0;
        form.add(new JLabel("URL"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        form.add(urlField, gbc);

        gbc.gridx = 0; gbc.gridy++;
        form.add(new JLabel("Method"), gbc);

        gbc.gridx = 1;
        form.add(methodComboBox, gbc);

        gbc.gridx = 0; gbc.gridy++; gbc.weightx = 0;
        form.add(new JLabel("Interval ms"), gbc);
        gbc.gridx = 1; form.add(intervalField, gbc);

        // instance selector (new)
        gbc.gridx = 0; gbc.gridy++; gbc.weightx = 0;
        form.add(new JLabel("USE Object (instance)"), gbc);
        gbc.gridx = 1; gbc.weightx = 1.0;
        instanceCombo.setEditable(false);
        form.add(instanceCombo, gbc);

        // button to refresh instances
        gbc.gridx = 2; gbc.weightx = 0;
        JButton refreshInstances = new JButton("Refresh");
        form.add(refreshInstances, gbc);

        gbc.gridx = 0; gbc.gridy++; gbc.weightx = 0;
        form.add(new JLabel("Device ID"), gbc);
        gbc.gridx = 1; form.add(deviceIdField, gbc);

        gbc.gridx = 0; gbc.gridy++; form.add(new JLabel("Object Name (override)"), gbc);
        gbc.gridx = 1; form.add(objectNameField, gbc);

        content.add(form, BorderLayout.NORTH);

        // center: telemetry mapping panel
        JPanel center = new JPanel(new BorderLayout(4,4));
        center.add(new JLabel("Telemetry bindings (enter JSON path for each telemetry or nested field)"), BorderLayout.NORTH);

        JScrollPane teleScroll = new JScrollPane(telemetryPanel);
        teleScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        teleScroll.setPreferredSize(new Dimension(820, 320));
        telemetryPanel.setBorder(new EmptyBorder(6,6,6,6));
        content.add(teleScroll, BorderLayout.CENTER);

        // bottom: adapter list + buttons
        JPanel right = new JPanel(new BorderLayout(4,4));
        right.add(new JLabel("Registered adapters:"), BorderLayout.NORTH);
        adapterList.setVisibleRowCount(8);
        right.add(new JScrollPane(adapterList), BorderLayout.CENTER);

        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton addBtn = new JButton("Register");
        JButton importBtn = new JButton("Import...");
        JButton startAllBtn = new JButton("Start All");
        JButton stopBtn = new JButton("Stop Selected");
        JButton removeBtn = new JButton("Remove Selected");
        JButton closeBtn = new JButton("Close");

        btns.add(addBtn);
        btns.add(importBtn);
        btns.add(startAllBtn);
        btns.add(stopBtn);
        btns.add(removeBtn);
        btns.add(closeBtn);

        JPanel south = new JPanel(new BorderLayout());
        south.add(right, BorderLayout.CENTER);
        south.add(btns, BorderLayout.SOUTH);

        add(south, BorderLayout.SOUTH);

        // actions
        refreshInstances.addActionListener(e -> populateInstanceCombo());
        instanceCombo.addActionListener(e -> onInstanceSelected());
        importBtn.addActionListener(e -> onImport());
        addBtn.addActionListener(e -> onAdd());
        startAllBtn.addActionListener(e -> onStartAll());
        stopBtn.addActionListener(e -> onStopSelected());
        removeBtn.addActionListener(e -> onRemoveSelected());
        closeBtn.addActionListener(e -> dispose());

        // initial population
        reloadAdapterList();
        populateInstanceCombo();

        pack();
        setSize(920, 700);
        setLocationRelativeTo(owner);
    }

    private void populateInstanceCombo() {
        instanceCombo.removeAllItems();

        try {
            var state = session.system().state();
            var objects = state.allObjects(); // Map<String, MObject>

            if (objects.isEmpty()) {
                instanceCombo.addItem("(no USE objects found)");
                return;
            }

            instanceCombo.addItem("(select an object)");
            for (var obj : objects) {
                instanceCombo.addItem(obj.name());
            }

            instanceCombo.setSelectedIndex(0);
            System.err.println("[API-DLG] populateInstanceCombo: found " + objects.size() + " objects");

        } catch (Throwable t) {
            System.err.println("[API-DLG] populateInstanceCombo failed: " + t.getMessage());
            t.printStackTrace(System.err);
            instanceCombo.addItem("(error listing USE objects)");
        }
    }

    private void onInstanceSelected() {
        Object sel = instanceCombo.getSelectedItem();
        if (sel == null) return;

        String name = sel.toString();
        if (name.startsWith("(")) return;

        objectNameField.setText(name);
        rebuildTelemetryPanelForSelectedObject(name);
    }

    private void rebuildTelemetryPanelForSelectedObject(String objectName) {
        telemetryPanel.removeAll();
        primitivePathFields.clear();
        objectFieldPathFields.clear();

        if (objectName == null || objectName.isBlank()) {
            telemetryPanel.revalidate();
            telemetryPanel.repaint();
            return;
        }

        MObject obj = session.system().state().objectByName(objectName);
        if (obj == null) {
            telemetryPanel.add(new JLabel("Selected object not found: " + objectName));
            telemetryPanel.revalidate();
            telemetryPanel.repaint();
            return;
        }

        String className = obj.cls().name();

        // resolve interface mapped to this class
        DTDLModelRegistry reg = DTDLPluginState.registry();
        Interface iface = null;

        if (reg != null && reg.getCanonicalModel() != null) {
            for (Interface cand : reg.getCanonicalModel().getInterfaces().values()) {
                Optional<String> mapped = reg.getClassNameForDtmi(cand.getId());
                if (mapped.isPresent() && mapped.get().equals(className)) {
                    iface = cand;
                    break;
                }
            }
        }

        if (iface == null) {
            telemetryPanel.add(new JLabel("No DTDL interface mapping found for class: " + className));
            telemetryPanel.revalidate();
            telemetryPanel.repaint();
            return;
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(4, 4, 4, 4);
        gbc.anchor = GridBagConstraints.WEST;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;

        int row = 0;

        for (ContentElement ce : iface.getContents()) {
            if (!(ce instanceof Telemetry telemetry)) {
                continue;
            }

            String teleName = telemetry.getName();
            if (teleName == null) continue;

            Schema sch = telemetry.getSchema();
            sch = SchemaInputFactory.resolveNamedSchema(sch, iface);

            // section header
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 2;

            JLabel header = new JLabel(
                    teleName + " (" + (sch == null ? "no schema" : sch.getClass().getSimpleName()) + ")"
            );
            header.setFont(header.getFont().deriveFont(Font.BOLD));
            telemetryPanel.add(header, gbc);
            row++;

            // 1) Primitive or enum -> single value path
            if (sch instanceof PrimitiveType || sch instanceof org.tzi.use.dtdl.DTDLModel.Schema.Enum.Enum) {
                gbc.gridx = 0;
                gbc.gridy = row;
                gbc.gridwidth = 1;
                gbc.weightx = 0;
                telemetryPanel.add(new JLabel("value path:"), gbc);

                gbc.gridx = 1;
                gbc.weightx = 1.0;
                JTextField pathField = new JTextField();
                primitivePathFields.put(teleName, pathField);
                telemetryPanel.add(pathField, gbc);
                row++;
                continue;
            }

            // 2) Object schema -> per-field paths (existing behavior)
            if (sch instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object objSch) {
                Map<String, JTextField> fieldMap = new LinkedHashMap<>();

                for (Field f : objSch.getFields()) {
                    JTextField pf = new JTextField();
                    fieldMap.put(f.getName(), pf);
                    row = addLabeledRow(gbc, "- " + f.getName() + ":", pf, row);
                }

                objectFieldPathFields.put(teleName, fieldMap);
                continue;
            }

            // 3) Array schema -> if element is object, allow per-field inputs for element schema;
            //    otherwise treat as single value-path (extract entire array).
            if (sch instanceof Array arrSch) {
                Schema elem = SchemaInputFactory.resolveNamedSchema(arrSch.getElementSchema(), iface);

                if (elem instanceof org.tzi.use.dtdl.DTDLModel.Schema.Object.Object elemObj) {
                    // treat as object-like: show fields (user should enter JSON path that extracts element field,
                    // e.g. "items[0].temp" or whichever path syntax JacksonPath supports for their use case)
                    Map<String, JTextField> fieldMap = new LinkedHashMap<>();
                    // add an optional hint row explaining this is per-element field extraction
                    gbc.gridx = 0;
                    gbc.gridy = row;
                    gbc.gridwidth = 2;
                    JLabel hint = new JLabel("Array of objects — enter per-element field paths (paths are applied to each element).");
                    hint.setFont(hint.getFont().deriveFont(Font.ITALIC, hint.getFont().getSize() - 1f));
                    telemetryPanel.add(hint, gbc);
                    row++;

                    for (Field f : elemObj.getFields()) {
                        JTextField pf = new JTextField();
                        fieldMap.put(f.getName(), pf);
                        row = addLabeledRow(gbc, "- " + f.getName() + ":", pf, row);
                    }
                    objectFieldPathFields.put(teleName, fieldMap);
                } else {
                    // array of primitive/enum/map -> single value path that extracts the array
                    gbc.gridx = 0;
                    gbc.gridy = row;
                    gbc.gridwidth = 1;
                    gbc.weightx = 0;
                    telemetryPanel.add(new JLabel("value path (array):"), gbc);

                    gbc.gridx = 1;
                    gbc.weightx = 1.0;
                    JTextField pf = new JTextField();
                    primitivePathFields.put(teleName, pf);
                    telemetryPanel.add(pf, gbc);
                    row++;
                }
                continue;
            }

            // 4) Map schema -> treat as single value path (extract whole map)
            if (sch instanceof org.tzi.use.dtdl.DTDLModel.Schema.Map.Map) {
                gbc.gridx = 0;
                gbc.gridy = row;
                gbc.gridwidth = 1;
                gbc.weightx = 0;
                telemetryPanel.add(new JLabel("value path (map):"), gbc);

                gbc.gridx = 1;
                gbc.weightx = 1.0;
                JTextField pf = new JTextField();
                primitivePathFields.put(teleName, pf);
                telemetryPanel.add(pf, gbc);
                row++;
                continue;
            }

            // 5) fallback: single value path
            gbc.gridx = 0;
            gbc.gridy = row;
            gbc.gridwidth = 1;
            gbc.weightx = 0;
            telemetryPanel.add(new JLabel("value path:"), gbc);

            gbc.gridx = 1;
            gbc.weightx = 1.0;
            JTextField pf = new JTextField();
            primitivePathFields.put(teleName, pf);
            telemetryPanel.add(pf, gbc);
            row++;
        }

        telemetryPanel.revalidate();
        telemetryPanel.repaint();
    }



    private void onAdd() {
        String url = urlField.getText().trim();
        if (url.isEmpty()) {
            JOptionPane.showMessageDialog(this, "URL required");
            return;
        }

        long interval;
        try {
            interval = Long.parseLong(intervalField.getText().trim());
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Invalid interval");
            return;
        }

        String selectedInstance = null;
        Object sel = instanceCombo.getSelectedItem();
        if (sel != null) {
            String s = sel.toString();
            if (!s.startsWith("(")) selectedInstance = s;
        }
        String objOverride = Utils.blankToNull(objectNameField.getText());
        String bindTargetObject = objOverride != null ? objOverride : selectedInstance;

        if (bindTargetObject == null) {
            JOptionPane.showMessageDialog(this, "Choose or enter object name to bind telemetry to (object name required)");
            return;
        }

        String dev = Utils.blankToNull(deviceIdField.getText());

        String id = "api-" + UUID.randomUUID().toString().substring(0,8);

        String method = methodComboBox.getSelectedItem().toString();
        HttpPollingAdapter adapter = new HttpPollingAdapter(id, url, method, interval, dev, bindTargetObject);

        try {
            // register and start adapter
            DTDLPluginState.registerAdapter(adapter, session);
            listModel.addElement(renderAdapterLine(adapter));

            // register bindings for entered paths
            BindingRegistry registry = DTDLPluginState.telemetryEngine().registry();

            // primitive telemetry bindings
            for (Map.Entry<String, JTextField> en : primitivePathFields.entrySet()) {
                String teleName = en.getKey();
                String path = en.getValue().getText();

                if (path != null) path = path.trim();

                if (path != null && !path.isEmpty()) {
                    String bindId = "bind-" + UUID.randomUUID().toString().substring(0,8);
                    BindingRegistry.Binding b = new BindingRegistry.Binding(null, teleName, adapter.id(), bindTargetObject, path);
                    registry.register(bindId, b);
                    System.err.println("[API-DLG] Registered binding: " + bindId + " -> " + b);
                }
            }

            // object telemetry bindings
            for (Map.Entry<String, Map<String, JTextField>> en : objectFieldPathFields.entrySet()) {
                String teleName = en.getKey();
                Map<String, JTextField> fields = en.getValue();
                Map<String, String> fieldPaths = new LinkedHashMap<>();
                boolean any = false;
                for (Map.Entry<String, JTextField> fe : fields.entrySet()) {
                    String fname = fe.getKey();
                    String path = fe.getValue().getText();
                    if (path != null) path = path.trim();
                    if (path != null && !path.isEmpty()) {
                        fieldPaths.put(fname, path);
                        any = true;
                    }
                }
                if (any) {
                    String bindId = "bind-" + UUID.randomUUID().toString().substring(0,8);
                    BindingRegistry.Binding b = new BindingRegistry.Binding(null, teleName, adapter.id(), bindTargetObject, fieldPaths);
                    registry.register(bindId, b);
                    System.err.println("[API-DLG] Registered binding: " + bindId + " -> " + b);
                }
            }

            JOptionPane.showMessageDialog(this, "Adapter registered: " + id);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to attach adapter:\n" + ex.getMessage());
        }
    }

    private void onImport() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Import telemetry registration JSON");
        int result = chooser.showOpenDialog(this);
        if (result != JFileChooser.APPROVE_OPTION) {
            return;
        }

        File file = chooser.getSelectedFile();
        if (file == null || !file.exists()) {
            JOptionPane.showMessageDialog(this, "Selected file does not exist.");
            return;
        }

        try {
            int count = DTDLPluginState.registerTelemetryImport(file, session);
            reloadAdapterList();
            JOptionPane.showMessageDialog(this, "Imported " + count + " adapter(s).");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Import failed:\n" + ex.getMessage());
        }
    }

    private void onStartAll() {
        try {
            DTDLPluginState.startAllRegisteredAdapters();
            JOptionPane.showMessageDialog(this, "All registered adapters started.");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this, "Failed to start adapters:\n" + ex.getMessage());
        }
    }

    private void onStopSelected() {
        List<String> sel = adapterList.getSelectedValuesList();
        if (sel.isEmpty()) { JOptionPane.showMessageDialog(this, "Select one or more adapters to stop"); return; }
        for (String line : sel) {
            String id = parseIdFromLine(line);
            DTDLPluginState.detachAndUnregisterAdapter(id);
            listModel.removeElement(line);
        }
    }

    private void onRemoveSelected() {
        onStopSelected();
    }

    private String renderAdapterLine(TelemetryAdapter a) {
        return a.id() + " | " + a.getClass().getSimpleName() + " | url=" + (a instanceof HttpPollingAdapter ? ((HttpPollingAdapter) a).url() : "<n/a>");
    }

    private String parseIdFromLine(String line) {
        if (line == null) return null;
        int sp = line.indexOf(' ');
        if (sp < 0) return line;
        return line.substring(0, sp);
    }

    private void reloadAdapterList() {
        listModel.clear();
        Map<String, ? extends TelemetryAdapter> regs = DTDLPluginState.getRegisteredAdapters();
        for (TelemetryAdapter a : regs.values()) {
            listModel.addElement(renderAdapterLine(a));
        }
    }

    public static void showDialog(Component parent, Session session) {
        Window w = parent == null ? null : SwingUtilities.getWindowAncestor(parent);
        ApiRegistrationDialog d = new ApiRegistrationDialog(w, session);
        d.setVisible(true);
    }

    private int addLabeledRow(GridBagConstraints gbc, String labelText, JComponent input, int row) {
        gbc.gridwidth = 1;
        gbc.gridx = 0;
        gbc.gridy = row;
        gbc.weightx = 0;
        gbc.anchor = GridBagConstraints.WEST;
        telemetryPanel.add(new JLabel(labelText), gbc);

        gbc.gridx = 1;
        gbc.weightx = 1.0;
        gbc.anchor = GridBagConstraints.WEST;
        telemetryPanel.add(input, gbc);

        return row + 1;
    }
}
