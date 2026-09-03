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
package org.jbpm.process.builder.dialect;

import org.jbpm.process.builder.ActionBuilder;
import org.jbpm.process.builder.AssignmentBuilder;
import org.jbpm.process.builder.ProcessBuildContext;
import org.jbpm.process.builder.ProcessClassBuilder;
import org.jbpm.process.builder.ReturnValueEvaluatorBuilder;
import org.jbpm.process.expression.ExpressionLanguage;

/**
 * Any {@link ExpressionLanguage} on the in-memory build path: nothing is compiled ahead of time, the language is
 * handed each expression and evaluates it when the process runs.
 */
public class ExpressionLanguageProcessDialect implements ProcessDialect {

    private final ExpressionLanguage language;

    public ExpressionLanguageProcessDialect(ExpressionLanguage language) {
        this.language = language;
    }

    public ExpressionLanguage getLanguage() {
        return language;
    }

    @Override
    public ActionBuilder getActionBuilder() {
        return (context, action, actionDescr, contextResolver) -> action.setMetaData("Action", language.script(actionDescr.getText()));
    }

    @Override
    public ReturnValueEvaluatorBuilder getReturnValueEvaluatorBuilder() {
        return (context, constraintNode, descr, contextResolver) -> constraintNode.setEvaluator(language.evaluator(descr.getText(), Boolean.class, null));
    }

    @Override
    public AssignmentBuilder getAssignmentBuilder() {
        // a data association builds its assignment actions itself, asking the language for a whole one first
        return (context, assignment, sources, target) -> {
        };
    }

    @Override
    public ProcessClassBuilder getProcessClassBuilder() {
        throw new UnsupportedOperationException(String.format("The expression language '%s' compiles nothing ahead of time.", language.id()));
    }

    @Override
    public void addProcess(ProcessBuildContext context) {
        // nothing to compile
    }
}
