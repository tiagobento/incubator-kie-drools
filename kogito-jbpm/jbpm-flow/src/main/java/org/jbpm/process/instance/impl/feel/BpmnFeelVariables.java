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
package org.jbpm.process.instance.impl.feel;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.process.instance.impl.EmtpyKogitoProcessContext;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.kie.api.runtime.Globals;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;

/**
 * Builds the variable map a BPMN FEEL expression is evaluated against.
 *
 * Process variables and globals are bound by their own name, as they always have been. <code>kcontext</code> is bound
 * as a curated, read-only context rather than as the {@link KogitoProcessContext} itself: FEEL resolves any no-arg
 * getter transitively, so binding the object would make the engine internals reachable - the KieBase and every rule and
 * process definition in it through <code>kieRuntime</code>, the model source through
 * <code>processInstance.process.resource</code>, and the whole instance tree through the node instance
 * back-references. Writes are not expressible in FEEL at all, so nothing here is writable.
 */
public final class BpmnFeelVariables {

    public static final String KCONTEXT = "kcontext";

    private BpmnFeelVariables() {
    }

    public static Map<String, Object> of(KogitoProcessContext context) {
        Map<String, Object> variables = new HashMap<>();
        variables.putAll(suppliedVariables(context));
        variables.putAll(processVariables(context));
        variables.putAll(globals(context));
        variables.put(KCONTEXT, kcontext(context));
        return variables;
    }

    /**
     * Values handed in directly, for an expression evaluated without a process instance behind it - a data-association
     * transformation, for instance, whose scope is the data being passed rather than the process variables.
     */
    /**
     * What a <code>#{...}</code> placeholder can see.
     *
     * Deliberately narrower than {@link #of(KogitoProcessContext)}: interpolation substitutes a value into a string
     * field, and MVEL never gave it a <code>kcontext</code> or the globals - only the process variables and the four
     * names below. FEEL gets the same, so a document that changes language keeps the same scope; the instance views
     * are the curated maps rather than the objects themselves.
     */
    public static Map<String, Object> forInterpolation(KogitoProcessContext context) {
        Map<String, Object> variables = new HashMap<>();
        variables.putAll(suppliedVariables(context));
        variables.putAll(processVariables(context));
        variables.put("processInstance", processInstance(context.getProcessInstance()));
        variables.put("nodeInstance", nodeInstance(context.getNodeInstance()));
        variables.put("processInstanceId", context.getProcessInstance() == null ? null : context.getProcessInstance().getStringId());
        variables.put("parentProcessInstanceId", context.getProcessInstance() == null ? null : context.getProcessInstance().getParentProcessInstanceId());
        return variables;
    }

    private static Map<String, Object> suppliedVariables(KogitoProcessContext context) {
        return context instanceof EmtpyKogitoProcessContext ? asFeelValues(((EmtpyKogitoProcessContext) context).getVariables()) : Map.of();
    }

    /**
     * Every value a FEEL expression can reach goes through the same translation, so a date behaves the same whether it
     * came from a process variable or from the instance itself.
     */
    private static Map<String, Object> asFeelValues(Map<String, Object> values) {
        Map<String, Object> translated = new HashMap<>();
        values.forEach((name, value) -> translated.put(name, BpmnFeelTypes.toFeel(value)));
        return translated;
    }

    private static Map<String, Object> processVariables(KogitoProcessContext context) {
        if (!(context.getProcessInstance() instanceof WorkflowProcessInstance) || context.getProcessInstance().getProcess() == null) {
            return Map.of();
        }
        VariableScopeInstance variableScope = (VariableScopeInstance) ((WorkflowProcessInstance) context.getProcessInstance())
                .getContextInstance(VariableScope.VARIABLE_SCOPE);
        return variableScope == null ? Map.of() : asFeelValues(variableScope.getVariables());
    }

    private static Map<String, Object> globals(KogitoProcessContext context) {
        Globals globals = context.getKieRuntime() == null ? null : context.getKieRuntime().getGlobals();
        if (globals == null || globals.getGlobalKeys() == null) {
            return Map.of();
        }
        Map<String, Object> values = new HashMap<>();
        for (String key : globals.getGlobalKeys()) {
            values.put(key, globals.get(key));
        }
        return values;
    }

    /**
     * The five keys a BPMN FEEL expression may read: <code>variables</code>, <code>processInstance</code>,
     * <code>nodeInstance</code>, <code>headers</code> and <code>contextData</code>.
     */
    private static Map<String, Object> kcontext(KogitoProcessContext context) {
        Map<String, Object> kcontext = new LinkedHashMap<>();
        kcontext.put("variables", processVariables(context));
        kcontext.put("processInstance", processInstance(context.getProcessInstance()));
        kcontext.put("nodeInstance", nodeInstance(context.getNodeInstance()));
        kcontext.put("headers", context.getProcessInstance() == null ? Map.of() : context.getHeaders());
        kcontext.put("contextData", context.getContextData());
        return kcontext;
    }

    private static Map<String, Object> processInstance(KogitoProcessInstance instance) {
        if (instance == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", instance.getStringId());
        values.put("processId", instance.getProcessId());
        values.put("processName", instance.getProcessName());
        values.put("processVersion", instance.getProcessVersion());
        values.put("state", instance.getState());
        values.put("businessKey", instance.getBusinessKey());
        values.put("description", instance.getDescription());
        values.put("referenceId", instance.getReferenceId());
        values.put("startDate", BpmnFeelTypes.toFeel(instance.getStartDate()));
        values.put("parentProcessInstanceId", instance.getParentProcessInstanceId());
        values.put("rootProcessInstanceId", instance.getRootProcessInstanceId());
        values.put("rootProcessId", instance.getRootProcessId());
        values.put("rootProcessVersion", instance.getRootProcessVersion());
        return values;
    }

    private static Map<String, Object> nodeInstance(KogitoNodeInstance instance) {
        if (instance == null) {
            return Map.of();
        }
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("id", instance.getStringId());
        values.put("nodeName", instance.getNodeName());
        values.put("nodeDefinitionId", instance.getNodeDefinitionId());
        values.put("triggerTime", BpmnFeelTypes.toFeel(instance.getTriggerTime()));
        values.put("leaveTime", BpmnFeelTypes.toFeel(instance.getLeaveTime()));
        values.put("slaDueDate", BpmnFeelTypes.toFeel(instance.getSlaDueDate()));
        return values;
    }

}
