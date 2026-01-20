package org.tzi.use.dtdl.parser;

import org.antlr.runtime.ANTLRInputStream;
import org.antlr.runtime.CommonTokenStream;
import org.antlr.runtime.RecognitionException;
import org.tzi.use.dtdl.ast.ASTInterface;
import org.tzi.use.dtdl.parser.DTDLLexer;
import org.tzi.use.dtdl.parser.DTDLParser;

import java.io.*;

public class DTDLCompiler {
    public static ASTInterface compileSpecification(String inName, PrintWriter err) {
        InputStream in;
        try {
            in = new FileInputStream(inName);
        } catch (FileNotFoundException e) {
            err.println("File not found: " + inName);
            return null;
        }

        ANTLRInputStream input;
        try {
            input = new ANTLRInputStream(in);
            input.name = inName;
        } catch (IOException e) {
            err.println(e.getMessage());
            return null;
        }

        DTDLLexer lexer = new DTDLLexer(input);
        CommonTokenStream tokens = new CommonTokenStream(lexer);
        DTDLParser parser = new DTDLParser(tokens);

        try {
            return parser.model();
        } catch (RecognitionException e) {
            err.println(
                    parser.getSourceName() + ":" +
                            e.line + ":" +
                            e.charPositionInLine + ": " +
                            e.getMessage()
            );
            return null;
        } finally {
            err.flush();
        }
    }
}
