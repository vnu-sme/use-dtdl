package org.tzi.use.dtdl.gui.ocl;

import org.tzi.use.main.Session;
import org.tzi.use.dtdl.integration.DTDLOCLIntegrator;

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
        JLabel info = new JLabel("<html>Paste OCL expressions, operation bodies or .use fragments to be merged with the current model.<br>"
                + "The current model will be exported, concatenated with your text and compiled. On success the session's system will be replaced.</html>");
        info.setFont(info.getFont().deriveFont(Font.PLAIN, 12f));
        main.add(info, BorderLayout.NORTH);

        JTextArea textArea = new JTextArea(25, 100);
        textArea.setLineWrap(false);
        JScrollPane sp = new JScrollPane(textArea, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        main.add(sp, BorderLayout.CENTER);

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
                // print current model
                org.tzi.use.uml.mm.MMPrintVisitor mmv = new org.tzi.use.uml.mm.MMPrintVisitor(pw);
                session.system().model().processWithVisitor(mmv);
            } catch (Throwable t) {
                pw.println("[Preview] Failed to export model: " + t.getMessage());
            }
            pw.println("\n\n// ---- user-provided OCL/spec ----\n");
            pw.println(textArea.getText());
            pw.flush();

            JTextArea preview = new JTextArea(sw.toString(), 30, 120);
            preview.setEditable(false);
            preview.setCaretPosition(0);
            JScrollPane psp = new JScrollPane(preview, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
            JOptionPane.showMessageDialog(this, psp, "Preview merged specification", JOptionPane.PLAIN_MESSAGE);
        });

        compileBtn.addActionListener(e -> {
            compileBtn.setEnabled(false);
            String userText = textArea.getText();
            StringWriter diagSw = new StringWriter();
            PrintWriter diagPw = new PrintWriter(diagSw, true);

            DTDLOCLIntegrator.CompileResult res = DTDLOCLIntegrator.compileAndReplaceSystemWithOcl(session, userText, diagPw);

            String diag = diagSw.toString();
            if (res.success) {
                JTextArea out = new JTextArea(diag.isEmpty() ? "Compilation successful.\nSystem replaced." : diag, 20, 100);
                out.setEditable(false);
                JScrollPane scroll = new JScrollPane(out, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
                JOptionPane.showMessageDialog(this, scroll, "Success", JOptionPane.INFORMATION_MESSAGE);
                dispose();
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
}
