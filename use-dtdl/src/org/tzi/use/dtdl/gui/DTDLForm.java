package org.tzi.use.dtdl.gui;

import org.tzi.use.api.UseModelApi;
import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.DTDLModel.ContentElement;
import org.tzi.use.dtdl.DTDLModel.Interface;
import org.tzi.use.dtdl.DTDLModel.Relationship.Relationship;
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
import java.util.*;
import java.util.List;


public class DTDLForm extends JDialog {
    private final Session session;
    private final MainWindow mainWindow;
    private JTextField filePathField;
    private PrintWriter logWriter;
    private final SemanticAnalyzerImpl analyzer;

    private JFileChooser chooser;
    private File[] selectedFiles;
    private JCheckBox multiSelectCheckbox;

    public DTDLForm(Session session, MainWindow parent, DTDLModelRegistry registry) {
        super(parent, "Load DTDL File(s)");
        this.session = session;
        this.mainWindow = parent;
        this.analyzer = new SemanticAnalyzerImpl(registry);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLayout(new BorderLayout());
        setResizable(true);

        logWriter = parent.logWriter();


        // File input UI
        JPanel filePanel = new JPanel(new FlowLayout());
        JLabel label = new JLabel("DTDL file(s):");
        filePathField = new JTextField(30);
        JButton browseButton = new JButton("Browse...");
        multiSelectCheckbox = new JCheckBox("Select multiple files", false);

        filePanel.add(label);
        filePanel.add(filePathField);
        filePanel.add(browseButton);
        filePanel.add(multiSelectCheckbox);

        browseButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if (chooser == null) {
//                    chooser = new JFileChooser(Options.getLastDirectory().toString());
                    chooser = new JFileChooser("D:\\workspaces\\use\\use-dtdl\\src\\resources\\example"); // adhoc
                    chooser.setDialogTitle("Open DTDL file(s)");
                    chooser.setFileFilter(new ExtFileFilter("json", "DTDL file (json)"));
                }
                chooser.setMultiSelectionEnabled(multiSelectCheckbox.isSelected());
                int result = chooser.showOpenDialog(DTDLForm.this);
                if (result == JFileChooser.APPROVE_OPTION) {
                    if (chooser.isMultiSelectionEnabled()) {
                        selectedFiles = chooser.getSelectedFiles();
                        if (selectedFiles != null && selectedFiles.length > 1) {
                            filePathField.setText(selectedFiles.length + " files selected");
                        } else if (selectedFiles != null && selectedFiles.length == 1) {
                            filePathField.setText(selectedFiles[0].getAbsolutePath());
                        } else {
                            filePathField.setText("");
                        }
                        if (selectedFiles != null && selectedFiles.length > 0) {
                            Options.setLastDirectory(selectedFiles[0].getParentFile().toPath());
                        }
                    } else {
                        File selected = chooser.getSelectedFile();
                        selectedFiles = new File[]{selected};
                        filePathField.setText(selected.getAbsolutePath());
                        Options.setLastDirectory(selected.getParentFile().toPath());
                    }
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

        // determine files to load
        File[] filesToLoad = null;
        if (selectedFiles != null && selectedFiles.length > 0) {
            filesToLoad = selectedFiles;
        } else {
            String path = filePathField.getText();
            if (path == null || path.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Please select a DTDL file first.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            File f = new File(path);
            if (!f.exists()) {
                JOptionPane.showMessageDialog(this, "Selected file does not exist.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            filesToLoad = new File[]{f};
        }

        // sanity: ensure files exist
        for (File file : filesToLoad) {
            if (file == null || !file.exists()) {
                JOptionPane.showMessageDialog(this, "Selected file does not exist: " + file, "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
        }

        // 1. Parse all files into ASTInterfaces
        List<ASTInterface> astInterfaces = new ArrayList<>();
        for (File file : filesToLoad) {
            ASTInterface iface = DTDLCompiler.compileSpecification(file.getAbsolutePath(), logWriter);
            if (iface == null) {
                JOptionPane.showMessageDialog(this, "DTDL parsing failed for file: " + file.getName(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            astInterfaces.add(iface);
        }

        // 2. Analyze (multi-file) — analyzer will register all interfaces first
        DTDLModel model = analyzer.analyze(astInterfaces);
        DTDLContext ctx = analyzer.getContext();

        if (!ctx.warnings.isEmpty()) {
            StringBuilder w = new StringBuilder("DTDL warnings:\n");
            for (String msg : ctx.warnings) {
                w.append("- ").append(msg).append("\n");
            }
            // show as info (non-blocking)
            JOptionPane.showMessageDialog(this, w.toString(), "DTDL Warnings", JOptionPane.WARNING_MESSAGE);
        }

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

        // 4.1. Ensure a System+Model exists before mapping (use first file for name derivation)
        ensureSystemInitialized(filesToLoad[0]);

        // 5. Mapping via UseModelApi
        try {
            UseModelApi useModelApi = new UseModelApi(session.system().model());

            DTDLToMModelMapper mapper = new DTDLToMModelMapper(model, useModelApi, logWriter, analyzer.getRegistry());

            MModel mappedModel = mapper.map();

            SwingUtilities.invokeLater(() ->
                    mainWindow.getModelBrowser().setModel(session.system().model())
            );

        } catch (Exception ex) {
            ex.printStackTrace(logWriter);
            JOptionPane.showMessageDialog(
                    this,
                    "Mapping failed: " + ex.getMessage(),
                    "Mapping Error",
                    JOptionPane.ERROR_MESSAGE
            );
            return;
        }

        session.system().ensureStateLinkSetsForModel();
        session.system().state().updateDerivedValues(true);

        // 6. Success
        JOptionPane.showMessageDialog(
                mainWindow,
                "DTDL loaded and mapped successfully.\nInterfaces: " +
                        canonical.getInterfaces().size(),
                "Success",
                JOptionPane.INFORMATION_MESSAGE
        );
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

            // create new empty model via UseModelApi
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
                logWriter.println("[DTDLForm] created new MModel/MSystem: " + modelName);
            }

        } catch (Exception ex) {
            ex.printStackTrace(logWriter);
            if (logWriter != null) {
                logWriter.println("[DTDLForm] Warning: failed to bootstrap system/model: " + ex.getMessage());
            }
        }
    }
}
