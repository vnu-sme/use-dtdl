package org.tzi.use.dtdl.integration;

import org.tzi.use.dtdl.actions.DTDLPluginState;
import org.tzi.use.main.Session;
import org.tzi.use.parser.use.USECompiler;
import org.tzi.use.uml.mm.MModel;
import org.tzi.use.uml.mm.ModelFactory;
import org.tzi.use.uml.sys.MSystem;
import org.tzi.use.uml.mm.MMPrintVisitor;
import org.tzi.use.util.uml.sorting.UseFileOrderComparator;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.util.Objects;

/**
 * Utility to merge an existing MModel (from Session.system()) with user-provided OCL/.use text,
 * compile it as a single specification and replace the session's system with the compiled result.
 */
public final class DTDLOCLIntegrator {

    public static final class CompileResult {
        public final boolean success;
        public final String diagnostics;
        public final MSystem newSystem;

        public CompileResult(boolean success, String diagnostics, MSystem newSystem) {
            this.success = success;
            this.diagnostics = diagnostics;
            this.newSystem = newSystem;
        }
    }

    /**
     * Combine the current MModel (text form) with the provided OCL/spec text, compile and replace session system.
     * - session must not be null and must have a system/model.
     * - oclSpec may contain OCL constraints, operation bodies, or any .use additions the user wishes to add.
     *
     * Returns CompileResult containing success flag, textual diagnostics (stderr-like), and the new MSystem on success.
     */
    public static CompileResult compileAndReplaceSystemWithOcl(Session session, String oclSpec, PrintWriter diagWriter) {
        Objects.requireNonNull(session, "session");
        if (session.system() == null || session.system().model() == null) {
            diagWriter.println("[DTDLOCLIntegrator] No existing system/model found in session.");
            diagWriter.flush();
            return new CompileResult(false, "[no model]", null);
        }

        try {
            // 1) Export current model to textual .use representation
            StringWriter modelSw = new StringWriter();
            PrintWriter modelPw = new PrintWriter(modelSw, true);
            MMPrintVisitor mmv = new MMPrintVisitor(modelPw);

            session.system().model().processWithVisitor(mmv);
            modelPw.flush();
            String modelText = modelSw.toString();

            // inject MDataTypes after "model <name>"
            StringWriter dtSw = new StringWriter();
            PrintWriter dtPw = new PrintWriter(dtSw, true);
            MMPrintVisitor dtVisitor = new MMPrintVisitor(dtPw);

            var model = session.system().model();
            org.tzi.use.uml.mm.MDataType[] dataTypes =
                    model.dataTypes().toArray(new org.tzi.use.uml.mm.MDataType[0]);
            java.util.Arrays.sort(dataTypes, new UseFileOrderComparator());

            for (org.tzi.use.uml.mm.MDataType dt : dataTypes) {
                dt.processWithVisitor(dtVisitor);
                dtPw.println();
            }

            dtPw.flush();
            String dtText = dtSw.toString();

            // remove attributes blocks to avoid merged parser errors
            dtText = dtText.replaceAll("(?ms)\\n\\s*attributes\\b.*?(?=\\n\\s*(operations\\b|end\\b))", "\n");

            // insert after first line (model declaration)
            int firstNewline = modelText.indexOf('\n');
            if (firstNewline > 0) {
                modelText = modelText.substring(0, firstNewline + 1)
                        + "\n" + dtText + "\n"
                        + modelText.substring(firstNewline + 1);
            }

            // 2) Concatenate model text and user-provided OCL/spec
            StringBuilder combined = new StringBuilder();
            combined.append("// ---- model (generated) ----\n");
            combined.append(modelText);
            combined.append("\n\n// ---- user-provided OCL/spec ----\n");
            combined.append(oclSpec == null ? "" : oclSpec);
            combined.append("\n");

            System.out.println("==== FINAL COMBINED SPEC ====");
            System.out.println(combined);
            System.out.println("==== END FINAL COMBINED SPEC ====");

            // 3) Compile combined specification
            String specName = "dtdl-merged-spec";
            StringWriter compileSw = new StringWriter();
            PrintWriter compilePw = new PrintWriter(compileSw, true);

            MModel compiledModel = USECompiler.compileSpecification(
                    combined.toString(),
                    specName,
                    compilePw,
                    new ModelFactory()
            );

            compilePw.flush();
            String compileDiagnostics = compileSw.toString();

            if (compiledModel == null) {
                // compilation failed; return diagnostics
                if (diagWriter != null) {
                    diagWriter.println("[DTDLOCLIntegrator] Compilation failed for merged spec.");
                    diagWriter.println(compileDiagnostics);
                    diagWriter.flush();
                }
                return new CompileResult(false, compileDiagnostics, null);
            }

            // 4) Create new system from compiled model and set it into session (safe re-init)
            MSystem newSystem = new MSystem(compiledModel);
            try {
                newSystem.ensureStateLinkSetsForModel();
                newSystem.state().updateDerivedValues(true);
            } catch (Throwable t) {
                if (diagWriter != null) {
                    diagWriter.println("[DTDLOCLIntegrator] Warning while initializing new system: " + t.getMessage());
                    t.printStackTrace(diagWriter);
                    diagWriter.flush();
                }
            }

            session.setSystem(newSystem);

            if (diagWriter != null) {
                diagWriter.println("[DTDLOCLIntegrator] Successfully compiled and replaced system with merged spec.");
                diagWriter.flush();
            }

            return new CompileResult(true, compileDiagnostics, newSystem);
        } catch (Throwable t) {
            if (diagWriter != null) {
                diagWriter.println("[DTDLOCLIntegrator] Unexpected error: " + t.getMessage());
                t.printStackTrace(diagWriter);
                diagWriter.flush();
            }
            return new CompileResult(false, t.getMessage(), null);
        }
    }
}
