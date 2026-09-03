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
package org.jbpm.process.expression;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import org.jbpm.process.core.ContextResolver;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.process.instance.impl.AssignmentAction;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.jbpm.workflow.core.impl.DataDefinition;
import org.jbpm.workflow.core.node.Assignment;

/**
 * An expression language a BPMN document can select, and the runtime that evaluates it.
 *
 * <p>
 * This is the single extension point for expression languages. One implementation, registered through
 * {@link java.util.ServiceLoader} under this interface's name, covers every place BPMN takes an expression: the
 * parser resolves the names a document uses against {@link #identifiers()}, the validator asks {@link #validate},
 * code generation asks {@link #compile} and, when that answers nothing, emits a call back into this language at
 * runtime, and the runtime calls {@link #evaluator}, {@link #script}, {@link #interpolate}, {@link #assign} or
 * {@link #assignment} depending on the surface.
 *
 * <p>
 * A language declares the {@link Surface surfaces} it supports. A document that uses it anywhere else fails
 * validation with a message naming the surface, rather than failing at runtime. Every runtime method has a default
 * that refuses, so an implementation only writes the surfaces it declares.
 *
 * <p>
 * Whether a language is available is decided by the classpath alone: the module providing it is a dependency of
 * the application or it is not. Nothing in the engine knows any language by name, MVEL, FEEL, Java and XPath
 * included - each of them is an implementation of this interface in its own module.
 */
public interface ExpressionLanguage {

    /**
     * Where an expression appears in a BPMN document. A language supports some or all of them.
     */
    enum Surface {
        /**
         * A condition that must yield a boolean: a sequence flow or gateway <code>conditionExpression</code>, an
         * ad-hoc sub-process activation or completion condition, a conditional start event, a multi-instance
         * <code>completionCondition</code>.
         */
        CONDITION,
        /**
         * A value of any type: a data association <code>transformation</code>, a correlation property.
         */
        EXPRESSION,
        /**
         * A script run for its effect: a <code>scriptTask</code>, an <code>onEntry-script</code> or
         * <code>onExit-script</code>.
         */
        SCRIPT,
        /**
         * The body of a <code>#{...}</code> placeholder: in node names, timer expressions, signal, message, error and
         * escalation names, a call activity's <code>calledElement</code>, user task actors and groups, a rule-flow
         * group, work item parameters, the process description and SLA due date, a multi-instance collection and
         * an assignment's <code>from</code>.
         */
        INTERPOLATION,
        /**
         * A <code>#{...}</code> in an assignment's <code>to</code>: it names a place to write, such as
         * <code>#{person.address.city}</code>.
         */
        ASSIGNMENT_TARGET,
        /**
         * A whole <code>assignment</code> whose <code>from</code> and <code>to</code> are expressions in this
         * language, evaluated together, the way XPath assigns one document fragment into another.
         */
        ASSIGNMENT
    }

    /**
     * The name this language is known by inside the engine: the dialect stored on the model, written into generated
     * code and reported in messages. Compared case-insensitively.
     */
    String id();

    /**
     * The identifier written to a BPMN document, on <code>expressionLanguage</code>, <code>language</code> or
     * <code>scriptFormat</code>. Conventionally a URI.
     */
    String uri();

    /**
     * Every spelling a document may use to select this language. Compared case-insensitively, after trimming.
     * Includes {@link #id()} and {@link #uri()} by default.
     */
    default Collection<String> identifiers() {
        return List.of(id(), uri());
    }

    /**
     * The surfaces this language can be used on.
     */
    Set<Surface> surfaces();

    default boolean supports(Surface surface) {
        return surfaces().contains(surface);
    }

    /**
     * Checks an expression while the process is built, reporting each problem found. The default reports nothing.
     *
     * @param surface where the expression appears
     * @param expression the expression text, never blank
     * @param scope what the expression can see: the variables in scope, the process globals and imports
     * @param problems where to report; each message is turned into a process validation error
     */
    default void validate(Surface surface, String expression, ValidationScope scope, Consumer<String> problems) {
        // nothing by default: an expression is checked when it is first evaluated
    }

    /**
     * Compiles an expression into Java source, for a language whose expressions become part of the generated
     * application rather than being evaluated at runtime.
     *
     * <p>
     * The source must be a single Java expression of the type the surface needs: a
     * {@link ReturnValueEvaluator} for {@link Surface#CONDITION} and {@link Surface#EXPRESSION}, an {@link Action}
     * for {@link Surface#SCRIPT}. Both are functional interfaces, so a lambda over <code>kcontext</code> is enough.
     * Return <code>null</code>, as the default does, to have the generated code call this language at runtime
     * instead, through {@link #evaluator} or {@link #script}.
     *
     * @param surface where the expression appears
     * @param expression the expression text
     * @param type the type the expression must yield, <code>Boolean.class</code> for a condition
     * @param root the variable the expression is evaluated against, or <code>null</code>
     * @param scope resolves the variables the expression can see, with their declared types
     */
    default String compile(Surface surface, String expression, Class<?> type, String root, ContextResolver scope) {
        return null;
    }

    /**
     * An evaluator for a condition or a value expression. Called once when the process definition is built and
     * kept for its lifetime, so this is the place to compile.
     *
     * @param type the type the expression must yield, <code>Boolean.class</code> for a condition
     * @param root the variable the expression is evaluated against, or <code>null</code>
     */
    default ReturnValueEvaluator evaluator(String expression, Class<?> type, String root) {
        throw unsupported(Boolean.class.equals(type) ? Surface.CONDITION : Surface.EXPRESSION);
    }

    /**
     * A script. Called once when the process definition is built and kept for its lifetime.
     */
    default Action script(String script) {
        throw unsupported(Surface.SCRIPT);
    }

    /**
     * Evaluates the body of one <code>#{...}</code> placeholder and returns its value, which the caller either
     * uses as is when the placeholder was the whole field, or converts to a string and splices in.
     */
    default Object interpolate(String placeholderBody, ExpressionScope scope) {
        throw unsupported(Surface.INTERPOLATION);
    }

    /**
     * Writes a value into the place an assignment target names, such as <code>person.address.city</code>, and
     * returns the value that ended up there. When the target is a bare variable name there is nothing to navigate:
     * the caller stores the returned value under that name.
     */
    default Object assign(String target, Object value, ExpressionScope scope) {
        throw unsupported(Surface.ASSIGNMENT_TARGET);
    }

    /**
     * An action for a whole assignment that declared this language, whose <code>from</code> and <code>to</code> are
     * both expressions in it. Return empty, as the default does, to have the engine apply its usual assignment rules
     * - a constant, a plain copy, a <code>#{...}</code> source through {@link #interpolate}, a <code>#{...}</code>
     * target through {@link #assign}.
     */
    default Optional<AssignmentAction> assignment(Assignment assignment, List<DataDefinition> sources, DataDefinition target) {
        return Optional.empty();
    }

    default UnsupportedOperationException unsupported(Surface surface) {
        return new UnsupportedOperationException(String.format("The expression language '%s' cannot be used for %s.", id(), describe(surface)));
    }

    static String describe(Surface surface) {
        switch (surface) {
            case CONDITION:
                return "a condition";
            case EXPRESSION:
                return "an expression yielding a value";
            case SCRIPT:
                return "a script";
            case INTERPOLATION:
                return "a #{...} placeholder";
            case ASSIGNMENT_TARGET:
                return "an assignment target";
            case ASSIGNMENT:
                return "a whole assignment";
            default:
                return surface.name().toLowerCase();
        }
    }
}
