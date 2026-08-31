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
import java.util.function.Supplier;

import org.jbpm.util.ExpressionLanguages;
import org.mvel2.integration.VariableResolverFactory;

/**
 * Evaluates the body of one <code>#{...}</code> placeholder in the language the document selected.
 *
 * Only this leaf changes between languages: the placeholder scanning, the single-placeholder object pass-through and
 * the concatenation fallback are shared by both, so a document that flips language keeps the same field semantics.
 *
 * The two scopes are supplied lazily because they are built differently and only one of them is ever needed - an MVEL
 * document must not pay for building a FEEL variable map, and vice versa.
 */
public final class InterpolationEvaluator {

    private InterpolationEvaluator() {
    }

    public static Object evaluate(String language, String placeholderBody,
            Supplier<VariableResolverFactory> mvelScope,
            Supplier<Map<String, Object>> feelScope) {
        if (ExpressionLanguages.isFeel(language)) {
            return FeelInterpolation.evaluate(placeholderBody, feelScope.get());
        }
        return MVELProcessHelper.evaluator().eval(placeholderBody, mvelScope.get());
    }
}
