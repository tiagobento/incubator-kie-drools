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
package org.jbpm.workflow.core.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;

import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.expression.ExpressionLanguages;
import org.jbpm.process.expression.ExpressionScope;
import org.jbpm.process.instance.impl.AssignmentAction;
import org.jbpm.process.instance.impl.AssignmentProducer;
import org.jbpm.util.PatternConstants;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

/**
 * An assignment whose source holds one or more <code>#{...}</code> placeholders. Each placeholder is evaluated in
 * the assignment's language - the one it declared, else the document's - and a source that is one placeholder
 * yields that value as is, while any other source is spliced together as a string.
 */
public class InputExpressionAssignment implements AssignmentAction {

    private DataDefinition from;
    private DataDefinition to;
    private String dialect;

    public InputExpressionAssignment(DataDefinition from, DataDefinition to) {
        this(from, to, null);
    }

    public InputExpressionAssignment(DataDefinition from, DataDefinition to, String dialect) {
        this.from = from;
        this.to = to;
        this.dialect = dialect;
    }

    @Override
    public void execute(Function<String, Object> sourceResolver, Function<String, Object> targetResolver, AssignmentProducer producer) throws Exception {
        execute(null, sourceResolver, targetResolver, producer);
    }

    @Override
    public void execute(KogitoProcessContext context, Function<String, Object> sourceResolver, Function<String, Object> targetResolver, AssignmentProducer producer)
            throws Exception {
        // producer in this case is void
        ExpressionLanguage language = ExpressionLanguages.of(dialect, context);
        producer.accept(to.getLabel(), evalInput(language, ExpressionScope.of(sourceResolver, context), from.getExpression()));
    }

    private Object evalInput(ExpressionLanguage language, ExpressionScope scope, String expression) {
        String outcome = expression;
        Matcher matcher = PatternConstants.PARAMETER_MATCHER.matcher(expression);
        Map<String, Object> values = new HashMap<>();
        while (matcher.find()) {
            String paramName = matcher.group(1);
            Object value = language.interpolate(paramName, scope);
            if (value != null) {
                values.put(paramName, value);
            }
        }
        if (values.size() == 1) {
            return values.values().iterator().next();
        }
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            outcome = outcome.replace("#{" + entry.getKey() + "}", entry.getValue().toString());
        }
        return outcome;
    }
}
