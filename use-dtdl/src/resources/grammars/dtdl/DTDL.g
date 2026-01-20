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
      key=STRING COLON v=value
      {
        String k = stripQuotes($key.text);

        Object ov =
            ($v.schema != null) ? $v.schema :
            ($v.obj != null)    ? $v.obj :
                                  $v.text;

        iface.props.put(k, ov);

        if (k.equals("@id")) iface.id = (String)ov;
        else if (k.equals("displayName")) iface.displayName = (String)ov;
        else if (k.equals("description")) iface.description = (String)ov;
      }
    ;

contentObject returns [ASTContent n]
@init { $n = null; }
    :
      LBRACE
        (
          typePairContent
          {
            switch ($typePairContent.t) {
              case "Telemetry":    $n = new ASTTelemetry(); break;
              case "Property":     $n = new ASTProperty(); break;
              case "Relationship": $n = new ASTRelationship(); break;
              case "Command":      $n = new ASTCommand(); break;
              case "Component":    $n = new ASTComponent(); break;
              default:             $n = new ASTContent() {}; // payload, request, response
            }
            $n.semanticType = $typePairContent.t;
          }
        )?
        {
          if ($n == null) $n = new ASTContent() {};
        }
        genericPairs[$n]?
      RBRACE
    ;

/* =======================
   OBJECT PLACEHOLDERS
=======================*/

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
      { $text = stripQuotes($s.text); $obj = $text; }
    | b=BOOLEAN
      { $text = $b.getText(); $obj = Boolean.parseBoolean($text); }
    | n=NUMBER
      { $text = $n.getText(); $obj = $text; }
    | a=arrayValue
      { $obj = $a.list; }
    | jsonObjectValue
      {
        if ($jsonObjectValue.schema != null) {
            $schema = $jsonObjectValue.schema;
            $obj = $schema;
        } else {
            $obj = $jsonObjectValue.obj;
        }
      }
    ;

/* arrayValue updated to only use arrayItem.obj (no ev/f/p references) */
arrayValue returns [java.util.List<Object> list]
@init {
    $list = new java.util.ArrayList<Object>();
}
    : LBRACKET
        ai=arrayItem
        { $list.add($ai.obj); }
        (COMMA ai=arrayItem { $list.add($ai.obj); })*
      RBRACKET
    ;

arrayItem returns [Object obj]
    : value { $obj = $value.obj != null ? $value.obj : $value.schema; }
    ;

/* =======================
   OBJECTS (fixed: jsonObjectValue now expects braces)
======================= */

// parse an object into a Java map of key -> value (value already converted by 'value' rule)
objectEntries returns [java.util.Map<String,Object> map]
@init {
    $map = new java.util.LinkedHashMap<String,Object>();
}
    : firstKey=STRING COLON firstVal=value
      {
        String k = stripQuotes($firstKey.text);
        Object v = $firstVal.obj != null ? $firstVal.obj : ($firstVal.schema != null ? $firstVal.schema : $firstVal.text);
        $map.put(k, v);
      }
      ( COMMA k2=STRING COLON v2=value
        {
          String kk = stripQuotes($k2.text);
          Object vv = $v2.obj != null ? $v2.obj : ($v2.schema != null ? $v2.schema : $v2.text);
          $map.put(kk, vv);
        }
      )*
    ;

// Decide semantics from the parsed map to avoid LL(*) ambiguity in alternatives beginning with STRING COLON ...
jsonObjectValue returns [ASTSchema schema, Object obj]
    // two alternatives: 1) empty object 2) object with entries (so $o exists)
    : LBRACE RBRACE
      {
        // empty object -> return empty map
        java.util.Map<String,Object> m0 = new java.util.LinkedHashMap<String,Object>();
        $obj = m0;
      }
    | LBRACE o=objectEntries RBRACE
      {
        java.util.Map<String,Object> m = $o.map;

        // If there is an @type, inspect it to decide whether this is a schema object or a DTDL content object.
        if (m.containsKey("@type") && m.get("@type") instanceof String) {
            String t = (String) m.get("@type");

            // Schema types we care about now
            if ("Enum".equals(t) || "Object".equals(t) || "Map".equals(t)) {
                ASTSchema s;
                switch (t) {
                    case "Enum": s = new ASTEnum(); break;
                    case "Object": s = new ASTObject(); break;
                    case "Map": s = new ASTMap(); break;
                    default: s = null; break;
                }
                if (s != null) {
                    // store all raw props for resolver to interpret later (fields, enumValues, mapKey/mapValue...)
                    s.props.putAll(m);
                    $schema = s;
                    $obj = s;
                } else {
                    // fallback to raw map
                    $obj = m;
                }
            }
            // DTDL "content" objects (Property, Telemetry, Command, Relationship, Component)
            else if ("Property".equals(t) || "Telemetry".equals(t) || "Command".equals(t)
                     || "Relationship".equals(t) || "Component".equals(t)) {
                ASTContent c;
                switch (t) {
                    case "Property": c = new ASTProperty(); break;
                    case "Telemetry": c = new ASTTelemetry(); break;
                    case "Command": c = new ASTCommand(); break;
                    case "Relationship": c = new ASTRelationship(); break;
                    case "Component": c = new ASTComponent(); break;
                    default: c = new ASTContent() {}; break;
                }
                c.semanticType = t;
                // copy parsed map into props (values already converted by 'value' rule)
                c.props.putAll(m);
                // also set name if present to keep parity with genericPair handling
                Object nm = m.get("name");
                if (nm instanceof String) c.name = (String) nm;

                $obj = c;
            }
            else {
                // unknown @type — keep as raw map (resolver can decide)
                $obj = m;
            }
        } else {
            // plain object without @type → return raw map
            $obj = m;
        }
      }
    ;


/* =======================
   SCHEMA PAIRS
======================= */

typePairSchema returns [String t]
    : key=STRING COLON val=STRING
      {
        if (!stripQuotes($key.text).equals("@type"))
            throw new RuntimeException("Expected @type");
        $t = stripQuotes($val.text);
      }
    ;

schemaPairs[ASTSchema s]
    : schemaPair[s] (COMMA schemaPair[s])*
    ;

schemaPair[ASTSchema s]
    : STRING COLON value
      {
        String k = stripQuotes($STRING.text);

        // enumValues: value.obj should be a List of ASTEnumValue (or raw list to be converted by resolver)
        if (s instanceof ASTEnum && k.equals("enumValues")) {
            if ($value.obj instanceof java.util.List<?>) {
                ((ASTEnum)s).enumValues = (java.util.List<ASTEnumValue>) $value.obj;
            }
        }
        // fields: value.obj should be a List of ASTField (or raw list to be converted by resolver)
        else if (s instanceof ASTObject && k.equals("fields")) {
            if ($value.obj instanceof java.util.List<?>) {
                ((ASTObject)s).fields = (java.util.List<ASTField>) $value.obj;
            }
        }
        // mapKey / mapValue: store raw object list or object for resolver later
        else if (s instanceof ASTMap && (k.equals("mapKey") || k.equals("mapValue"))) {
            // store the raw parsed object in a map on the schema for later resolution
            // (requires ASTSchema to have a 'props' Map<String,Object>)
            s.props.put(k, $value.obj);
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
