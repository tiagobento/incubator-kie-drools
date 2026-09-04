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
package org.jbpm.workflow.instance.impl;

import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.jbpm.process.instance.impl.FeelErrorEvaluatorListener;
import org.jbpm.process.instance.impl.feel.BpmnFeel;
import org.jbpm.process.instance.impl.feel.BpmnFeelSettings;
import org.jbpm.process.instance.impl.feel.BpmnFeelVariables;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.lang.CompiledExpression;

/**
 * Evaluates the body of a single <code>#{...}</code> placeholder with FEEL.
 *
 * Placeholders are resolved every time a node runs - a node name, a timer, a work item parameter - so parsing one on
 * each evaluation is the difference between roughly 120 and 20 microseconds. They are compiled once and kept.
 *
 * Unlike a constraint or a script, a placeholder has no single scope: the same body can be reached from nodes that see
 * different variables, and the names in scope decide whether the expression compiles at all. The scope is therefore
 * part of the key rather than something to be assumed, which keeps the cache bounded by the model: a body is compiled
 * once for each set of names it is evaluated against.
 */
public final class FeelInterpolation {

    private static final Map<CacheKey, CompiledExpression> COMPILED = new ConcurrentHashMap<>();

    private FeelInterpolation() {
    }

    public static Object evaluate(String placeholderBody, Map<String, Object> variables) {
        boolean sandboxed = BpmnFeelSettings.isSandboxed();
        CompiledExpression compiled = COMPILED.computeIfAbsent(
                new CacheKey(placeholderBody, variables.keySet(), sandboxed),
                key -> BpmnFeel.compile(placeholderBody, BpmnFeelVariables.interpolationTypes(key.names, sandboxed), sandboxed));

        FEEL feel = BpmnFeel.newFeel(sandboxed);
        FeelErrorEvaluatorListener listener = new FeelErrorEvaluatorListener();
        feel.addListener(listener);

        Object value = feel.evaluate(compiled, variables);

        BpmnFeel.failOnError(listener, placeholderBody);
        return value;
    }

    private static final class CacheKey {

        private final String body;
        private final Set<String> names;
        // an expression compiled with external functions allowed must not serve a sandboxed evaluation
        private final boolean sandboxed;

        private CacheKey(String body, Set<String> names, boolean sandboxed) {
            this.body = body;
            // sorted and copied: the caller's scope is rebuilt per evaluation and its iteration order is not fixed
            this.names = new TreeSet<>(names);
            this.sandboxed = sandboxed;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof CacheKey && body.equals(((CacheKey) other).body) && names.equals(((CacheKey) other).names)
                    && sandboxed == ((CacheKey) other).sandboxed;
        }

        @Override
        public int hashCode() {
            return 31 * (31 * body.hashCode() + names.hashCode()) + Boolean.hashCode(sandboxed);
        }
    }
}
