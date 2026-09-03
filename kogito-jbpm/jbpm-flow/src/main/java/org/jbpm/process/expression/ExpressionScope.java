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

import java.util.Map;
import java.util.function.Function;

import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;

/**
 * What an expression can see when it is evaluated outside a condition or a script: the variables in scope, by name
 * and as a whole, and the process and node instance it runs in.
 *
 * <p>
 * A language that resolves names one at a time uses {@link #has} and {@link #get}; a language that needs every
 * value up front uses {@link #variables()}. Neither includes the process or node instance themselves: a language
 * decides what of {@link #context()} it exposes, and under which names.
 */
public interface ExpressionScope {

    boolean has(String name);

    Object get(String name);

    /**
     * Every variable visible from where the expression runs, innermost scope winning. May be a subset of what
     * {@link #get} can resolve when the scope is backed by a resolver that cannot be enumerated.
     */
    Map<String, Object> variables();

    /**
     * The process and node instance the expression runs in. Either may be absent.
     */
    KogitoProcessContext context();

    static ExpressionScope of(KogitoNodeInstance nodeInstance) {
        return new Scopes.NodeInstanceScope(nodeInstance);
    }

    static ExpressionScope of(KogitoProcessInstance processInstance) {
        return new Scopes.ProcessInstanceScope(processInstance);
    }

    static ExpressionScope of(Map<String, Object> variables) {
        return new Scopes.MapScope(variables, null);
    }

    static ExpressionScope of(Map<String, Object> variables, KogitoProcessContext context) {
        return new Scopes.MapScope(variables, context);
    }

    /**
     * A scope over a resolver, such as the source or target side of a data association. The variables it can
     * enumerate are those visible from the context's node or process instance.
     */
    static ExpressionScope of(Function<String, Object> resolver, KogitoProcessContext context) {
        return new Scopes.ResolverScope(resolver, context);
    }
}
