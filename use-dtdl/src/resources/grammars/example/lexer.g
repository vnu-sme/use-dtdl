lexer grammar ExprLexer;

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