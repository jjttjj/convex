package convex.core.lang;

import convex.core.data.*;
import org.parboiled.Parboiled;
import org.parboiled.Rule;
import org.parboiled.annotations.BuildParseTree;
import org.parboiled.annotations.DontLabel;
import org.parboiled.annotations.MemoMismatches;
import org.parboiled.annotations.SuppressNode;
import org.parboiled.parserunners.ReportingParseRunner;
import org.parboiled.support.Var;

import java.util.ArrayList;

@BuildParseTree
public class ScryptNext extends Reader {

    // Use a ThreadLocal reader because instances are not thread safe
    private static final ThreadLocal<ScryptNext> syntaxReader = ThreadLocal.withInitial(() -> Parboiled.createParser(ScryptNext.class));
    public final Rule DEF = Keyword("def");
    public final Rule DEFN = Keyword("defn");
    public final Rule FN = Keyword("fn");
    public final Rule IF = Keyword("if");
    public final Rule ELSE = Keyword("else");
    public final Rule WHEN = Keyword("when");
    public final Rule DO = Keyword("do");

    final Rule EQU = Terminal("=", Ch('='));
    final Rule COMMA = Terminal(",");
    final Rule LPAR = Terminal("(");
    final Rule RPAR = Terminal(")");
    final Rule LWING = Terminal("{");
    final Rule RWING = Terminal("}");
    final Rule LBRK = Terminal("[");
    final Rule RBRK = Terminal("]");
    final Rule SEMI = Terminal(";");
    final Rule RIGHT_ARROW = Terminal("->");

    /**
     * Constructor for reader class. Called by Parboiled.createParser
     */
    public ScryptNext() {
        super(true);
    }

    /**
     * Parses an expression and returns a Syntax object
     *
     * @param source
     * @return Parsed form
     */
    @SuppressWarnings("rawtypes")
    public static Syntax readSyntax(String source) {
        ScryptNext scryptReader = syntaxReader.get();
        scryptReader.tempSource = source;

        var rule = scryptReader.CompilationUnit();
        var result = new ReportingParseRunner(rule).run(source);

        if (result.matched) {
            return (Syntax) result.resultValue;
        } else {
            throw new RuntimeException(rule.toString() + " failed to match " + source);
        }
    }

    // --------------------------------
    // COMPILATION UNIT
    // --------------------------------
    public Rule CompilationUnit() {
        return FirstOf(
                Sequence(
                        Spacing(),
                        FirstOf(
                                Sequence(Expression(), EOI),
                                Sequence(Statement(), EOI),
                                Sequence(
                                        ZeroOrMoreOf(Statement()),
                                        push(prepare(popNodeList().cons(Syntax.create(Symbols.DO)))), EOI
                                )
                        )
                ),
                push(error("Invalid program."))
        );
    }

    // --------------------------------
    // STATEMENT
    // --------------------------------
    public Rule Statement() {
        return FirstOf(
                IfElseStatement(),
                WhenStatement(),
                DefStatement(),
                DefnStatement(),
                BlockStatement(),
                EmptyStatement(),
                ExpressionStatement()
        );
    }

    // --------------------------------
    // IF ELSE STATEMENT
    // --------------------------------
    public Rule IfElseStatement() {
        return FirstOf(
                Sequence(
                        IF,
                        ParExpression(),
                        Statement(),
                        ELSE,
                        Statement(),
                        push(prepare(ifElseStatement()))
                ),
                Sequence(
                        IF,
                        ParExpression(),
                        Statement(),
                        push(prepare(ifStatement()))
                )
        );
    }

    @SuppressWarnings("rawtypes")
    public ASequence ifStatement() {
        // Pop expressions from if body
        var body = pop();

        // Pop test
        var test = pop();

        return Lists.of(
                Syntax.create(Symbols.COND),
                test,
                body
        );
    }

    @SuppressWarnings("rawtypes")
    public ASequence ifElseStatement() {
        // Pop expressions from else body
        var elseBody = pop();

        // Pop expressions from if body
        var ifBody = pop();

        // Pop test
        var test = (Syntax) pop();

        return Lists.of(
                Syntax.create(Symbols.COND),
                test,
                ifBody,
                elseBody
        );
    }

    // --------------------------------
    // WHEN STATEMENT
    // --------------------------------
    public Rule WhenStatement() {
        return Sequence(
                WHEN,
                ParExpression(),
                Statement(),
                push(prepare(whenStatement()))
        );
    }

    public AList<Syntax> whenStatement() {
        // Pop expressions from body
        Syntax body = (Syntax) pop();

        // Pop test
        Syntax test = (Syntax) pop();

        return Lists.of(
                Syntax.create(Symbols.COND),
                test,
                body
        );
    }

    // --------------------------------
    // DEF STATEMENT
    // --------------------------------
    public Rule DefStatement() {
        return Sequence(
                DEF,
                Symbol(),
                Spacing(),
                EQU,
                Expression(),
                SEMI,
                push(prepare(defStatement((Syntax) pop(), (Syntax) pop())))
        );
    }

    public List<Syntax> defStatement(Syntax expr, Syntax sym) {
        return (List<Syntax>) Lists.of(Syntax.create(Symbols.DEF), sym, expr);
    }

    // --------------------------------
    // DEFN STATEMENT
    // --------------------------------
    public Rule DefnStatement() {
        return Sequence(
                DEFN,
                Symbol(),
                WrapInParenthesis(ZeroOrMoreCommaSeparatedOf(Symbol())),
                WrapInCurlyBraces(ZeroOrMoreOf(Statement())),
                push(prepare(defnStatement()))
        );
    }

    public AList<Object> defnStatement() {
        var block = popNodeList();
        var parameters = Vectors.create(popNodeList());
        var name = pop();

        return Lists.of(
                Syntax.create(Symbols.DEF),
                name,
                Syntax.create(block
                        .cons(parameters)
                        .cons(Syntax.create(Symbols.FN))
                )
        );
    }

    // --------------------------------
    // BLOCK STATEMENT
    // --------------------------------
    public Rule BlockStatement() {
        return Sequence(
                WrapInCurlyBraces(ZeroOrMoreOf(Statement())),
                TestNot(SEMI),
                push(block(popNodeList()))
        );
    }

    public Object block(ASequence<Object> statements) {
        Object form;

        switch (statements.size()) {
            case 0:
                form = null;
                break;
            case 1:
                form = statements.get(0);
                break;
            default:
                form = statements.cons(Syntax.create(Symbols.DO));
        }

        return prepare(form);
    }

    // --------------------------------
    // EXPRESSION STATEMENT
    // --------------------------------
    public Rule ExpressionStatement() {
        return Sequence(Expression(), SEMI);
    }

    // --------------------------------
    // EMPTY STATEMENT
    // --------------------------------
    public Rule EmptyStatement() {
        return Sequence(SEMI, push(prepare(null)));
    }


    // ~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~


    // --------------------------------
    // EXPRESSION
    // --------------------------------
    public Rule Expression() {
        return Sequence(
                FirstOf(
                        DoExpression(),
                        CallableExpression(),
                        FnExpression(),
                        LambdaExpression(),
                        ParExpression(),
                        StringLiteral(),
                        NilLiteral(),
                        NumberLiteral(),
                        BooleanLiteral(),
                        Symbol(),
                        Keyword(),
                        VectorExpression(),
                        MapExpression(),
                        Set()
                ),
                Spacing(),
                ZeroOrMore(InfixExtension())
        );
    }

    // --------------------------------
    // DO EXPRESSION
    // --------------------------------
    public Rule DoExpression() {
        return Sequence(
                DO,
                WrapInCurlyBraces(ZeroOrMoreOf(Statement())),
                push(block(popNodeList()))
        );
    }

    // --------------------------------
    // CALLABLE EXPRESSION
    // --------------------------------
    public Rule Callable() {
        return FirstOf(
                FnExpression(),
                VectorExpression(),
                MapExpression(),
                Set()
        );
    }

    public Rule CallableExpression() {
        return Sequence(
                FirstOf(
                        Callable(),
                        Symbol()
                ),
                Spacing(),
                WrapInParenthesis(ZeroOrMoreCommaSeparatedOf(Expression())),
                push(prepare(callableExpression()))
        );
    }

    public ASequence<Object> callableExpression() {
        var args = popNodeList();
        var callableOrSym = pop();

        return Lists.create(args).cons(callableOrSym);
    }

    // --------------------------------
    // FUNCTION EXPRESSION
    // --------------------------------
    public Rule FnExpression() {
        return Sequence(
                FN,
                WrapInParenthesis(ZeroOrMoreCommaSeparatedOf(Symbol())),
                WrapInCurlyBraces(ZeroOrMoreOf(Statement())),
                push(fnExpression())
        );
    }

    public Syntax fnExpression() {
        var block = popNodeList();
        var parameters = Vectors.create(popNodeList());

        return Syntax.create(block.cons(parameters).cons(Syntax.create(Symbols.FN)));
    }

    // --------------------------------
    // LAMBDA EXPRESSION
    // --------------------------------
    public Rule LambdaExpression() {
        return Sequence(
                WrapInParenthesis(ZeroOrMoreCommaSeparatedOf(Symbol())),
                RIGHT_ARROW,
                Expression(),
                push(prepare(lambdaExpression()))
        );
    }

    public AList<Object> lambdaExpression() {
        var fn = Syntax.create(Symbols.FN);
        var body = pop();
        var args = Vectors.create(popNodeList());

        return Lists.of(fn, args, body);
    }

    // --------------------------------
    // INFIX
    // --------------------------------
    public Rule InfixExtension() {
        return Sequence(
                InfixOperator(),
                Expression(),
                push(prepare(infixExpression()))
        );
    }

    public AList<Object> infixExpression() {
        var operand2 = pop();
        var operator = pop();
        var operand1 = pop();

        return Lists.of(operator, operand1, operand2);
    }

    public Rule VectorExpression() {
        return Sequence(
                LBRK,
                ZeroOrMoreCommaSeparatedOf(Expression()),
                FirstOf(
                        RBRK,
                        Sequence(
                                FirstOf(RWING, RPAR, EOI),
                                push(error("Expected closing ']'"))
                        )
                ),
                Spacing(),
                push(prepare(Vectors.create(popNodeList()))));
    }

    public Rule MapExpression() {
        return Sequence(
                LWING,
                MapEntries(),
                RWING,
                Spacing(),
                // Create a Map from a List of MapEntry.
                // `MapEntries` builds up a list of MapEntry,
                // which we can get from `popNodeList`.
                push(prepare(Maps.create(popNodeList())))
        );
    }

    public Rule MapEntries() {
        Var<ArrayList<Object>> expVar = new Var<>(new ArrayList<>());

        return Sequence(
                Optional(
                        MapEntry(),
                        ListAddAction(expVar),
                        ZeroOrMore(
                                COMMA,
                                MapEntry(),
                                ListAddAction(expVar))),
                push(prepare(Lists.create(expVar.get()))));
    }

    public Rule MapEntry() {
        return Sequence(
                Expression(),
                Spacing(),
                Expression(),
                push(buildMapEntry((Syntax) pop(), (Syntax) pop()))
        );
    }

    public MapEntry<Syntax, Syntax> buildMapEntry(Syntax v, Syntax k) {
        return MapEntry.create(k, v);
    }

    public Rule InfixOperator() {
        return Sequence(
                FirstOf(
                        Sequence("+", push(Symbols.PLUS)),
                        Sequence("-", push(Symbols.MINUS)),
                        Sequence("*", push(Symbols.TIMES)),
                        Sequence("/", push(Symbols.DIVIDE)),
                        Sequence("==", push(Symbols.EQUALS)),
                        Sequence("==", push(Symbols.EQUALS)),
                        Sequence("<=", push(Symbols.LE)),
                        Sequence("<", push(Symbols.LT)),
                        Sequence(">=", push(Symbols.GE)),
                        Sequence(">", push(Symbols.GT))
                ),
                Spacing()
        );
    }

    public Rule HexDigit() {
        return FirstOf(CharRange('a', 'f'), CharRange('A', 'F'), CharRange('0', '9'));
    }

    Rule UnicodeEscape() {
        return Sequence(OneOrMore('u'), HexDigit(), HexDigit(), HexDigit(), HexDigit());
    }

    @MemoMismatches
    Rule LetterOrDigit() {
        return FirstOf(Sequence('\\', UnicodeEscape()), new ScryptLetterOrDigitMatcher());
    }

    @SuppressNode
    @DontLabel
    Rule Keyword(String keyword) {
        return Terminal(keyword, LetterOrDigit());
    }

    @SuppressNode
    @DontLabel
    Rule Terminal(String string) {
        return Sequence(string, Spacing()).label('\'' + string + '\'');
    }

    @SuppressNode
    @DontLabel
    Rule Terminal(String string, Rule mustNotFollow) {
        return Sequence(string, TestNot(mustNotFollow), Spacing()).label('\'' + string + '\'');
    }

    public Rule Spacing() {
        return ZeroOrMore(FirstOf(

                // whitespace
                OneOrMore(AnyOf(" \t\r\n\f").label("Whitespace")),

                // traditional comment
                Sequence("/*", ZeroOrMore(TestNot("*/"), ANY), "*/"),

                // end of line comment
                Sequence(
                        "//",
                        ZeroOrMore(TestNot(AnyOf("\r\n")), ANY),
                        FirstOf("\r\n", '\r', '\n', EOI)
                )
        ));
    }

    public Rule ParExpression() {
        return WrapInParenthesis(Expression());
    }

    public Rule WrapInParenthesis(Rule rule) {
        return Sequence(
                LPAR,
                rule,
                RPAR
        );
    }

    public Rule WrapInCurlyBraces(Rule rule) {
        return Sequence(
                LWING,
                rule,
                RWING
        );
    }

    public Rule ZeroOrMoreOf(Rule rule) {
        Var<ArrayList<Object>> expVar = new Var<>(new ArrayList<>());

        return Sequence(
                ZeroOrMore(
                        rule,
                        ListAddAction(expVar)
                ),
                push(prepare(Lists.create(expVar.get())))
        );
    }

    public Rule ZeroOrMoreCommaSeparatedOf(Rule rule) {
        Var<ArrayList<Object>> expVar = new Var<>(new ArrayList<>());

        return Sequence(
                Optional(
                        rule,
                        ListAddAction(expVar),
                        ZeroOrMore(
                                COMMA,
                                rule,
                                ListAddAction(expVar)
                        )
                ),
                push(prepare(Lists.create(expVar.get())))
        );
    }

}