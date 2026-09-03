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
package org.jbpm.process.expression.mvel;

import java.io.Serializable;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.expression.ExpressionScope;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.process.instance.impl.MVELInterpretedReturnValueEvaluator;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.jbpm.workflow.instance.impl.MVELProcessHelper;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.VariableResolverFactory;
import org.mvel2.integration.impl.SimpleValueResolver;

/**
 * MVEL, the language a BPMN expression is in when nothing says otherwise.
 *
 * Expressions are interpreted at runtime against the variables in scope, plus <code>nodeInstance</code>,
 * <code>processInstance</code>, <code>processInstanceId</code> and <code>parentProcessInstanceId</code>. MVEL is not
 * available in a native image.
 */
public class MvelExpressionLanguage implements ExpressionLanguage {

    public static final String ID = "mvel";
    public static final String URI = "http://www.mvel.org/2.0";

    private static final String VALUE = "_value";
    private static final Map<String, Serializable> COMPILED_ASSIGNMENTS = new ConcurrentHashMap<>();

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String uri() {
        return URI;
    }

    @Override
    public java.util.Collection<String> identifiers() {
        return List.of(ID, URI);
    }

    @Override
    public Set<Surface> surfaces() {
        return EnumSet.of(Surface.CONDITION, Surface.EXPRESSION, Surface.SCRIPT, Surface.INTERPOLATION, Surface.ASSIGNMENT_TARGET);
    }

    @Override
    public ReturnValueEvaluator evaluator(String expression, Class<?> type, String root) {
        return new MVELInterpretedReturnValueEvaluator(expression);
    }

    @Override
    public Action script(String script) {
        return new MvelScriptAction(script);
    }

    @Override
    public Object interpolate(String placeholderBody, ExpressionScope scope) {
        return MVELProcessHelper.evaluator().eval(placeholderBody, new ScopeResolverFactory(scope));
    }

    @Override
    public Object assign(String target, Object value, ExpressionScope scope) {
        Serializable compiled = COMPILED_ASSIGNMENTS.computeIfAbsent(target, t -> MVELProcessHelper.compileExpression(t + " = " + VALUE));
        return MVELProcessHelper.evaluator().executeExpression(compiled, new ScopeResolverFactory(scope) {
            @Override
            public boolean isResolveable(String name) {
                return VALUE.equals(name) || super.isResolveable(name);
            }

            @Override
            public VariableResolver getVariableResolver(String name) {
                return VALUE.equals(name) ? new SimpleValueResolver(value) : super.getVariableResolver(name);
            }
        });
    }

    /**
     * What MVEL sees: the scope's variables by name, and the instances the expression runs in under the names MVEL
     * expressions have always used.
     */
    public static VariableResolverFactory resolverFactory(ExpressionScope scope) {
        return new ScopeResolverFactory(scope);
    }
}
