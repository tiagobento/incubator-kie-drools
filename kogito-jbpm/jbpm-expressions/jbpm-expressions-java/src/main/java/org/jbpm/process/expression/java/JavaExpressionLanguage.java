/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.jbpm.process.expression.java;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import org.jbpm.process.core.ContextResolver;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.expression.ValidationScope;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.AssignExpr.Operator;
import com.github.javaparser.ast.expr.CastExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.type.UnknownType;

/**
 * Java as a BPMN expression language.
 *
 * A Java script or condition is not evaluated at runtime: it is compiled into the generated application as a lambda
 * over <code>kcontext</code>, with every process variable it names declared and cast to its type first. A mistake
 * is reported when the application is built. Nothing here runs in a process instance, so the runtime methods refuse.
 */
public class JavaExpressionLanguage implements ExpressionLanguage {

    public static final String ID = "java";
    public static final String URI = "http://www.java.com/java";

    static final String KCONTEXT = "kcontext";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String uri() {
        return URI;
    }

    @Override
    public Collection<String> identifiers() {
        return List.of(ID, URI);
    }

    @Override
    public Set<Surface> surfaces() {
        return EnumSet.of(Surface.CONDITION, Surface.EXPRESSION, Surface.SCRIPT);
    }

    @Override
    public void validate(Surface surface, String expression, ValidationScope scope, Consumer<String> problems) {
        if (surface == Surface.SCRIPT) {
            JavaScriptValidator.validate(expression, scope, problems);
        }
    }

    @Override
    public String compile(Surface surface, String expression, Class<?> type, String root, ContextResolver scope) {
        return surface == Surface.SCRIPT ? script(expression, scope).toString() : evaluator(expression, scope).toString();
    }

    @Override
    public ReturnValueEvaluator evaluator(String expression, Class<?> type, String root) {
        throw notAtRuntime(expression);
    }

    @Override
    public Action script(String script) {
        throw notAtRuntime(script);
    }

    private static UnsupportedOperationException notAtRuntime(String expression) {
        return new UnsupportedOperationException(String.format(
                "A Java expression is compiled into the application when the application is built; it cannot be evaluated at runtime: '%s'. "
                        + "Build the application with the Kogito build plugin, or write the expression in a language that is evaluated at runtime.",
                expression));
    }

    /**
     * <code>(KogitoProcessContext kcontext) -> { ...script... }</code>, each variable the script names declared
     * from the context first.
     */
    static LambdaExpr script(String script, ContextResolver scope) {
        BlockStmt body = StaticJavaParser.parseBlock("{" + script + "\n}");
        declareVariables(body, body, scope);
        ClassOrInterfaceType contextType = StaticJavaParser.parseClassOrInterfaceType(KogitoProcessContext.class.getName());
        return new LambdaExpr(NodeList.nodeList(new Parameter(contextType, KCONTEXT)), body, true);
    }

    /**
     * <code>(kcontext) -> { ...; return expression; }</code>. The expression may be a bare variable, an expression,
     * a statement or a whole block, tried in that order.
     */
    static LambdaExpr evaluator(String expression, ContextResolver scope) {
        BlockStmt body = new BlockStmt();
        LambdaExpr lambda = new LambdaExpr(new Parameter(new UnknownType(), KCONTEXT), body);
        BlockStmt parsed = parseIdentifier(expression);
        if (parsed == null) {
            parsed = parseExpression(expression);
        }
        if (parsed == null) {
            parsed = parseStatement(expression);
        }
        if (parsed == null) {
            parsed = StaticJavaParser.parseBlock("{" + expression + "\n}");
        }
        declareVariables(parsed, body, scope);
        parsed.getStatements().forEach(body::addStatement);
        return lambda;
    }

    private static void declareVariables(BlockStmt parsed, BlockStmt body, ContextResolver scope) {
        Set<NameExpr> identifiers = new HashSet<>(parsed.findAll(NameExpr.class));
        for (NameExpr identifier : identifiers) {
            VariableScope variableScope = (VariableScope) scope.resolveContext(VariableScope.VARIABLE_SCOPE, identifier.getNameAsString());
            if (variableScope == null) {
                continue;
            }
            Variable variable = variableScope.findVariable(identifier.getNameAsString());
            if (variable == null) {
                continue;
            }
            body.addStatement(0, declaration(variable));
        }
    }

    /**
     * <code>Type name = (Type) kcontext.getVariable("name");</code>
     */
    public static AssignExpr declaration(Variable variable) {
        Type type = StaticJavaParser.parseType(variable.getType().getStringType());
        VariableDeclarationExpr target = new VariableDeclarationExpr(type, variable.getName());
        Expression source = new CastExpr(type, new MethodCallExpr(new NameExpr(KCONTEXT), "getVariable", NodeList.nodeList(new StringLiteralExpr(variable.getName()))));
        return new AssignExpr(target, source, Operator.ASSIGN);
    }

    private static BlockStmt parseStatement(String expression) {
        try {
            BlockStmt block = new BlockStmt();
            block.addStatement(StaticJavaParser.parseStatement(expression));
            return block;
        } catch (ParseProblemException e) {
            return null;
        }
    }

    private static BlockStmt parseExpression(String expression) {
        try {
            BlockStmt block = new BlockStmt();
            block.addStatement(new ReturnStmt(StaticJavaParser.parseExpression(expression)));
            return block;
        } catch (ParseProblemException e) {
            return null;
        }
    }

    private static BlockStmt parseIdentifier(String expression) {
        try {
            BlockStmt block = new BlockStmt();
            block.addStatement(new ReturnStmt(new NameExpr(StaticJavaParser.parseSimpleName(expression.trim()))));
            return block;
        } catch (ParseProblemException e) {
            return null;
        }
    }
}
