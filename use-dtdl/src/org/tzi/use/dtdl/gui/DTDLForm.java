package org.tzi.use.dtdl.gui;

import org.tzi.use.api.UseModelApi;
import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.ast.ASTInterface;
import org.tzi.use.dtdl.mapping.DTDLToMModelMapper;
import org.tzi.use.dtdl.parser.DTDLCompiler;
import org.tzi.use.dtdl.semantic.DTDLContext;
import org.tzi.use.dtdl.semantic.DTDLModelRegistry;
import org.tzi.use.dtdl.semantic.SemanticAnalyzerImpl;
import org.tzi.use.dtdl.semantic.SemanticError;
import org.tzi.use.gui.main.MainWindow;
import org.tzi.use.main.Session;
import org.tzi.use.gui.util.CloseOnEscapeKeyListener;
import org.tzi.use.gui.util.ExtFileFilter;
import org.tzi.use.config.Options;
import org.tzi.use.uml.mm.*;
import org.tzi.use.uml.ocl.type.EnumType;
import org.tzi.use.uml.sys.MSystem;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintWriter;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;


public class DTDLForm extends JDialog {
    private final Session session;
    private final MainWindow mainWindow;
    private JTextField filePathField;
    private PrintWriter logWriter;
    private final SemanticAnalyzerImpl analyzer;
    private DTDLModelRegistry sharedRegistry;

    public DTDLForm(Session session, MainWindow parent, DTDLModelRegistry registry) {
        super(parent, "Load DTDL File");
        this.session = session;
        this.mainWindow = parent;
        this.analyzer = new SemanticAnalyzerImpl(registry);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setResizable(true);

        logWriter = parent.logWriter();


        // File input UI
        JPanel filePanel = new JPanel(new FlowLayout());
        JLabel label = new JLabel("DTDL file:");
        filePathField = new JTextField(30);
        JButton browseButton = new JButton("Browse...");
        filePanel.add(label);
        filePanel.add(filePathField);
        filePanel.add(browseButton);

        browseButton.addActionListener(new ActionListener() {
            private JFileChooser chooser;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (chooser == null) {
                    chooser = new JFileChooser(Options.getLastDirectory().toString());
//                    chooser.setFileFilter(new ExtFileFilter("dtdl", "DTDL file"));
                    chooser.setDialogTitle("Open DTDL file");
                }
                int result = chooser.showOpenDialog(DTDLForm.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selected = chooser.getSelectedFile();
                    filePathField.setText(selected.getAbsolutePath());
                    Options.setLastDirectory(selected.getParentFile().toPath());
                }
            }
        });

        // Buttons
        JButton loadButton = new JButton("Load");
        JButton closeButton = new JButton("Close");

        JPanel buttonPanel = new JPanel();
        buttonPanel.add(loadButton);
        buttonPanel.add(closeButton);

        add(filePanel, BorderLayout.CENTER);
        add(buttonPanel, BorderLayout.SOUTH);

        // Events
        loadButton.addActionListener(e -> loadDTDL());
        closeButton.addActionListener(e -> closeDialog());

        pack();
        setLocationRelativeTo(parent);

        addKeyListener(new CloseOnEscapeKeyListener(this));
    }

    private void loadDTDL() {
        MModel targetModel = null;
        String path = filePathField.getText();
        if (path == null || path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please select a DTDL file first.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        File file = new File(path);
        if (!file.exists()) {
            JOptionPane.showMessageDialog(this, "Selected file does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }


        // 1. Parse
        ASTInterface iface = DTDLCompiler.compileSpecification(path, logWriter);
        if (iface == null) {
            JOptionPane.showMessageDialog(this, "DTDL parsing failed.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 2. Analyze
        DTDLModel model = analyzer.analyze(java.util.List.of(iface));
        DTDLContext ctx = analyzer.getContext();


        // 3. Handle semantic errors
        if (model == null) {
            StringBuilder sb = new StringBuilder("DTDL semantic errors:\n");
            for (SemanticError err : ctx.errors) {
                sb.append("- ").append(err.toString()).append("\n");
            }
            JOptionPane.showMessageDialog(this, sb.toString(), "Semantic Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // 4. Register model
        DTDLModelRegistry.RegistrationResult result = analyzer.getRegistry().registerModel(model, false);
        if (!result.success) {
            int res = JOptionPane.showConfirmDialog(this,
                    "Interfaces already registered: " + result.conflicts + ". Overwrite?",
                    "Conflict", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                analyzer.getRegistry().registerModel(model, true);
            } else {
                return;
            }
        }

//        model.prints();

        DTDLModel canonical = analyzer.getRegistry().getCanonicalModel();
        System.out.println("Canonical DTDL model (all registered interfaces):" + canonical.getInterfaces().size());
//        canonical.prints();


        // 4.1. Ensure a System+Model exists before mapping
        ensureSystemInitialized(file);


        // 5. Mapping via UseModelApi
        try {
            UseModelApi useModelApi = new UseModelApi(session.system().model());

            DTDLToMModelMapper mapper = new DTDLToMModelMapper(model, useModelApi, logWriter, analyzer.getRegistry());

            MModel mappedModel = mapper.map();

            // debug: print associations present in session/system model (the model our UI will get)
            MModel mm = session.system().model();
            System.out.println("[DTDLForm] Model after mapping: " + mm.name()
                    + " (dataTypes=" + mm.dataTypes().size()
                    + ", classes=" + mm.classes().size()
                    + ", associations=" + mm.associations().size() + ")");

            for (MAssociation a : mm.associations()) {
                try {
                    List<MAssociationEnd> ends = a.associationEnds();
                    String e0cls = ends.size()>0 && ends.get(0).cls()!=null ? ends.get(0).cls().name() : "<null>";
                    String e0role = ends.size()>0 ? ends.get(0).name() : "<null>";
                    String e1cls = ends.size()>1 && ends.get(1).cls()!=null ? ends.get(1).cls().name() : "<null>";
                    String e1role = ends.size()>1 ? ends.get(1).name() : "<null>";
                    System.out.println("[DTDLForm] association: " + a.name() + "  ends: " + e0cls + ":" + e0role + " <-> " + e1cls + ":" + e1role);
                } catch (Throwable t) {
                    t.printStackTrace(logWriter);
                }
            }

            System.out.println("[DTDLForm] classes and annotations:");
            for (MClass c : mm.classes()) {
                StringBuilder sb = new StringBuilder();
                sb.append(" - ").append(c.name()).append(" annotations=");
                try {
                    // try common getter names
                    Method m = c.getClass().getMethod("annotations");
                    Object anns = m.invoke(c);
                    sb.append(String.valueOf(anns));
                } catch (Throwable t) {
                    // fallback: iterate possible methods
                    sb.append("<no-annotations-method>");
                }
                System.out.println(sb.toString());
            }

            SwingUtilities.invokeLater(() ->
                    mainWindow.getModelBrowser().setModel(session.system().model())
            );

//            closeDialog();

        } catch (Exception ex) {
            ex.printStackTrace(logWriter);
            JOptionPane.showMessageDialog(
                    this,
                    "Mapping failed: " + ex.getMessage(),
                    "Mapping Error",
                    JOptionPane.ERROR_MESSAGE
            );
        }

        session.system().ensureStateLinkSetsForModel();
        session.system().state().updateDerivedValues(true);


        // 5. Success
        JOptionPane.showMessageDialog(
                mainWindow,
                "DTDL loaded and mapped successfully.\nInterfaces: " +
                        canonical.getInterfaces().size(),
                "Success",
                JOptionPane.INFORMATION_MESSAGE
        );

//        closeDialog();
    }

    private void closeDialog() {
        setVisible(false);
        dispose();
    }

    private void ensureSystemInitialized(File sourceFile) {
        try {
            boolean needCreate = false;

            try {
                if (session.system() == null) {
                    needCreate = true;
                } else if (session.system().model() == null) {
                    needCreate = true;
                }
            } catch (Throwable t) {
                needCreate = true;
            }

            if (!needCreate) {
                return;
            }

            // derive model name from source file
            String base = sourceFile.getName();
            int idx = base.lastIndexOf('.');
            if (idx > 0) {
                base = base.substring(0, idx);
            }

            String modelName = base.replaceAll("[^A-Za-z0-9_]", "_");
            if (modelName.isEmpty()) {
                modelName = "unnamed";
            }

            // create new empty model via UseModelApi (uses ModelFactory internally)
            UseModelApi api = new UseModelApi(modelName);
            MModel model = api.getModel();

            // create new system
            MSystem system = new MSystem(model);
            session.setSystem(system);

            try {
                system.ensureStateLinkSetsForModel();
                system.state().updateDerivedValues(true);
            } catch (Throwable t) {
                t.printStackTrace(logWriter);
            }

            if (logWriter != null) {
                logWriter.println(
                        "[DTDLForm] created new MModel/MSystem: " + modelName
                );
            }

        } catch (Exception ex) {
            // never crash import because of bootstrap
            ex.printStackTrace(logWriter);
            if (logWriter != null) {
                logWriter.println(
                        "[DTDLForm] Warning: failed to bootstrap system/model: "
                                + ex.getMessage()
                );
            }
        }
    }
}
