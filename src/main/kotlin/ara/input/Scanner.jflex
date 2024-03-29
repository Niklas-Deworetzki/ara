package ara.input;

import java.util.List;
import java_cup.runtime.*;
import ara.input.*;
import ara.input.symbol.*;
import ara.position.InputSource;
import static ara.input.Sym.*;


%%

%class Scanner
%public
%final

%cup
%unicode

%char

%eofclose false
%eofval{
    // This needs to be specified when using a custom sym class name
    return Token.withId(EOF, yychar, yychar);
%eofval}

%{
    private Token token(int tokenId) {
        return Token.withId(tokenId, yychar, yychar + yylength());
    }

    private Token token(int tokenId, Object value) {
        return Token.withId(tokenId, yychar, yychar + yylength(), value);
    }

    private static String formatCharacter(char character) {
        if (Character.isISOControl(character)) {
            return String.format("\\%03X", (int) character);
        } else {
            return Character.toString(character);
        }
    }

    private List<String> getCommentLines() {
        return yytext().lines()
            .filter(line -> !line.isBlank())
            .map(line -> line.substring(1))
            .toList();
    }
%}

WhiteSpace  = \s+

LineComment = "//".*
HashComment = ("#".*[\n\r]*)+

Identifier  = [a-zA-Z_][a-zA-Z0-9_]*
Decimal     = [0-9]+

%%

{WhiteSpace}                         { /* Ignore whitespace */ }
{LineComment}                        { /* Ignore comments */ }
{HashComment}                        { if (yychar == 0) {
                                        return token(HASHCOMMENT, getCommentLines());
                                       } else {
                                        /* Ignore comments */
                                       }
                                     }

"routine"   { return token(ROUTINE); }
"type"      { return token(TYPE); }
"call"      { return token(CALL); }
"uncall"    { return token(UNCALL); }
"null"      { return token(NULL); }

":"         { return token(COLON); }
","         { return token(COMMA); }
"."         { return token(DOT); }
"="         { return token(EQ); }
":="        { return token(ASSIGNMENT); }
"&"         { return token(AMPERSAND); }
"&("        { return token(AMPERSAND_PAREN_L); }

"+"         { return token(OPERATOR_ADD); }
"-"         { return token(OPERATOR_SUB); }
"^"         { return token(OPERATOR_XOR); }
"*"         { return token(OPERATOR_MUL); }
"/"         { return token(OPERATOR_DIV); }
"%"         { return token(OPERATOR_MOD); }
"=="        { return token(OPERATOR_EQU); }
"!="        { return token(OPERATOR_NEQ); }
"<"         { return token(OPERATOR_LST); }
"<="        { return token(OPERATOR_LSE); }
">"         { return token(OPERATOR_GRT); }
">="        { return token(OPERATOR_GRE); }

"<-"        { return token(ARROW_L); }
"->"        { return token(ARROW_R); }
"("         { return token(PAREN_L); }
")"         { return token(PAREN_R); }
"{"         { return token(CURL_L); }
"}"         { return token(CURL_R); }

{Identifier}    { return token(IDENTIFIER, yytext()); }
{Decimal}       { try {
                    return token(INTEGER, Integer.parseInt(yytext()));
                  } catch (NumberFormatException exception) {
                    return token(UNKNOWN, "Decimal number " + yytext() + " exceeds valid integer range.");
                  }
                }

[^]     { // This rule matches any previously unmatched characters.
          char offendingCharacter = yytext().charAt(0);
          return token(UNKNOWN, "Unknown or unsupported character: " + formatCharacter(offendingCharacter));
        }
