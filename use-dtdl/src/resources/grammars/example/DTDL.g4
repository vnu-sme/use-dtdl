grammar DTDL;

options {
    language=Java;
}

@header {
package org.tzi.use.dtdl.parser;

import org.tzi.use.dtdl.DTDLModel.DTDLModel;
import org.tzi.use.uml.mm.MClass;
import org.tzi.use.parser.base.BaseParser;
import java.util.Map;
import java.util.TreeMap;
}
@lexer::header {
package org.tzi.use.dtdl.parser;
}

@members {
    private DTDLModel model;
    private MClass currentClass;

    public void setModel(DTDLModel m) {
        this.model = m;
    }
}

models
    : model+ EOF
    ;

model
    : 'class' name=ID ':' NL
      {
          currentClass = model.getBaseModel().getClass($name.text);
      }
      telemetrySection
    ;

telemetrySection
    : telemetryItem+
    ;

telemetryItem
    : name=ID ':' type=TYPE NL
      {
          if (model != null && currentClass != null) {
              Map<String,String> classTelemetry = model.getTelemetry().get(currentClass);
              if (classTelemetry == null) {
                  classTelemetry = new TreeMap<>();
                  model.getTelemetry().put(currentClass, classTelemetry);
              }
              classTelemetry.put($name.text, $type.text);
          }
      }
    ;

// Lexer rules
TYPE: 'string' | 'double' | 'int' ;
ID  : ('a'..'z'|'A'..'Z'|'_') ('a'..'z'|'A'..'Z'|'_'|'0'..'9')* ;
NL  : ('\r'? '\n')+ ;
WS  : (' '|'\t')+ { $channel=HIDDEN; } ;
