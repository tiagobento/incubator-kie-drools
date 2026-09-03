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

import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

/**
 * An evaluator that asks its language for the real one the first time it is used.
 *
 * <p>
 * A document is parsed long before its expressions run, and in a build tool the language may compile the
 * expression into the application rather than evaluate it - so nothing is asked of the language while parsing.
 * Code generation reads the dialect and the expression off this evaluator and decides; on the in-memory path the
 * language is asked when the expression first runs.
 */
public class DeferredReturnValueEvaluator implements ReturnValueEvaluator {

    private final String dialect;
    private final String expression;
    private final Class<?> type;
    private final String root;
    private volatile ReturnValueEvaluator delegate;

    public DeferredReturnValueEvaluator(String dialect, String expression, Class<?> type) {
        this(dialect, expression, type, null);
    }

    public DeferredReturnValueEvaluator(String dialect, String expression, Class<?> type, String root) {
        this.dialect = dialect;
        this.expression = expression;
        this.type = type;
        this.root = root;
    }

    private ReturnValueEvaluator delegate() {
        if (delegate == null) {
            delegate = ExpressionLanguages.require(dialect).evaluator(expression, type, root);
        }
        return delegate;
    }

    @Override
    public String dialect() {
        return dialect;
    }

    @Override
    public String expression() {
        return expression;
    }

    @Override
    public Class<?> type() {
        return type;
    }

    @Override
    public String root() {
        return root;
    }

    @Override
    public Object evaluate(KogitoProcessContext processContext) {
        return delegate().evaluate(processContext);
    }

    @Override
    public Object eval(Object event) {
        return delegate().eval(event);
    }

    @Override
    public Object eval(Function<String, Object> resolver) {
        return delegate().eval(resolver);
    }

    @Override
    public Object eval(Map<String, Object> variables) {
        return delegate().eval(variables);
    }

    @Override
    public String toString() {
        return "[" + dialect + "] (" + expression + ")";
    }
}
