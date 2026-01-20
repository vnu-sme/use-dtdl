grammar DTDL;

@header {
package org.tzi.use.dtdl.parser;

import org.tzi.use.dtdl.ast.*;
import org.tzi.use.dtdl.ast.schema.*;
}

@lexer::header {
package org.tzi.use.dtdl.parser;
}


@members {
    String stripQuotes(String s) {
        return s.substring(1, s.length() - 1);
    }
}

/* =======================
   ENTRY
======================= */

model returns [ASTInterface n]
    :
      LBRACE
        { $n = new ASTInterface(); }
        interfaceFields[$n]
      RBRACE
      EOF
    ;


/* =======================
   INTERFACE BODY
======================= */

interfaceFields[ASTInterface iface]
    :
      interfaceField[iface] (COMMA interfaceField[iface])*
    ;

interfaceField[ASTInterface iface]
    :
      key=STRING COLON ca=contentArray
      {
        if (stripQuotes($key.text).equals("contents")) {
            for (ASTContent c : $ca.list) {
                if (c instanceof ASTProperty) iface.addProperty((ASTProperty)c);
                else if (c instanceof ASTTelemetry) iface.addTelemetry((ASTTelemetry)c);
                else if (c instanceof ASTRelationship) iface.addRelationship((ASTRelationship)c);
                else if (c instanceof ASTCommand) iface.addCommand((ASTCommand)c);
                else if (c instanceof ASTComponent) iface.addComponent((ASTComponent)c);
            }
        }
      }
    | key=STRING COLON sa=schemaArray
        {
            if (stripQuotes($key.text).equals("schemas")) {
                for (ASTSchema s : $sa.list) {
                    iface.addSchema(s);
                }
            }
        }
    | key=STRING COLON v=value
      {
        String k = stripQuotes($key.text);
        if (k.equals("@id")) iface.id = $v.text;
        else if (k.equals("displayName")) iface.displayName = $v.text;
        else if (k.equals("description")) iface.description = $v.text;
        else {
            Object ov = ($v.obj != null) ? $v.obj : ($v.schema != null ? $v.schema : $v.text);
            iface.props.put(k, ov);
        }
      }

/* =======================
   TOP-LEVEL FIELDS
======================= */

contentArray returns [java.util.List<ASTContent> list]
    :
      LBRACKET
        c=contentObject
        {
          $list = new java.util.ArrayList<>();
          $list.add($c.n);
        }
        (COMMA c=contentObject { $list.add($c.n); })*
      RBRACKET
    ;

contentObject returns [ASTContent n]
    :
      LBRACE
        typePairContent
        {
          if ($typePairContent.t.equals("Telemetry")) {
              $n = new ASTTelemetry();
          } else if ($typePairContent.t.equals("Property")) {
              $n = new ASTProperty();
          } else if ($typePairContent.t.equals("Relationship")) {
              $n = new ASTRelationship();
          } else if ($typePairContent.t.equals("Command")) {
              $n = new ASTCommand();
          } else if ($typePairContent.t.equals("Component")) {
              $n = new ASTComponent();
          }
          $n.semanticType = $typePairContent.t;
        }
        genericPairs[$n]
      RBRACE
    ;


/* =======================
   OBJECT PLACEHOLDERS
======================= */


typePairContent returns [String t]
    :
      key=STRING COLON val=STRING
      {
        if (!stripQuotes($key.text).equals("@type")) {
            throw new RuntimeException("Expected @type");
        }
        $t = stripQuotes($val.text);
      }
    ;

genericPairs[ASTContent n]
    :
      genericPair[n] (COMMA genericPair[n])*
    ;

genericPair[ASTContent n]
    :
      keyTok=STRING COLON value
      {
        String key = stripQuotes($keyTok.text);

        Object valObj = null;
        if ($value.obj != null) valObj = $value.obj;
        else if ($value.schema != null) valObj = $value.schema;
        else if ($value.text != null) valObj = $value.text;

        n.props.put(key, valObj);

        if ("name".equals(key) && valObj instanceof String) {
            n.name = (String)valObj;
        }
      }
    ;

/* =======================
   VALUES
======================= */

value returns [ASTSchema schema, String text, Object obj]
    : s=STRING
      {
        $text = stripQuotes($s.text);
        $obj = $text; // primitive string by default
      }
    | o=objectSchema
      {
        $schema = $o.schema;
        $obj = $schema;
      }
    | a=arrayValue
      {
        $obj = $a.list; // a.list is java.util.List<Object> containing items
      }
    ;


arrayValue returns [java.util.List<Object> list]
@init {
    $list = new java.util.ArrayList<Object>();
}
    : LBRACKET
        ai=arrayItem
        {
          if ($ai.ev != null) $list.add($ai.ev);
          else if ($ai.f != null) $list.add($ai.f);
          else if ($ai.p != null) $list.add($ai.p);
          else if ($ai.obj != null) $list.add($ai.obj);
        }
        (COMMA ai=arrayItem {
          if ($ai.ev != null) $list.add($ai.ev);
          else if ($ai.f != null) $list.add($ai.f);
          else if ($ai.p != null) $list.add($ai.p);
          else if ($ai.obj != null) $list.add($ai.obj);
        })*
      RBRACKET
    ;

arrayItem returns [ASTEnumValue ev, ASTField f, ASTProperty p, Object obj]
    : e=enumValue      { $ev = $e.v; }
    | fo=fieldObject   { $f  = $fo.f; }
    | co=contentObject {
          if ($co.n instanceof ASTProperty) $p = (ASTProperty)$co.n;
          else $obj = $co.n;
      }
    | s=STRING { $obj = stripQuotes($s.text); }
    ;

enumValue returns [ASTEnumValue v]
    : LBRACE
        nKey=STRING COLON nVal=STRING
        COMMA vKey=STRING COLON vVal=STRING
      RBRACE
      {
        $v = new ASTEnumValue();
        $v.name = stripQuotes($nVal.text);
        $v.value = new ASTEnumLiteral(stripQuotes($vVal.text));
      }
    ;

fieldObject returns [ASTField f]
    : LBRACE
        STRING COLON STRING
        COMMA STRING COLON value
      RBRACE
      {
        $f = new ASTField();
        $f.name = stripQuotes($STRING.text);
        $f.schema = $value.schema;
      }
    ;


/* =======================
   OBJECTS
======================= */

objectSchema returns [ASTSchema schema]
    : LBRACE
        typePairSchema
        {
          switch ($typePairSchema.t) {
            case "Enum":   $schema = new ASTEnum(); break;
            case "Object": $schema = new ASTObject(); break;
            case "Map":    $schema = new ASTMap(); break;
            default: throw new RuntimeException("Unknown schema type");
          }
        }
        schemaPairs[$schema]
      RBRACE
    ;

/* =======================
   SCHEMAS
======================= */

schemaArray returns [java.util.List<ASTSchema> list]
    : LBRACKET
        s=schemaObject
        {
          $list = new java.util.ArrayList<>();
          $list.add($s.n);
        }
        (COMMA s=schemaObject { $list.add($s.n); })*
      RBRACKET
    ;

schemaObject returns [ASTSchema n]
    : LBRACE
        typePairSchema
        {
          switch ($typePairSchema.t) {
            case "Enum":    $n = new ASTEnum(); break;
            case "Object":  $n = new ASTObject(); break;
            case "Map":     $n = new ASTMap(); break;
            default: throw new RuntimeException("Unknown schema type");
          }
          $n.type = $typePairSchema.t;
        }
        schemaPairs[$n]
      RBRACE
    ;

typePairSchema returns [String t]
    : STRING COLON STRING
      {
        if (!stripQuotes($STRING.text).equals("@type"))
            throw new RuntimeException("Expected @type");
        $t = stripQuotes($STRING_1.text);
      }
    ;

schemaPairs[ASTSchema s]
    : schemaPair[s] (COMMA schemaPair[s])*
    ;

schemaPair[ASTSchema s]
    : STRING COLON value
      {
        String k = stripQuotes($STRING.text);

        if (s instanceof ASTEnum && k.equals("enumValues")) {
            ((ASTEnum)s).enumValues = $value.enumValues;
        }
        else if (s instanceof ASTObject && k.equals("fields")) {
            ((ASTObject)s).fields = $value.fields;
        }
        else if (s instanceof ASTMap && k.equals("mapKey")) {
            ((ASTMap)s).mapKey = (ASTMapKey)$value.schema;
        }
        else if (s instanceof ASTMap && k.equals("mapValue")) {
            ((ASTMap)s).mapValue = (ASTMapValue)$value.schema;
        }
      }
    ;

/* =======================
   LEXER
======================= */

LBRACE  : '{' ;
RBRACE  : '}' ;
LBRACKET: '[' ;
RBRACKET: ']' ;
COMMA   : ',' ;

BOOLEAN : 'true' | 'false' ;
NUMBER  : '-'? ('0'..'9')+ ('.' ('0'..'9')+)? ;

/* dùng trong parser */
STRING : '"' ( '\\' . | ~('\\'|'"') )* '"' ;
COLON  : ':' ;

WS
    : (' ' | '\t' | '\r' | '\n')+ {skip();}
    ;









