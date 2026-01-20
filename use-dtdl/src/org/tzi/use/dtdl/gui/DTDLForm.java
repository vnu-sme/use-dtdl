package org.tzi.use.dtdl.gui;

import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.dtdl.ast.ASTInterface;
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

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.PrintWriter;


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
        canonical.prints();


        // 5. Success
        JOptionPane.showMessageDialog(
                mainWindow,
                "Loaded DTDL successfully.\nInterfaces: " + model.getInterfaces().size(),
                "Success",
                JOptionPane.INFORMATION_MESSAGE
        );

        closeDialog();

//        Collection<Interface> ifaces = canonical.getInterfaces().values();
//
//        // populate a small chooser (JList, JComboBox) with iface.getId() / getDisplayName()
//        JComboBox<String> box = new JComboBox<>();
//        for (Interface inface : ifaces) box.addItem(inface.getId());
//
//        // when user presses "Create instance" button:
//        String selectedId = (String) box.getSelectedItem();
//        Interface selectedIface = canonical.getInterface(selectedId);
//
//        // create instance in your DTDLSystem (keep system instance somewhere)
//        DTDLSystem system = new DTDLSystem(sharedRegistry, analyzer.getContext());
//        DTDLInstance inst = system.createInstance(selectedIface, null, Map.of("capacity", 30));
//        system.startInstance(inst.getId());

    }

    private void closeDialog() {
        setVisible(false);
        dispose();
    }
}
