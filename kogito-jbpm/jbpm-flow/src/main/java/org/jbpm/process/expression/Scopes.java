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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.ContextInstance;
import org.jbpm.process.instance.ContextInstanceContainer;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.process.instance.impl.EmtpyKogitoProcessContext;
import org.jbpm.util.ContextFactory;
import org.jbpm.workflow.instance.NodeInstance;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;

final class Scopes {

    private Scopes() {
    }

    /**
     * Every variable visible from a node instance: the process variables, then those of each enclosing composite
     * node, innermost last so it wins.
     */
    static Map<String, Object> visibleFrom(KogitoNodeInstance nodeInstance) {
        Deque<VariableScopeInstance> chain = new ArrayDeque<>();
        Object container = nodeInstance;
        while (container != null) {
            if (container instanceof ContextInstanceContainer) {
                List<ContextInstance> instances = ((ContextInstanceContainer) container).getContextInstances(VariableScope.VARIABLE_SCOPE);
                for (ContextInstance instance : instances == null ? List.<ContextInstance> of() : instances) {
                    if (instance instanceof VariableScopeInstance) {
                        chain.addFirst((VariableScopeInstance) instance);
                    }
                }
            }
            container = container instanceof NodeInstance ? ((NodeInstance) container).getNodeInstanceContainer() : null;
        }
        Map<String, Object> variables = new HashMap<>();
        chain.forEach(scope -> variables.putAll(scope.getVariables()));
        return variables;
    }

    static final class NodeInstanceScope implements ExpressionScope {

        private final NodeInstance nodeInstance;
        private KogitoProcessContext context;

        NodeInstanceScope(KogitoNodeInstance nodeInstance) {
            this.nodeInstance = (NodeInstance) nodeInstance;
        }

        @Override
        public boolean has(String name) {
            return nodeInstance.resolveContextInstance(VariableScope.VARIABLE_SCOPE, name) != null;
        }

        @Override
        public Object get(String name) {
            VariableScopeInstance scope = (VariableScopeInstance) nodeInstance.resolveContextInstance(VariableScope.VARIABLE_SCOPE, name);
            return scope == null ? null : scope.getVariable(name);
        }

        @Override
        public Map<String, Object> variables() {
            return visibleFrom(nodeInstance);
        }

        @Override
        public KogitoProcessContext context() {
            if (context == null) {
                context = ContextFactory.fromNode(nodeInstance);
            }
            return context;
        }
    }

    static final class ProcessInstanceScope implements ExpressionScope {

        private final WorkflowProcessInstance processInstance;
        private KogitoProcessContext context;

        ProcessInstanceScope(KogitoProcessInstance processInstance) {
            this.processInstance = (WorkflowProcessInstance) processInstance;
        }

        @Override
        public boolean has(String name) {
            return processInstance.getVariable(name) != null;
        }

        @Override
        public Object get(String name) {
            return processInstance.getVariable(name);
        }

        @Override
        public Map<String, Object> variables() {
            return processInstance.getVariables();
        }

        @Override
        public KogitoProcessContext context() {
            if (context == null) {
                context = ContextFactory.fromProcessInstance(processInstance);
            }
            return context;
        }
    }

    static final class MapScope implements ExpressionScope {

        private final Map<String, Object> variables;
        private KogitoProcessContext context;

        MapScope(Map<String, Object> variables, KogitoProcessContext context) {
            this.variables = variables;
            this.context = context;
        }

        @Override
        public boolean has(String name) {
            return variables.containsKey(name);
        }

        @Override
        public Object get(String name) {
            return variables.get(name);
        }

        @Override
        public Map<String, Object> variables() {
            return variables;
        }

        @Override
        public KogitoProcessContext context() {
            if (context == null) {
                context = new EmtpyKogitoProcessContext(variables);
            }
            return context;
        }
    }

    static final class ResolverScope implements ExpressionScope {

        private final Function<String, Object> resolver;
        private KogitoProcessContext context;

        ResolverScope(Function<String, Object> resolver, KogitoProcessContext context) {
            this.resolver = resolver;
            this.context = context;
        }

        @Override
        public boolean has(String name) {
            return resolver.apply(name) != null;
        }

        @Override
        public Object get(String name) {
            return resolver.apply(name);
        }

        @Override
        public Map<String, Object> variables() {
            if (context == null) {
                return Map.of();
            }
            if (context.getNodeInstance() != null) {
                return visibleFrom(context.getNodeInstance());
            }
            if (context.getProcessInstance() instanceof WorkflowProcessInstance) {
                return ((WorkflowProcessInstance) context.getProcessInstance()).getVariables();
            }
            return Map.of();
        }

        @Override
        public KogitoProcessContext context() {
            if (context == null) {
                context = new EmtpyKogitoProcessContext(resolver);
            }
            return context;
        }
    }
}
