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
import java.util.List;
import java.util.Objects;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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
    public static CompileResult compileAndReplaceSystemWithOcl(Session session, String operationBodies, String oclSpec, PrintWriter diagWriter) {
        Objects.requireNonNull(session, "session");
        PrintWriter log = diagWriter != null ? diagWriter : new PrintWriter(System.out, true);

        if (session.system() == null || session.system().model() == null) {
            log.println("[DTDLOCLIntegrator] No existing system/model found in session.");
            log.flush();
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

            String modelWithOps = injectClassBodies(modelText, operationBodies, diagWriter);

            combined = new StringBuilder();
            combined.append("// ---- model (generated) ----\n");
            combined.append(modelWithOps);
            combined.append("\n\n// ---- user-provided OCL/spec ----\n");
            if (oclSpec != null && !oclSpec.isBlank()) {
                combined.append(oclSpec);
            }
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

    public static String buildMergedSpec(Session session, String operationBodies, String oclSpec, PrintWriter log) {
        if (session == null || session.system() == null || session.system().model() == null) {
            if (log != null) {
                log.println("[DTDLOCLIntegrator] Cannot build spec: no model.");
            }
            return null;
        }

        try {
            StringWriter modelSw = new StringWriter();
            PrintWriter modelPw = new PrintWriter(modelSw, true);
            MMPrintVisitor mmv = new MMPrintVisitor(modelPw);

            session.system().model().processWithVisitor(mmv);
            modelPw.flush();
            String modelText = modelSw.toString();

            StringWriter dtSw = new StringWriter();
            PrintWriter dtPw = new PrintWriter(dtSw, true);
            MMPrintVisitor dtVisitor = new MMPrintVisitor(dtPw);

            var model = session.system().model();
            org.tzi.use.uml.mm.MDataType[] dataTypes = model.dataTypes().toArray(new org.tzi.use.uml.mm.MDataType[0]);
            java.util.Arrays.sort(dataTypes, new UseFileOrderComparator());

            for (org.tzi.use.uml.mm.MDataType dt : dataTypes) {
                dt.processWithVisitor(dtVisitor);
                dtPw.println();
            }

            dtPw.flush();
            String dtText = dtSw.toString();

            dtText = dtText.replaceAll("(?ms)\\n\\s*attributes\\b.*?(?=\\n\\s*(operations\\b|end\\b))", "\n");

            int firstNewline = modelText.indexOf('\n');
            if (firstNewline > 0) {
                modelText = modelText.substring(0, firstNewline + 1)
                        + "\n" + dtText + "\n"
                        + modelText.substring(firstNewline + 1);
            }

            String modelWithOps = injectClassBodies(modelText, operationBodies, log);

            StringBuilder combined = new StringBuilder();
            combined.append("// ---- model (generated) ----\n");
            combined.append(modelWithOps);
            combined.append("\n\n// ---- user-provided OCL/spec ----\n");
            if (oclSpec != null && !oclSpec.isBlank()) {
                combined.append(oclSpec);
            }
            combined.append("\n");

            return combined.toString();
        } catch (Throwable t) {
            if (log != null) {
                log.println("[DTDLOCLIntegrator] buildMergedSpec error: " + t.getMessage());
            }
            return null;
        }
    }

    private static String injectClassBodies(String modelText, String operationText, PrintWriter diagWriter) {
        if (operationText == null || operationText.isBlank()) {
            return modelText;
        }

        Map<String, String> blocks = parseClassBlocks(operationText, diagWriter); // map class name => operation section
        String result = modelText;

        for (Map.Entry<String, String> en : blocks.entrySet()) {
            String className = en.getKey();
            String rawClassBlock = en.getValue();

            String opSection = stripWrapperLines(rawClassBlock);
            if (opSection.isBlank()) {
                if (diagWriter != null) {
                    diagWriter.println("[DTDLOCLIntegrator] Empty operation block for class: " + className);
                }
                continue;
            }

            if (diagWriter != null) {
                diagWriter.println("[DTDLOCLIntegrator] Injecting class operations into: " + className);
            }

            Pattern classPattern = Pattern.compile(
                    "(?ms)(^class\\s+" + Pattern.quote(className) + "\\b.*?\\R)(.*?)(^end\\s*$)"
            );
            Matcher cm = classPattern.matcher(result);

            if (!cm.find()) {
                if (diagWriter != null) {
                    diagWriter.println("[DTDLOCLIntegrator] Class not found in generated model: " + className);
                }
                continue;
            }

            String header = cm.group(1);
            String classBody = cm.group(2);
            String endLine = cm.group(3);

            // find operations block
            Pattern opsPattern = Pattern.compile("(?m)^\\s*operations\\s*$");
            Matcher om = opsPattern.matcher(classBody);

            if (!om.find()) {
                if (diagWriter != null) {
                    diagWriter.println("[DTDLOCLIntegrator] No operations section found in class: " + className);
                }
                continue;
            }

            // get everything above operations block
            String prefix = classBody.substring(0, om.start());

            if (!prefix.isEmpty() && !prefix.endsWith(System.lineSeparator())) {
                prefix = prefix + System.lineSeparator();
            }

            String replacement = header + prefix + "operations" + System.lineSeparator() + opSection + System.lineSeparator() + endLine;

            result = result.substring(0, cm.start())
                    + replacement
                    + result.substring(cm.end());

            if (diagWriter != null) {
                diagWriter.println("[DTDLOCLIntegrator] Replaced operations section for class: " + className);
            }
        }

        return result;
    }

    private static Map<String, String> parseClassBlocks(String operationText, PrintWriter diagWriter) {
        Map<String, String> blocks = new LinkedHashMap<>();

        String currentClass = null;
        StringBuilder currentBody = null;

        // class Person, class Person < Object, class    Person ==> group(1) = Person
        Pattern headerPattern = Pattern.compile("^\\s*class\\s+([A-Za-z_][A-Za-z0-9_]*)\\b.*$");

        for (String line : operationText.split("\\R", -1)) { // -1 keeps empty line
            Matcher hm = headerPattern.matcher(line);

            if (hm.matches()) {
                // put previous class and body to blocks
                if (currentClass != null) {
                    blocks.put(currentClass, currentBody.toString());
                }

                // start of new class
                currentClass = hm.group(1);
                currentBody = new StringBuilder();

                if (diagWriter != null) {
                    diagWriter.println("[DTDLOCLIntegrator] Found operation block for class: " + currentClass);
                }
                continue;
            }

            // If not header, append it to build operation body of current class
            if (currentClass != null) {
                currentBody.append(line).append(System.lineSeparator());
            }
        }

        // For last block
        if (currentClass != null) {
            blocks.put(currentClass, currentBody.toString());
        }

        return blocks;
    }

    private static String stripWrapperLines(String classBlock) {
        if (classBlock == null || classBlock.isBlank()) {
            return "";
        }

        String[] lines = classBlock.split("\\R", -1);
        int start = 0;
        int end = lines.length - 1;

        while (start <= end && lines[start].trim().isEmpty()) {
            start++;
        }

        if (start <= end && lines[start].trim().equalsIgnoreCase("operations")) {
            start++;
        }

        while (end >= start && lines[end].trim().isEmpty()) {
            end--;
        }

        if (end >= start && lines[end].trim().equalsIgnoreCase("end")) {
            end--;
        }

        if (start > end) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = start; i <= end; i++) {
            if (i > start) {
                sb.append(System.lineSeparator());
            }
            sb.append(lines[i]);
        }

        return sb.toString();
    }
}
