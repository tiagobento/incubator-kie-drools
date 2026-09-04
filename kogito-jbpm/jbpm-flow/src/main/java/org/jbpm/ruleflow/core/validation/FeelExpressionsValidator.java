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
package org.jbpm.ruleflow.core.validation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.validation.ProcessValidationError;
import org.jbpm.process.core.validation.impl.ProcessValidationErrorImpl;
import org.jbpm.process.instance.impl.FeelReturnValueEvaluatorException;
import org.jbpm.process.instance.impl.ReturnValueConstraintEvaluator;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.jbpm.process.instance.impl.feel.BpmnFeel;
import org.jbpm.process.instance.impl.feel.BpmnFeelSettings;
import org.jbpm.process.instance.impl.feel.BpmnFeelVariables;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.util.ExpressionLanguages;
import org.jbpm.workflow.core.Constraint;
import org.jbpm.workflow.core.DroolsAction;
import org.jbpm.workflow.core.impl.DroolsConsequenceAction;
import org.jbpm.workflow.core.impl.ExtendedNodeImpl;
import org.jbpm.workflow.core.node.ActionNode;
import org.jbpm.workflow.core.node.ForEachNode;
import org.jbpm.workflow.core.node.Split;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.NodeContainer;
import org.kie.dmn.feel.exceptions.ExternalFunctionsDisabledException;
import org.kie.kogito.process.validation.ValidationException;

import static java.lang.String.format;

/**
 * Compiles every FEEL expression a parsed process carries - split constraints, script tasks, on-entry and on-exit
 * scripts, multi-instance completion conditions - so that one that does not compile fails the build rather than the
 * instance.
 *
 * <p>
 * Each expression is compiled with what it will see at runtime: the process variables, and <code>kcontext</code> in
 * the shape the configured mode gives it. A reach past the sandboxed view, or an <code>external</code> function
 * definition, is a compile error, so a sandboxed application refuses at build time what it would refuse at runtime.
 *
 * <p>
 * This is a step of the two build paths - parsing a model for code generation, and building one in memory - and not
 * of {@link RuleFlowProcessValidator}, which generated code runs again when a process is registered. By then the
 * expressions are compiled objects the build already checked, and the application's configuration may not be in
 * place yet; checking them there would be redundant at best.
 */
public final class FeelExpressionsValidator {

    private static final List<String> SCRIPT_EVENT_TYPES = List.of(ExtendedNodeImpl.EVENT_NODE_ENTER, ExtendedNodeImpl.EVENT_NODE_EXIT);

    private FeelExpressionsValidator() {
    }

    /**
     * Throws when any FEEL expression in the process does not compile in the configured mode.
     */
    public static void check(RuleFlowProcess process) {
        List<ProcessValidationError> errors = validate(process);
        if (!errors.isEmpty()) {
            throw new ValidationException(process.getId(), errors);
        }
    }

    /**
     * One error per FEEL expression that does not compile in the configured mode; empty when all of them do.
     */
    public static List<ProcessValidationError> validate(RuleFlowProcess process) {
        List<ProcessValidationError> errors = new ArrayList<>();
        List<String> variableNames = process.getVariableScope().getVariables().stream().map(Variable::getName).collect(Collectors.toList());
        boolean sandboxed = BpmnFeelSettings.isSandboxed();
        validate(process.getNodes(), process, variableNames, sandboxed, errors);
        return errors;
    }

    private static void validate(Node[] nodes, RuleFlowProcess process, List<String> variableNames, boolean sandboxed, List<ProcessValidationError> errors) {
        for (Node node : nodes) {
            if (node instanceof Split) {
                Split split = (Split) node;
                if (split.getType() == Split.TYPE_XOR || split.getType() == Split.TYPE_OR) {
                    for (Collection<Constraint> constraints : split.getConstraints().values()) {
                        for (Constraint constraint : constraints) {
                            validateConstraint(constraint, node, process, variableNames, sandboxed, errors);
                        }
                    }
                }
            }
            if (node instanceof ActionNode) {
                validateScript(((ActionNode) node).getAction(), node, process, variableNames, sandboxed, errors);
            }
            if (node instanceof ExtendedNodeImpl) {
                for (String type : SCRIPT_EVENT_TYPES) {
                    List<DroolsAction> actions = ((ExtendedNodeImpl) node).getActions(type);
                    if (actions != null) {
                        actions.forEach(action -> validateScript(action, node, process, variableNames, sandboxed, errors));
                    }
                }
            }
            if (node instanceof ForEachNode && ((ForEachNode) node).hasCompletionCondition()) {
                ReturnValueEvaluator completion = ((ForEachNode) node).getCompletionConditionExpression();
                if (ExpressionLanguages.isFeel(completion.dialect())) {
                    compile(completion.expression(), node, process, variableNames, sandboxed, errors);
                }
            }
            if (node instanceof NodeContainer) {
                validate(((NodeContainer) node).getNodes(), process, variableNames, sandboxed, errors);
            }
        }
    }

    private static void validateConstraint(Constraint constraint, Node node, RuleFlowProcess process, List<String> variableNames, boolean sandboxed, List<ProcessValidationError> errors) {
        if (constraint == null) {
            return;
        }
        // a constraint already built into an evaluator carries its expression there, and a placeholder as its text
        if (constraint instanceof ReturnValueConstraintEvaluator && ((ReturnValueConstraintEvaluator) constraint).getReturnValueEvaluator() != null) {
            ReturnValueEvaluator evaluator = ((ReturnValueConstraintEvaluator) constraint).getReturnValueEvaluator();
            if (ExpressionLanguages.isFeel(evaluator.dialect())) {
                compile(evaluator.expression(), node, process, variableNames, sandboxed, errors);
            }
        } else if (ExpressionLanguages.isFeel(constraint.getDialect())) {
            compile(constraint.getConstraint(), node, process, variableNames, sandboxed, errors);
        }
    }

    private static void validateScript(DroolsAction action, Node node, RuleFlowProcess process, List<String> variableNames, boolean sandboxed, List<ProcessValidationError> errors) {
        if (action instanceof DroolsConsequenceAction && ExpressionLanguages.isFeel(((DroolsConsequenceAction) action).getDialect())) {
            compile(((DroolsConsequenceAction) action).getConsequence(), node, process, variableNames, sandboxed, errors);
        }
    }

    private static void compile(String expression, Node node, RuleFlowProcess process, List<String> variableNames, boolean sandboxed, List<ProcessValidationError> errors) {
        if (expression == null || expression.isBlank()) {
            return;
        }
        try {
            BpmnFeel.compile(expression, BpmnFeelVariables.expressionTypes(variableNames, sandboxed), sandboxed);
        } catch (FeelReturnValueEvaluatorException | ExternalFunctionsDisabledException e) {
            errors.add(new ProcessValidationErrorImpl(process,
                    format("Node '%s' [%s] Invalid FEEL expression: '%s'. %s", node.getName(), node.getId().toExternalFormat(), expression, e.getMessage())));
        }
    }
}
