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

    /**
         * Pick language string from a language map.
         * Prefer "en", otherwise take first string-valued entry.
         * Returns null if no suitable string found.
    */
    String pickLangString(java.util.Map<?,?> m) {
        if (m == null) return null;
        Object en = m.get("en");
        if (en instanceof String) return (String) en;
        for (java.util.Map.Entry<?,?> e : m.entrySet()) {
            if (e.getValue() instanceof String) return (String) e.getValue();
        }
        return null;
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
        else if ("displayName".equals(k)) {
            if (ov instanceof String) {
                iface.displayName = (String) ov;
            } else if (ov instanceof java.util.Map<?,?>) {
                iface.displayName = pickLangString((java.util.Map<?,?>) ov);
            }
        }
        else if ("description".equals(k)) {
            if (ov instanceof String) {
                iface.description = (String) ov;
            } else if (ov instanceof java.util.Map<?,?>) {
                iface.description = pickLangString((java.util.Map<?,?>) ov);
            }
        }
        else if (k.equals("extends")) {
            java.util.List<String> extList = new java.util.ArrayList<>();
            if (ov instanceof String s) {
                extList.add(s);
            } else if (ov instanceof java.util.List<?>) {
                for (Object it : (java.util.List<?>) ov) {
                    if (it instanceof String ss) extList.add(ss);
                }
            }
            iface.extendsInterfaces = extList;
            // backward
            iface.props.put("extends", ov);
        }
        else if (k.equals("@context")) {
            if (ov instanceof String) {
                iface.context = (String) ov;
            }
            else if (ov instanceof java.util.List<?>) {
                java.util.List<?> list = (java.util.List<?>) ov;
                if (!list.isEmpty() && list.get(0) instanceof String) {
                    iface.context = (String) list.get(0); // take first entry
                }
            }
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
      {
          String txt = $n.getText();
          if (txt.contains(".")) {
              $obj = Double.parseDouble(txt);
          } else {
              $obj = Integer.parseInt(txt);
          }
          $text = txt;
      }
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
        // Accept either a String or a List of Strings for @type (e.g. ["Property","Density"])
        if (m.containsKey("@type")) {
            Object typeField = m.get("@type");
            String primaryType = null;
            java.util.List<String> typeList = null;

            if (typeField instanceof String) {
                primaryType = (String) typeField;
            } else if (typeField instanceof java.util.List<?>) {
                typeList = new java.util.ArrayList<>();
                for (Object it : (java.util.List<?>) typeField) {
                    if (it instanceof String) typeList.add((String) it);
                }
                if (!typeList.isEmpty()) primaryType = typeList.get(0);
            }

            if (primaryType != null) {
                // Schema types we care about now
                if ("Enum".equals(primaryType) || "Object".equals(primaryType) || "Map".equals(primaryType)) {
                    ASTSchema s;
                    switch (primaryType) {
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
                else if ("Property".equals(primaryType) || "Telemetry".equals(primaryType)
                         || "Command".equals(primaryType) || "Relationship".equals(primaryType)
                         || "Component".equals(primaryType)) {

                    ASTContent c;
                    switch (primaryType) {
                        case "Property": c = new ASTProperty(); break;
                        case "Telemetry": c = new ASTTelemetry(); break;
                        case "Command": c = new ASTCommand(); break;
                        case "Relationship": c = new ASTRelationship(); break;
                        case "Component": c = new ASTComponent(); break;
                        default: c = new ASTContent() {}; break;
                    }

                    c.semanticType = primaryType;
                    c.props.putAll(m);

                    // if @type was an array, keep the extra semantic types as metadata
                    if (typeList != null && typeList.size() > 1) {
                        java.util.List<String> extras = new java.util.ArrayList<>(typeList.subList(1, typeList.size()));
                        c.props.put("semanticTypes", extras);             // e.g. ["Density", ...]
                        // convenience: also expose the first extra (if any) as 'semanticType' prop
                        c.props.put("semanticType", extras.get(0));
                    }

                    // also pick up 'name' into c.name (parity with genericPair handling)
                    Object nm = m.get("name");
                    if (nm instanceof String) c.name = (String) nm;

                    $obj = c;
                }
                else {
                    // unknown @type — keep as raw map (resolver can decide)
                    $obj = m;
                }
            } else {
                // couldn't derive a string primary type -> keep raw map
                $obj = m;
            }
        } else {
            // plain object without @type → return raw map
            $obj = m;
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
