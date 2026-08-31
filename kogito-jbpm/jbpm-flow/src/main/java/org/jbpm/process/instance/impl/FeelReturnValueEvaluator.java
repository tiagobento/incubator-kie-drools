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
package org.jbpm.process.instance.impl;

import java.util.Map;

import org.jbpm.process.instance.impl.feel.BpmnFeel;
import org.jbpm.process.instance.impl.feel.BpmnFeelVariables;
import org.kie.dmn.api.feel.runtime.events.FEELEvent;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.lang.CompiledExpression;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

public class FeelReturnValueEvaluator extends AbstractReturnValueEvaluator {

    /** Compiled once and kept: parsing is the expensive part of a FEEL evaluation, and the expression never changes. */
    private transient CompiledExpression compiledExpression;

    public FeelReturnValueEvaluator() {
        this("true()");
    }

    /**
     * An evaluator for a condition: the expression has to produce a boolean.
     */
    public FeelReturnValueEvaluator(String expr) {
        this(expr, Boolean.class);
    }

    /**
     * @param type what the expression is expected to produce. Only a condition is held to a boolean; a transformation
     *        or a correlation expression produces a value of any type.
     */
    public FeelReturnValueEvaluator(String expr, Class<?> type) {
        super("FEEL", expr, type, null);
    }

    public Object evaluate(KogitoProcessContext context) {
        Map<String, Object> variables = BpmnFeelVariables.of(context);

        FEEL feel = BpmnFeel.newFeel();
        FeelErrorEvaluatorListener listener = new FeelErrorEvaluatorListener();
        feel.addListener(listener);

        CompiledExpression compiled = compiledExpression != null
                ? compiledExpression
                : BpmnFeel.compileQuietly(feel, expression(), variables.keySet());

        Object value = feel.evaluate(compiled, variables);

        // compile and evaluation errors are reported together, and only a clean expression is worth keeping
        BpmnFeel.failOnError(listener, expression());
        compiledExpression = compiled;
        if (Boolean.class.equals(type()) && !(value instanceof Boolean)) {
            throw new RuntimeException("Constraints must return boolean values: " +
                    expression() + " returns " + value +
                    (value == null ? "" : " (type=" + value.getClass()));
        }

        return value;
    }

    public static String eventToMessage(FEELEvent event) {
        return BpmnFeel.eventToMessage(event);
    }

}
