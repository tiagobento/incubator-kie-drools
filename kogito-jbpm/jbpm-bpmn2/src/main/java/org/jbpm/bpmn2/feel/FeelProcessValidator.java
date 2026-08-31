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
package org.jbpm.bpmn2.feel;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.validation.ProcessValidationError;
import org.jbpm.process.instance.impl.FeelReturnValueEvaluatorException;
import org.jbpm.process.instance.impl.feel.BpmnFeel;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.validation.RuleFlowProcessValidator;
import org.jbpm.util.ExpressionLanguages;
import org.jbpm.workflow.core.Constraint;
import org.jbpm.workflow.core.DroolsAction;
import org.jbpm.workflow.core.impl.ConnectionRef;
import org.jbpm.workflow.core.impl.DroolsConsequenceAction;
import org.jbpm.workflow.core.impl.ExtendedNodeImpl;
import org.jbpm.workflow.core.node.ActionNode;
import org.jbpm.workflow.core.node.ForEachNode;
import org.jbpm.workflow.core.node.Split;
import org.kie.api.definition.process.Node;

import static java.lang.String.format;

/**
 * Feel validator.
 *
 * Every FEEL expression in the process is compiled while the process is built, so an expression that cannot compile -
 * or that declares an external function, which BPMN does not allow - fails the build rather than the process instance.
 */
public class FeelProcessValidator extends RuleFlowProcessValidator {

    private static final List<String> SCRIPT_TYPES = List.of(ExtendedNodeImpl.EVENT_NODE_ENTER, ExtendedNodeImpl.EVENT_NODE_EXIT);

    private static FeelProcessValidator INSTANCE;

    private FeelProcessValidator() {
        super();
    }

    public static FeelProcessValidator getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new FeelProcessValidator();
        }
        return INSTANCE;
    }

    @Override
    protected void validateNodes(Node[] nodes, List<ProcessValidationError> errors, RuleFlowProcess process) {
        super.validateNodes(nodes, errors, process);
        for (Node node : nodes) {
            validateSplitConstraints(node, errors, process);
            validateScripts(node, errors, process);
            validateCompletionCondition(node, errors, process);
        }
    }

    private void validateSplitConstraints(Node node, List<ProcessValidationError> errors, RuleFlowProcess process) {
        if (!(node instanceof Split)) {
            return;
        }
        final Split split = (Split) node;
        if (split.getType() != Split.TYPE_XOR && split.getType() != Split.TYPE_OR) {
            return;
        }
        for (Map.Entry<ConnectionRef, Collection<Constraint>> entry : split.getConstraints().entrySet()) {
            for (Constraint constraint : entry.getValue()) {
                if (constraint != null && ExpressionLanguages.FEEL.equals(constraint.getDialect())) {
                    validate(constraint.getConstraint(), node, errors, process);
                }
            }
        }
    }

    /**
     * Script tasks and onEntry/onExit scripts written in FEEL.
     */
    private void validateScripts(Node node, List<ProcessValidationError> errors, RuleFlowProcess process) {
        if (node instanceof ActionNode) {
            validateScript(((ActionNode) node).getAction(), node, errors, process);
        }
        if (node instanceof ExtendedNodeImpl) {
            for (String type : SCRIPT_TYPES) {
                List<DroolsAction> actions = ((ExtendedNodeImpl) node).getActions(type);
                if (actions != null) {
                    actions.forEach(action -> validateScript(action, node, errors, process));
                }
            }
        }
    }

    private void validateScript(DroolsAction action, Node node, List<ProcessValidationError> errors, RuleFlowProcess process) {
        if (action instanceof DroolsConsequenceAction && ExpressionLanguages.isFeel(((DroolsConsequenceAction) action).getDialect())) {
            validate(((DroolsConsequenceAction) action).getConsequence(), node, errors, process);
        }
    }

    private void validateCompletionCondition(Node node, List<ProcessValidationError> errors, RuleFlowProcess process) {
        if (node instanceof ForEachNode && ((ForEachNode) node).hasCompletionCondition()
                && ExpressionLanguages.isFeel(((ForEachNode) node).getCompletionConditionExpression().dialect())) {
            validate(((ForEachNode) node).getCompletionConditionExpression().expression(), node, errors, process);
        }
    }

    private void validate(String feelExpression, Node node, List<ProcessValidationError> errors, RuleFlowProcess process) {
        if (feelExpression == null || feelExpression.isBlank()) {
            return;
        }
        try {
            verifyFEELbyCompilingExpression(process.getVariableScope(), feelExpression);
        } catch (FeelCompilationException ex) {
            addErrorMessage(process, node, errors, format("Invalid FEEL expression: '%s'. %s", feelExpression, ex.getMessage()));
        }
    }

    /**
     * Instead of throwing a generic JavaParser compilation error (atm happens for invalid expression of dialect=JAVA)
     * use the FEEL compiler capabilities to verify if mere compilation of the FEEL expression may contain any error.
     */
    private void verifyFEELbyCompilingExpression(VariableScope variableScope, String feelExpression) {
        List<String> inputVariableNames = variableScope.getVariables().stream().map(Variable::getName).collect(Collectors.toList());
        try {
            BpmnFeel.compile(feelExpression, inputVariableNames);
        } catch (FeelReturnValueEvaluatorException ex) {
            throw new FeelCompilationException(ex.getMessage());
        }
    }

}
