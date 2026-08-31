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
package org.jbpm.process.instance.impl.feel;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.jbpm.process.instance.impl.FeelErrorEvaluatorListener;
import org.jbpm.process.instance.impl.FeelReturnValueEvaluatorException;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.lang.CompiledExpression;
import org.kie.dmn.feel.lang.CompilerContext;
import org.kie.dmn.feel.lang.impl.FEELBuilder;
import org.kie.dmn.feel.parser.feel11.profiles.KieExtendedFEELProfile;

/**
 * The single place where BPMN builds a FEEL engine.
 *
 * BPMN evaluates expressions coming from authored and imported models, so its FEEL surface is bounded by construction:
 * external functions, which reflectively invoke an arbitrary class, are disabled. No call site in the process modules
 * should build a FEEL engine directly - go through here, so the guarantee holds everywhere.
 */
public final class BpmnFeel {

    private BpmnFeel() {
    }

    /**
     * A new engine, safe for BPMN. Engines are not shared: listeners are registered per instance.
     */
    public static FEEL newFeel() {
        return FEELBuilder.builder()
                .withProfiles(List.of(new KieExtendedFEELProfile()))
                .withExternalFunctionsDisabled()
                .build();
    }

    public static CompiledExpression compile(String expression) {
        return compile(expression, List.of());
    }

    /**
     * Compiles an expression, failing on a syntax error or on a rejected external function.
     *
     * Called at build time, so an invalid expression fails the build rather than the process instance, and lazily at
     * runtime, where the result is cached by the evaluator owning the expression.
     */
    public static CompiledExpression compile(String expression, Collection<String> inputVariableNames) {
        FEEL feel = newFeel();
        FeelErrorEvaluatorListener listener = new FeelErrorEvaluatorListener();
        feel.addListener(listener);
        CompiledExpression compiled = compileQuietly(feel, expression, inputVariableNames);
        failOnError(listener, expression);
        return compiled;
    }

    /**
     * Compiles without failing, leaving the caller to decide what to do with whatever the engine's listeners collected.
     *
     * Runtime evaluators use this so a compile error and an evaluation error are still reported together, the way they
     * were when every evaluation recompiled the expression from scratch.
     */
    public static CompiledExpression compileQuietly(FEEL feel, String expression, Collection<String> inputVariableNames) {
        CompilerContext context = feel.newCompilerContext();
        inputVariableNames.forEach(name -> context.addInputVariable(name, null));
        return feel.compile(expression, context);
    }

    /**
     * Throws if the listener collected any error, naming the expression that produced it.
     */
    public static void failOnError(FeelErrorEvaluatorListener listener, String expression) {
        if (!listener.getErrorEvents().isEmpty()) {
            throw new FeelReturnValueEvaluatorException(
                    "FEEL expression '" + expression + "' is not valid: " + eventsToMessage(listener.getErrorEvents()));
        }
    }

    public static String eventsToMessage(List<FEELEvent> events) {
        return events.stream().map(BpmnFeel::eventToMessage).collect(Collectors.joining(", "));
    }

    public static String eventToMessage(FEELEvent event) {
        StringBuilder messageBuilder = new StringBuilder(event.getSeverity().toString()).append(" ").append(event.getMessage());
        if (event.getOffendingSymbol() != null) {
            messageBuilder.append(" ( offending symbol: '").append(event.getOffendingSymbol()).append("' )");
        }
        if (event.getSourceException() != null) {
            messageBuilder.append("  ").append(event.getSourceException().getMessage());
        }
        return messageBuilder.toString();
    }
}
