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
package org.jbpm.process.instance.impl.actions;

import java.util.Map;

import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.ContextResolver;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.process.instance.impl.FeelErrorEvaluatorListener;
import org.jbpm.process.instance.impl.feel.BpmnFeel;
import org.jbpm.process.instance.impl.feel.BpmnFeelTypes;
import org.jbpm.process.instance.impl.feel.BpmnFeelVariables;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.lang.CompiledExpression;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a FEEL script task, or a FEEL onEntry/onExit script.
 *
 * FEEL is a pure expression language - it has no statements and no assignment - so a FEEL script is defined as an
 * expression evaluating to a context whose entries are written back to the process variables they name. A result that
 * is not a context cannot mean anything and is an error.
 */
public class FeelScriptAction implements Action {

    private static final Logger logger = LoggerFactory.getLogger(FeelScriptAction.class);

    private final String expression;

    private transient CompiledExpression compiledExpression;

    public FeelScriptAction(String expression) {
        this.expression = expression;
    }

    public String getExpression() {
        return expression;
    }

    @Override
    public void execute(KogitoProcessContext context) throws Exception {
        FEEL feel = BpmnFeel.newFeel();
        FeelErrorEvaluatorListener listener = new FeelErrorEvaluatorListener();
        feel.addListener(listener);

        Map<String, Object> variables = BpmnFeelVariables.of(context);
        CompiledExpression compiled = compiledExpression != null
                ? compiledExpression
                : BpmnFeel.compileQuietly(feel, expression, variables.keySet());

        Object result = feel.evaluate(compiled, variables);

        BpmnFeel.failOnError(listener, expression);
        compiledExpression = compiled;

        if (!(result instanceof Map)) {
            throw new IllegalArgumentException(String.format(
                    "A FEEL script must evaluate to a context whose entries are written back to the process variables, "
                            + "but '%s' returned %s. Write it as, for instance, { aVariable: someExpression }.",
                    expression, result == null ? "null" : result.getClass().getName()));
        }
        writeBack(context, (Map<?, ?>) result);
    }

    private void writeBack(KogitoProcessContext context, Map<?, ?> result) {
        for (Map.Entry<?, ?> entry : result.entrySet()) {
            String name = String.valueOf(entry.getKey());
            Variable variable = declaredVariable(context, name);
            if (variable != null) {
                // FEEL has one number type and its own temporal ones; the variable was declared with a Java type
                context.setVariable(name, BpmnFeelTypes.fromFeel(entry.getValue(), declaredType(variable), name));
            } else {
                // a FEEL context may legitimately carry intermediate values; only the entries naming a variable are
                // written back, and the rest are reported so a mistyped key is discoverable
                logger.info("FEEL script '{}' returned an entry named '{}', which is not a declared process variable. It was ignored.",
                        expression, name);
            }
        }
    }

    private static Class<?> declaredType(Variable variable) {
        return variable.getType() == null ? null : variable.getType().getObjectClass();
    }

    /**
     * Resolved through the node instance where possible, so a variable declared by an enclosing subprocess counts just
     * as a process-level one does.
     */
    private static Variable declaredVariable(KogitoProcessContext context, String name) {
        VariableScope scope = null;
        KogitoNodeInstance nodeInstance = context.getNodeInstance();
        if (nodeInstance instanceof ContextResolver) {
            scope = (VariableScope) ((ContextResolver) nodeInstance).resolveContext(VariableScope.VARIABLE_SCOPE, name);
        } else if (context.getProcessInstance() != null && context.getProcessInstance().getProcess() instanceof ContextContainer) {
            scope = (VariableScope) ((ContextContainer) context.getProcessInstance().getProcess())
                    .getDefaultContext(VariableScope.VARIABLE_SCOPE);
        }
        return scope == null ? null : scope.findVariable(name);
    }

    @Override
    public String toString() {
        return "FeelScriptAction(" + expression + ")";
    }
}
