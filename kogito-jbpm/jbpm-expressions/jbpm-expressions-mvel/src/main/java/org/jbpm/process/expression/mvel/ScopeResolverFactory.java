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

import java.util.HashMap;
import java.util.Map;

import org.jbpm.process.expression.ExpressionScope;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.mvel2.integration.VariableResolver;
import org.mvel2.integration.impl.ImmutableDefaultFactory;
import org.mvel2.integration.impl.SimpleValueResolver;

/**
 * An MVEL variable resolver over an {@link ExpressionScope}.
 */
class ScopeResolverFactory extends ImmutableDefaultFactory {

    private static final long serialVersionUID = 1L;

    private final transient ExpressionScope scope;
    private final transient Map<String, Object> instances = new HashMap<>();

    ScopeResolverFactory(ExpressionScope scope) {
        this.scope = scope;
        KogitoProcessContext context = scope.context();
        if (context != null) {
            if (context.getNodeInstance() != null) {
                instances.put("nodeInstance", context.getNodeInstance());
            }
            if (context.getProcessInstance() != null) {
                instances.put("processInstance", context.getProcessInstance());
                instances.put("processInstanceId", context.getProcessInstance().getStringId());
                instances.put("parentProcessInstanceId", context.getProcessInstance().getParentProcessInstanceId());
            }
        }
    }

    @Override
    public boolean isResolveable(String name) {
        return scope.has(name) || instances.containsKey(name);
    }

    @Override
    public VariableResolver getVariableResolver(String name) {
        return new SimpleValueResolver(scope.has(name) ? scope.get(name) : instances.get(name));
    }
}
