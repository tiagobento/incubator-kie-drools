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
package org.jbpm.process.expression.feel;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.expression.ExpressionScope;
import org.jbpm.process.expression.PathWriter;
import org.jbpm.process.expression.ValidationScope;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.process.instance.impl.FeelReturnValueEvaluator;
import org.jbpm.process.instance.impl.FeelReturnValueEvaluatorException;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.jbpm.process.instance.impl.actions.FeelScriptAction;
import org.jbpm.process.instance.impl.feel.BpmnFeel;
import org.jbpm.process.instance.impl.feel.BpmnFeelVariables;
import org.jbpm.workflow.instance.impl.FeelInterpolation;

/**
 * FEEL, the DMN expression language, for a document that must not reach into the application: no side effects,
 * no Java calls, and every expression checked when the process is built.
 *
 * A FEEL script evaluates to a context whose entries are written back to the variables they name. A FEEL
 * assignment target has to be a variable or a property path, since FEEL computes values and has no assignment.
 */
public class FeelExpressionLanguage implements ExpressionLanguage {

    public static final String ID = "FEEL";
    public static final String URI = "http://www.omg.org/spec/DMN/20180521/FEEL/";
    public static final String FEEL_2014_URI = "http://www.omg.org/spec/FEEL/20140401";
    public static final String MIME_TYPE = "application/feel";

    /** A reference: a name, or names separated by dots. */
    private static final Pattern REFERENCE = Pattern.compile("\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*(\\.\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*)*");

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
        return List.of(ID, URI, FEEL_2014_URI, MIME_TYPE);
    }

    @Override
    public Set<Surface> surfaces() {
        return EnumSet.of(Surface.CONDITION, Surface.EXPRESSION, Surface.SCRIPT, Surface.INTERPOLATION, Surface.ASSIGNMENT_TARGET);
    }

    @Override
    public void validate(Surface surface, String expression, ValidationScope scope, Consumer<String> problems) {
        if (surface == Surface.ASSIGNMENT_TARGET) {
            if (!isReference(expression)) {
                problems.accept(notAReference(expression));
            }
            return;
        }
        List<String> names = new ArrayList<>(scope.variableNames());
        names.addAll(scope.globals());
        if (surface == Surface.INTERPOLATION) {
            names.addAll(BpmnFeelVariables.INTERPOLATION_NAMES);
        } else {
            names.add(BpmnFeelVariables.KCONTEXT);
        }
        try {
            BpmnFeel.compile(expression, names);
        } catch (FeelReturnValueEvaluatorException e) {
            problems.accept(String.format("Invalid FEEL expression: '%s'. %s", expression, e.getMessage()));
        }
    }

    @Override
    public ReturnValueEvaluator evaluator(String expression, Class<?> type, String root) {
        // the caller says what the expression is for: only a condition is held to a boolean result
        return new FeelReturnValueEvaluator(expression, type);
    }

    @Override
    public Action script(String script) {
        return new FeelScriptAction(script);
    }

    @Override
    public Object interpolate(String placeholderBody, ExpressionScope scope) {
        return FeelInterpolation.evaluate(placeholderBody, BpmnFeelVariables.forInterpolation(scope));
    }

    @Override
    public Object assign(String target, Object value, ExpressionScope scope) {
        if (!isReference(target)) {
            throw new IllegalArgumentException(notAReference(target));
        }
        return PathWriter.write(target, value, scope);
    }

    private static boolean isReference(String expression) {
        Matcher matcher = org.jbpm.util.PatternConstants.PARAMETER_MATCHER.matcher(expression);
        String body = matcher.find() ? matcher.group(1) : expression;
        return REFERENCE.matcher(body).matches();
    }

    private static String notAReference(String expression) {
        return String.format(
                "'%s' cannot be an assignment target in FEEL. A target has to be a variable, or a property of one, "
                        + "such as #{person.name} - not an expression computing a value.",
                expression.trim());
    }
}
