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
package org.jbpm.compiler.canonical;

import org.jbpm.process.core.ContextResolver;
import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.expression.ExpressionLanguage.Surface;
import org.jbpm.process.expression.ExpressionLanguages;
import org.jbpm.workflow.core.impl.NodeImpl;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.ClassExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.NullLiteralExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

/**
 * The generated code for an expression, whatever its language.
 *
 * A language that compiles to Java supplies the source itself through {@link ExpressionLanguage#compile}. Any other
 * language is called at runtime: the generated code asks the {@link ExpressionLanguages registry} for the language
 * by id and hands it the expression text, so the application needs the module providing the language on its
 * classpath and nothing else.
 */
public final class ExpressionCodegen {

    private final ClassLoader classLoader;

    private ExpressionCodegen(ClassLoader classLoader) {
        this.classLoader = classLoader;
    }

    public static ExpressionCodegen of(ClassLoader classLoader) {
        return new ExpressionCodegen(classLoader);
    }

    /**
     * A {@link org.jbpm.process.instance.impl.ReturnValueEvaluator} for a condition or a value expression.
     */
    public Expression evaluator(ContextResolver scope, Surface surface, String dialect, String expression, Class<?> type, String root) {
        ExpressionLanguage language = language(dialect);
        String compiled = language.compile(surface, expression, type, root, scope);
        if (compiled != null) {
            return StaticJavaParser.parseExpression(compiled);
        }
        return runtimeCall(language, "evaluator",
                literal(expression),
                new ClassExpr(StaticJavaParser.parseClassOrInterfaceType(type.getName())),
                root == null ? new NullLiteralExpr() : literal(root));
    }

    /**
     * An {@link org.jbpm.process.instance.impl.Action} for a script.
     */
    public Expression script(NodeImpl node, String dialect, String script) {
        ExpressionLanguage language = language(dialect);
        String compiled = language.compile(Surface.SCRIPT, script, null, null, node);
        if (compiled != null) {
            return StaticJavaParser.parseExpression(compiled);
        }
        return runtimeCall(language, "script", literal(script));
    }

    private ExpressionLanguage language(String dialect) {
        return ExpressionLanguages.require(dialect == null || dialect.isBlank() ? ExpressionLanguages.DEFAULT : dialect, classLoader);
    }

    private static Expression runtimeCall(ExpressionLanguage language, String method, Expression... arguments) {
        MethodCallExpr require = new MethodCallExpr(new NameExpr(ExpressionLanguages.class.getName()), "require", NodeList.nodeList(literal(language.id())));
        return new MethodCallExpr(require, method, NodeList.nodeList(arguments));
    }

    private static StringLiteralExpr literal(String value) {
        return new StringLiteralExpr().setString(value);
    }
}
