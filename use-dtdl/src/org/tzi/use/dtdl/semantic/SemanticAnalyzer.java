// File: org/tzi/use/dtdl/semantic/SemanticAnalyzer.java
package org.tzi.use.dtdl.semantic;

import org.tzi.use.dtdl.ast.ASTInterface;
import org.tzi.use.dtdl.DTDLModel.DTDLModel;

import java.util.List;

public interface SemanticAnalyzer {
    DTDLContext getContext();
    /**
     * Run the analyze pipeline (resolveAll first, then validate).
     * Returns built DTDLModel if no errors, otherwise returns null and errors are in context.
     */
    DTDLModel analyze(List<ASTInterface> astInterfaces);
}
