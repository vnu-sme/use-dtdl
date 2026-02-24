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
            // Wrap with PushbackInputStream so we can consume a UTF-8 BOM if present,
            // otherwise push the bytes back so the lexer sees the original stream.
            PushbackInputStream pin = new PushbackInputStream(in, 3);
            byte[] probe = new byte[3];
            int n = pin.read(probe);
            if (n == 3 &&
                    (probe[0] & 0xFF) == 0xEF &&
                    (probe[1] & 0xFF) == 0xBB &&
                    (probe[2] & 0xFF) == 0xBF) {

                err.println("[DTDLCompiler] UTF-8 BOM detected and skipped.");
            } else {
                if (n > 0) pin.unread(probe, 0, n);
                err.println("[DTDLCompiler] No UTF-8 BOM detected.");
            }

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
