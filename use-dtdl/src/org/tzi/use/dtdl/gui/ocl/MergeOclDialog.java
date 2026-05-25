package org.tzi.use.dtdl.gui.ocl;

import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.main.Session;
import org.tzi.use.dtdl.integration.DTDLOCLIntegrator;
import org.tzi.use.uml.mm.MMPrintVisitor;
import org.tzi.use.util.uml.sorting.UseFileOrderComparator;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.PrintWriter;
import java.io.StringWriter;

/**
 * Dialog that contains the UI previously embedded in the action.
 * Preserves original preview + compile behavior.
 */
public class MergeOclDialog extends JDialog {

    public MergeOclDialog(Component parent, Session session) {
        super(SwingUtilities.getWindowAncestor(parent),
                "Merge OCL / .use into current model",
                ModalityType.APPLICATION_MODAL);

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        JPanel main = new JPanel(new BorderLayout(8,8));
        main.setBorder(new EmptyBorder(8,8,8,8));
        getContentPane().add(main);

        // instruction + text area
        JLabel info = new JLabel("<html>Paste class operation blocks in the top box and OCL constraints in the bottom box.<br>"
                + "Each operation block must start with <b>class ClassName</b> and will be injected into that generated class.</html>");
        info.setFont(info.getFont().deriveFont(Font.PLAIN, 12f));
        main.add(info, BorderLayout.NORTH);

        JTextArea operationArea = new JTextArea(12, 100);
        operationArea.setLineWrap(false);

        JTextArea oclArea = new JTextArea(12, 100);
        oclArea.setLineWrap(false);

        JPanel center = new JPanel(new GridLayout(2, 1, 8, 8));
        center.add(wrapEditor("Class operations", operationArea));
        center.add(wrapEditor("OCL constraints", oclArea));
        main.add(center, BorderLayout.CENTER);

        // buttons
        JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton compileBtn = new JButton("Compile & Apply");
        JButton previewBtn = new JButton("Preview Model Text");
        JButton closeBtn = new JButton("Close");
        btns.add(previewBtn);
        btns.add(compileBtn);
        btns.add(closeBtn);
        main.add(btns, BorderLayout.SOUTH);

        previewBtn.addActionListener(e -> {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw, true);

            try {
                String merged = DTDLOCLIntegrator.buildMergedSpec(
                        session,
                        operationArea.getText(),
                        oclArea.getText(),
                        pw
                );

                pw.println(merged == null ? "[Preview] Failed to build merged spec." : merged);
            } catch (Throwable t) {
                pw.println("[Preview] Failed to export model: " + t.getMessage());
            }

            JTextArea preview = new JTextArea(sw.toString(), 30, 120);
            preview.setEditable(false);
            preview.setCaretPosition(0); // pointer
            JScrollPane psp = new JScrollPane(preview, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            JOptionPane.showMessageDialog(this, psp, "Preview merged specification", JOptionPane.PLAIN_MESSAGE);
        });

        compileBtn.addActionListener(e -> {
            compileBtn.setEnabled(false);
            StringWriter diagSw = new StringWriter();
            PrintWriter diagPw = new PrintWriter(diagSw, true);

            DTDLOCLIntegrator.CompileResult res = DTDLOCLIntegrator.compileAndReplaceSystemWithOcl(
                    session,
                    operationArea.getText(),
                    oclArea.getText(),
                    diagPw
            );

            String diag = diagSw.toString();
            if (res.success) {
                JTextArea out = new JTextArea(diag.isEmpty() ? "Compilation successful.\nSystem replaced." : diag, 20, 100);
                out.setEditable(false);
                JScrollPane scroll = new JScrollPane(out, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                JOptionPane.showMessageDialog(this, scroll, "Success", JOptionPane.INFORMATION_MESSAGE);
                dispose();

                DTDLPluginState.operationService(session).removeRules();
            } else {
                JTextArea out = new JTextArea(diag.isEmpty() ? "Compilation failed (no diagnostics available)." : diag, 20, 100);
                out.setEditable(false);
                JScrollPane scroll = new JScrollPane(out, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                JOptionPane.showMessageDialog(this, scroll, "Compilation failed", JOptionPane.ERROR_MESSAGE);
                compileBtn.setEnabled(true);
            }
        });

        closeBtn.addActionListener(e -> dispose());

        pack();
        setLocationRelativeTo(parent);
    }

    private JPanel wrapEditor(String title, JTextArea area) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBorder(BorderFactory.createTitledBorder(title));
        panel.add(new JScrollPane(area, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED), BorderLayout.CENTER);
        return panel;
    }
}
