package com.jmpl.j_jmpl;

import java.util.ArrayList;
import java.util.List;

/**
 * Parser class for j-jmpl. Takes in a list of tokens and generates the AST or detects errors and notifies the user.
 * Follows Recursive Descent Parsing.
 * <p>
 * Follows the precedence (highest to lowest):
 * <ul>
 * <li>Primary: true, false, null, literals, parentheses
 * <li>Function Call: f()
 * <li>Unary: ¬, -
 * <li>Exponent: ^
 * <li>Factor: /, *
 * <li>Term: -, +
 * <li>Comparison: >, >=, <, <=
 * <li>Equality: ==, ¬=
 * <li>And: ∧, and
 * <li>Or: ∨, or
 * <li>Sequence Operation: ∑
 * <li>Assignment: :=
 * </ul>
 * To Do: add my other operators.
 * 
 * @author Joel Luckett
 * @version 0.1
 */
class Parser {
    private static class ParseError extends RuntimeException {}

    private final List<Token> tokens;
    /** Pointer to the next token to be parsed. */
    private int current = 0;

    Parser(List<Token> tokens) {
        this.tokens = tokens;
    }

    /**
     * Initial method to start the parser.
     * 
     * @return a list of {@link Stmt} statements
     */
    List<Stmt> parse() {
        List<Stmt> statements = new ArrayList<>();
        
        while(!isAtEnd()) {
            // Create a list of statements to be evaluated
            statements.add(declaration());
        }

        return statements;
    }

    /**
     * Starts the parser's expression precedence chain from the bottom.
     * 
     * @return an {@link Expr} expression
     */
    private Expr expression() {
        return assignment();
    }

    /**
     * Declare a new statement.
     * 
     * @return a {@link Stmt} statement
     */
    private Stmt declaration() {
        try {
            if(match(TokenType.LET)) return variableDeclaration();

            return statement();
        } catch (ParseError e) {
            synchronise();
            return null;
        }
    }

    /**
     * Parses a statement based on its type.
     * 
     * @return a {@link Stmt} statement
     */
    private Stmt statement() {
        if(match(TokenType.FUNCTION)) return function("function");
        if(match(TokenType.IF)) return ifStatement();
        if(match(TokenType.OUT)) return outputStatement();
        if(match(TokenType.RETURN)) return returnStatement();
        if(match(TokenType.WHILE)) return whileStatement();
        if(match(TokenType.LEFT_PAREN)) return new Stmt.Block(block());

        return expressionStatement();
    }

    /**
     * Parses an if statement based on a conditional expression.
     * 
     * @return an if statement
     */
    private Stmt ifStatement() {
        // Get the condition expression
        Expr condition = expression();
        
        // Get the then statement after a 'then' token
        consume(TokenType.THEN, ErrorType.SYNTAX, "Expected 'then' after an if condition");
        Stmt thenBranch = statement();

        // Get the else statement if there's an 'else' token
        Stmt elseBranch = null;
        if(match(TokenType.ELSE)) {
            elseBranch = statement();
        }

        return new Stmt.If(condition, thenBranch, elseBranch);
    }

    /** 
     * Parses a statement that outputs to the console (identified by the OUT token)
     * 
     * @return a statement that performs an output
     */
    private Stmt outputStatement() {
        Expr value = expression();
        consumeSemicolon();
        return new Stmt.Output(value);
    }

    /** 
     * Parses a statement that returns values in a function.
     * 
     * @return a statement that returns a value in a function
     */
    private Stmt returnStatement() {
        Token keyword = previous();

        // Return null unless specified
        Expr value = null;

        // If there is no semicolon after the 'return' keyword, parse the expression
        if(!check(TokenType.SEMICOLON)) {
            value = expression();
        }

        consumeSemicolon();
        return new Stmt.Return(keyword, value);
    }

    /**
     * Parses a statement that declares a new variable (identified by the LET token).
     * 
     * @return a statement that declares a new variable
     */
    private Stmt variableDeclaration() {
        Token name = consume(TokenType.IDENTIFIER, ErrorType.SYNTAX, "Expected variable name");

        Expr initialiser = null;

        // If there is no '=', initial value remains null
        if(match(TokenType.EQUAL)) {
            initialiser = expression();
        }

        consumeSemicolon();
        return new Stmt.Let(name, initialiser);
    }

    /**
     * Parses a while statement based on a conditional expression.
     * 
     * @return a while statement
     */
    private Stmt whileStatement() {
        Expr condition = expression();
        
        // The statement starts after the do token
        consume(TokenType.DO, ErrorType.SYNTAX, "Expected 'do' after condition");
        Stmt body = statement();

        return new Stmt.While(condition, body);
    }

    /** 
     * Parse a statement that is an expression.
     * 
     * @return a statement that is an expression
     */
    private Stmt expressionStatement() {
        Expr expr = expression();
        consumeSemicolon();
        return new Stmt.Expression(expr);
    }

    /**
     * Parse a statement that is a function.
     * 
     * @param type the type of function
     * @return     a statement that declares a function
     */
    private Stmt.Function function(String type) {
        Token name = consume(TokenType.IDENTIFIER, ErrorType.FUNCTION, "Expected " + type + " name");
        consume(TokenType.LEFT_PAREN, ErrorType.SYNTAX, "Expected '(' after " + type + " name");

        List<Token> parameters = new ArrayList<>();
        if(!check(TokenType.RIGHT_PAREN)) {
            do {
                if(parameters.size() >= 255) {
                    error(peek(), ErrorType.ARGUMENT, "Can't have more than 255 parameters");
                }

                parameters.add(consume(TokenType.IDENTIFIER, ErrorType.PARAMETER, "Expected parameter name"));
            } while(match(TokenType.COMMA));
        }

        consume(TokenType.RIGHT_PAREN, ErrorType.SYNTAX, "Expected ')' after parameters");
        consume(TokenType.EQUAL, ErrorType.SYNTAX, "Expected '=' before function body");

        // Parse the body
        Stmt body = statement();
        return new Stmt.Function(name, parameters, body);
    }

    /**
     * Parses a new block of statements to be treated as a new environment.
     * 
     * @return a list of statements that form the block
     */
    private List<Stmt> block() {
        List<Stmt> statements = new ArrayList<>();

        // Until a ')' is found to close of the block, add the statements inside
        while(!check(TokenType.RIGHT_PAREN) && !isAtEnd()) {
            statements.add(declaration());
        }

        consume(TokenType.RIGHT_PAREN, ErrorType.SYNTAX, "Expected ')' after block");
        return statements;
    }

    /**
     * Assign a value to an already stored variable.
     * 
     * @return an abstract syntax tree for assignment operations
     */
    private Expr assignment() {
        Expr expr = summation();

        if(match(TokenType.ASSIGN)) {
            Token equals = previous();
            Expr value = assignment();

            // Store a new variable
            if(expr instanceof Expr.Variable) {
                Token name = ((Expr.Variable)expr).name;
                return new Expr.Assign(name, value);
            }

            error(equals, ErrorType.SYNTAX, "Invalid assignment target");
        }

        return expr;
    }

    /**
     * Parses a summation as an expression. It has the form: sum (n; m) i;
     * Where n, and i are expressions (not assignments) and m is a declaration or assignment
     * 
     * @return an abstract syntax tree for a summation operation
     */
    private Expr summation() {
        // If a summation symbol is at the start of the expression
        if(match(TokenType.SUMMATION)) {
            Token sum = previous();

            // Requires parentheses
            consume(TokenType.LEFT_PAREN, ErrorType.SYNTAX, "Expected '('");

            // Get the upperbound expression (n)
            Expr upperBound = summation();
            
            consume(TokenType.COMMA, ErrorType.SYNTAX, "Expected ',' after upper bound expression");

            // Get the lowerbound statement (m)
            Stmt lowerBound;
            if(match(TokenType.LET)) {
                // When the lowerbound is in the form 'let m = x;'
                Token name = consume(TokenType.IDENTIFIER, ErrorType.VARIABLE, "Expected variable name");

                Expr initialiser = null;

                // If there is no '=', initial value remains null
                if(match(TokenType.EQUAL)) {
                    initialiser = expression();
                } else {
                    throw error(peek(), ErrorType.VARIABLE, "Variable must be initialised");
                }

                lowerBound = new Stmt.Let(name, initialiser);
            } else {
                // When the lowerbound is in the form 'm := 0'
                Expr lowerExpr = assignment();
                if(lowerExpr instanceof Expr.Assign) {
                    lowerBound = new Stmt.Expression(lowerExpr);   
                } else {
                    throw error(peek(), ErrorType.SYNTAX, "Lower bound must be declaration or assignment");
                }
            }

            // Close the parentheses
            consume(TokenType.RIGHT_PAREN, ErrorType.SYNTAX, "Expected ')'");

            // Get the summand expression statement - this will add to create the sum
            Expr summand = expression();

            return new Expr.SequenceOp(sum, upperBound, lowerBound, summand);
        }

        // If not, skip this function
        return or();
    }

    /**
     * Checks if there are a series of or expressions to be parsed.
     * 
     * @return a Logical expression abstract syntax tree node for an or expression
     */
    private Expr or() {
        // Parse the left hand operand
        Expr expr = and();

        while(match(TokenType.OR)) {
            Token operator = previous();

            // Parse the right hand operand
            Expr right = and();

            // Create new Logical operator syntax tree node
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    /**
     * Checks if there are a series of and expressions to be parsed.
     * 
     * @return a Logical expression abstract syntax tree node for an and expression
     */
    private Expr and() {
        // Parse the left hand operand
        Expr expr = equality();

        while(match(TokenType.AND)) {
            Token operator = previous();

            // Parse the right hand operand
            Expr right = equality();

            // Create new Binary operator syntax tree node
            expr = new Expr.Logical(expr, operator, right);
        }

        return expr;
    }

    /**
     * Checks if the current token is an equality operation to be parsed.
     * 
     * @return a Binary expression abstract syntax tree node for equality operations
     */
    private Expr equality() {
        // Parse the left hand operand
        Expr expr = comparison();

        // Check if the current token is an equality operator
        while(match(TokenType.NOT_EQUAL, TokenType.EQUAL_EQUAL)) {
            Token operator = previous();

            // Parse the right hand operand
            Expr right = comparison();

            // Create new Binary operator syntax tree node
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * Checks if the current token is a comparison operation to be parsed.
     * 
     * @return a Binary expression abstract syntax tree node for comparison operations
     */
    private Expr comparison() {
        // Parse the left hand operand
        Expr expr = term();

        while(match(TokenType.GREATER, TokenType.GREATER_EQUAL, TokenType.LESS, TokenType.LESS_EQUAL)) {
            Token operator = previous();

            // Parse the right hand operand
            Expr right = term();

            // Create new Binary operator syntax tree node
            expr = new Expr.Binary(expr, operator, right);
        }

        return expr;
    }

    /**
     * Checks if the current token is a term (addition and subtraction) operation to be parsed.
     * 
     * @return a Binary expression abstract syntax tree node for term operations
     */
    private Expr term() {
        // Parse the left hand operand
        Expr expr = factor();
    
        while (match(TokenType.MINUS, TokenType.PLUS)) {
            Token operator = previous();

            // Parse the right hand operand
            Expr right = factor();

            // Create new Binary operator syntax tree node
            expr = new Expr.Binary(expr, operator, right);
        }
    
        return expr;
    }

    /**
     * Checks if the current token is a factor (multiplication and division) operation to be parsed.
     * 
     * @return a Binary expression abstract syntax tree node for factor operations
     */
    private Expr factor() {
        // Parse the left hand operand
        Expr expr = exponent();
    
        while (match(TokenType.SLASH, TokenType.ASTERISK)) {
            Token operator = previous();

            // Parse the right hand operand
            Expr right = exponent();

            // Create new Binary operator syntax tree node
            expr = new Expr.Binary(expr, operator, right);
        }
    
        return expr;
    }

    /**
     * Checks if the current token is a exponent operation to be parsed.
     * 
     * @return a Binary expression abstract syntax tree node for factor operations
     */
    private Expr exponent() {
        // Parse the left hand operand
        Expr expr = unary();
    
        while (match(TokenType.CARET)) {
            Token operator = previous();

            // Parse the right hand operand
            Expr right = unary();

            // Create new Binary operator syntax tree node
            expr = new Expr.Binary(expr, operator, right);
        }
    
        return expr;
    }

    /**
     * Checks if the current token is a unary (not and negation) operation to be parsed.
     * It is the top of the operation precedence level chain (before primary statements).
     * 
     * @return a Unary expression abstract syntax tree node for unary operations
     */
    private Expr unary() {
        if (match(TokenType.NOT, TokenType.MINUS)) {
            Token operator = previous();

            // Recursive call to parse the operand
            Expr right = unary();

            // Create new Unary operator syntax tree node
            return new Expr.Unary(operator, right);
        }
    
        return call();
    }

    private Expr finishCall(Expr callee) {
        List<Expr> arguments = new ArrayList<>();

        if(!check(TokenType.RIGHT_PAREN)) {
            // Add argument expressions seperated by commas until a right paranthese is found
            do {
                if(arguments.size() >= 255) error(peek(), ErrorType.ARGUMENT, "Function can't have more than 255 arguments");

                arguments.add(expression());
            } while(match(TokenType.COMMA));
        }

        Token paren = consume(TokenType.RIGHT_PAREN, ErrorType.SYNTAX, "Expected ')' after arguments");

        return new Expr.Call(callee, paren, arguments);
    }
    
    /**
     * Evaluate a function call expression.
     * 
     * @return a Call expression abstract syntax tree node for function calls
     */
    private Expr call() {
        Expr expr = primary();

        while(true) {
            if(match(TokenType.LEFT_PAREN)) {
                expr = finishCall(expr);
            } else {
                break;
            }
        }

        return expr;
    }

    /**
     * Evaluates primary expressions (literals, boolean values, etc.).
     * 
     * @return an expression abstract syntax tree node for primary operations
     */
    private Expr primary() {
        if (match(TokenType.FALSE)) return new Expr.Literal(false);
        if (match(TokenType.TRUE)) return new Expr.Literal(true);
        if (match(TokenType.NULL)) return new Expr.Literal(null);
    
        if (match(TokenType.NUMBER, TokenType.STRING)) {
            return new Expr.Literal(previous().literal);
        }

        if(match(TokenType.IDENTIFIER)) {
            return new Expr.Variable(previous());
        }
    
        if (match(TokenType.LEFT_PAREN)) {
            Expr expr = expression();

            // Call an error if a corresponding right parentheses is not found
            consume(TokenType.RIGHT_PAREN, ErrorType.SYNTAX, "Expected ')' after expression");
            return new Expr.Grouping(expr);
        }

        // Throw an error if unexpected token
        throw error(peek(), ErrorType.SYNTAX, "Expression expected");
    }

    /**
     * Checks to see if the current token is any of the given types.
     * 
     * @param types a list of {@link TokenType}s to check against
     * @return      if the current token matches to one of the given types
     */
    private boolean match(TokenType... types) {
        for(TokenType type : types) {
            if(check(type)) {
                advance(); // Consume token
                return true;
            }
        }

        return false;
    }

    /**
     * Checks a token against a desired type which is consumed if it is a match. If not, it throws an error.
     * Message does not need a full stop at the end.
     * 
     * @param type    the expected token to consume
     * @param message the potential error message if the token is not found
     * @return        the current token if it is the desired token
     * @throws error  signified by the message parameter, thrown if the desired token is not found
     */
    private Token consume(TokenType type, ErrorType errorType, String message) {
        if(check(type)) return advance();

        throw error(peek(), ErrorType.SYNTAX, message);
    }

    /**
     * Shorthand way to consume a semicolon and report potential errors.
     */
    private void consumeSemicolon() {
        consume(TokenType.SEMICOLON, ErrorType.SYNTAX, "Expected ';' after value");
    }

    /**
     * Returns true if the current token is of a given type.
     * 
     * @param type the type to check
     * @return     whether current token matches the given type
     */
    private boolean check(TokenType type) {
        if(isAtEnd()) return false;
        return peek().type == type;
    }

    /**
     * Consume the current token and returns it. Increments the current token pointer.
     * 
     * @return the current token
     */
    private Token advance() {
        if(!isAtEnd()) current++;
        return previous();
    }

    /**
     * Checks if we are at the end of the token list by comparing the current token to the EOF token.
     * 
     * @return if we are at the end of the token list
     */
    private boolean isAtEnd() {
        return peek().type == TokenType.EOF;
    }

    /**
     * Gets the current token.
     * 
     * @return the token at the location of the current pointer
     */
    private Token peek() {
        return tokens.get(current);
    }
    
    /**
     * Gets the previous token.
     * 
     * @return the token at the location of the current pointer - 1
     */
    private Token previous() {
        return tokens.get(current - 1);
    }

    /**
     * Shows an error to the user.
     * 
     * @param token   the token that caused an error
     * @param type    the type of error
     * @param message the error message
     * @return        a {@link ParseError}
     */
    private ParseError error(Token token, ErrorType type, String message) {
        JMPL.error(token, type, message);
        return new ParseError();
    }
    
    /**
     * Synchronises the parser by discarding tokens until we reach the start of the next statement.
     */
    private void synchronise() {
        advance();

        while(!isAtEnd()) {
            if(previous().type == TokenType.SEMICOLON) return;

            // Switch the tokens that should start a statement
            switch(peek().type) {
                case TokenType.FUNCTION:
                case TokenType.LET:
                case TokenType.IF:
                case TokenType.RETURN:
                case TokenType.WHILE:
                    return;
                default:
                    // If another token is found
                    break;
            }

            advance();
        }
    }
}