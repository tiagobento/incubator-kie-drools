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
 * An assignment whose target is a <code>#{...}</code> naming a place to write, such as <code>#{person.name}</code>.
 * The write is done by the assignment's language - the one it declared, else the document's - and what it wrote is
 * then produced under the target's id, as every language does for a bare variable target.
 */
public class OutputExpressionAssignment implements AssignmentAction {

    private DataDefinition from;
    private DataDefinition to;
    private String dialect;
    private String target;

    public OutputExpressionAssignment(DataDefinition from, DataDefinition to) {
        this(from, to, null);
    }

    public OutputExpressionAssignment(DataDefinition from, DataDefinition to, String dialect) {
        this.from = from;
        this.to = to;
        this.dialect = dialect;
        Matcher matcher = PatternConstants.PARAMETER_MATCHER.matcher(this.to.getExpression());
        this.target = matcher.find() ? matcher.group(1) : this.to.getExpression();
    }

    @Override
    public void execute(Function<String, Object> sourceResolver, Function<String, Object> targetResolver, AssignmentProducer producer) throws Exception {
        execute(null, sourceResolver, targetResolver, producer);
    }

    @Override
    public void execute(KogitoProcessContext context, Function<String, Object> sourceResolver, Function<String, Object> targetResolver, AssignmentProducer producer)
            throws Exception {
        ExpressionLanguage language = ExpressionLanguages.of(dialect, context);
        Object value = language.assign(target, sourceResolver.apply(from.getLabel()), ExpressionScope.of(targetResolver, context));
        producer.accept(to.getId(), value);
    }
}
