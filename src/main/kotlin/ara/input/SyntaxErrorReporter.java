package ara.input;

import ara.input.symbol.Token;

import java.util.List;

public interface SyntaxErrorReporter {
    void reportSyntaxError(Token position, List<Integer> expectedTokenIds);
}
