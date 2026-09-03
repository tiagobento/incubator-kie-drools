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

import org.jbpm.process.expression.ExpressionLanguages;
import org.jbpm.process.expression.ExpressionScope;

/**
 * Evaluates the body of one <code>#{...}</code> placeholder in the language the document selected.
 *
 * Only this leaf changes between languages: the placeholder scanning, the single-placeholder object pass-through and
 * the concatenation fallback are shared by every caller, so a document that changes language keeps the same field
 * semantics.
 */
public final class InterpolationEvaluator {

    private InterpolationEvaluator() {
    }

    /**
     * @param language the document's language, or <code>null</code> for the default
     * @param placeholderBody what was between <code>#{</code> and <code>}</code>
     * @param scope what the placeholder can see
     */
    public static Object evaluate(String language, String placeholderBody, ExpressionScope scope) {
        return ExpressionLanguages.require(language == null || language.isBlank() ? ExpressionLanguages.DEFAULT : language)
                .interpolate(placeholderBody, scope);
    }
}
