parser grammar ExprParser;
options { tokenVocab=ExprLexer; }

/* =======================
   ENTRY
======================= */

model
    : LBRACE interfaceBody RBRACE EOF
    ;

/* =======================
   INTERFACE BODY
======================= */

interfaceBody
    : pair (COMMA pair)*
    ;

pair
    : contextPair
    | idPair
    | typePair
    | displayNamePair
    | descriptionPair
    | schemasPair
    | contentsPair
    ;

/* =======================
   TOP-LEVEL FIELDS
======================= */

contextPair
    : STRING_COLON STRING
    ;

idPair
    : STRING_COLON STRING
    ;

typePair
    : STRING_COLON STRING
    ;

displayNamePair
    : STRING_COLON STRING
    ;

descriptionPair
    : STRING_COLON STRING
    ;

/* =======================
   ARRAYS
======================= */

schemasPair
    : STRING_COLON LBRACKET schemaObject (COMMA schemaObject)* RBRACKET
    ;

contentsPair
    : STRING_COLON LBRACKET contentObject (COMMA contentObject)* RBRACKET
    ;

/* =======================
   OBJECT PLACEHOLDERS
======================= */

schemaObject
    : LBRACE genericPairs? RBRACE
    ;

contentObject
    : LBRACE genericPairs? RBRACE
    ;

genericPairs
    : genericPair (COMMA genericPair)*
    ;

genericPair
    : STRING_COLON value
    ;

/* =======================
   VALUES
======================= */

value
    : STRING
    | NUMBER
    | BOOLEAN
    | object
    | array
    ;

object
    : LBRACE genericPairs? RBRACE
    ;

array
    : LBRACKET value (COMMA value)* RBRACKET
    ;



LBRACE  : '{' ;
RBRACE  : '}' ;
LBRACKET: '[' ;
RBRACKET: ']' ;
COMMA   : ',' ;
STRING_COLON : STRING ':' ;

BOOLEAN : 'true' | 'false' ;
NUMBER  : '-'? INT ('.' INT)? ;
fragment INT : [0-9]+ ;

STRING
    : '"' (~["\\] | '\\' .)* '"'
    ;

WS : [ \t\r\n]+ -> skip ;











