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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.instance.context.variable.VariableScopeInstance;
import org.jbpm.process.instance.impl.EmtpyKogitoProcessContext;
import org.jbpm.workflow.instance.WorkflowProcessInstance;
import org.kie.api.runtime.Globals;
import org.kie.dmn.feel.lang.Type;
import org.kie.dmn.feel.lang.impl.MapBackedType;
import org.kie.dmn.feel.lang.types.BuiltInType;
import org.kie.kogito.internal.process.runtime.KogitoNodeInstance;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.internal.process.runtime.KogitoWorkflowProcessInstance;

/**
 * Builds the variable map a BPMN FEEL expression is evaluated against.
 *
 * <p>
 * Two scopes exist, mirroring the two MVEL has. An expression - a condition, a script, a transformation - sees the
 * process variables and the globals by name, and <code>kcontext</code>. A <code>#{...}</code> placeholder sees the
 * process variables and the four names MVEL interpolation resolves, and no <code>kcontext</code>.
 *
 * <p>
 * What <code>kcontext</code> and the instance names hold depends on {@link BpmnFeelSettings#isSandboxed()}.
 * Sandboxed, they are read-only views with a fixed set of keys:
 *
 * <pre>
 * kcontext.variables          the process variables, by name        (also bound at the top level)
 * kcontext.processInstance    id, processId, processName, processVersion, state, businessKey, description,
 *                             referenceId, startDate, endDate, slaDueDate, parentProcessInstanceId,
 *                             rootProcessInstanceId, rootProcessId, rootProcessVersion
 * kcontext.nodeInstance       id, nodeName, nodeDefinitionId, triggerTime, leaveTime, slaDueDate
 * kcontext.headers            the process instance headers, name to list of values
 * kcontext.contextData        the values the engine attached to this evaluation, by name
 *
 * processInstance             the same view as kcontext.processInstance     (interpolation only)
 * nodeInstance                the same view as kcontext.nodeInstance        (interpolation only)
 * processInstanceId           processInstance.id                            (interpolation only)
 * parentProcessInstanceId     processInstance.parentProcessInstanceId       (interpolation only)
 * </pre>
 *
 * Not sandboxed, <code>kcontext</code> is the {@link KogitoProcessContext} itself and the two instance names are the
 * instances themselves, exactly what MVEL binds. FEEL resolves any no-arg getter on them transitively, so the engine
 * internals are reachable - the KieBase and every definition in it through <code>kcontext.kieRuntime</code>, the model
 * source through <code>kcontext.processInstance.process.resource</code>, the whole instance tree through the node
 * instance back-references - and so is any public no-arg method, by name. That is the behaviour FEEL conditions had
 * before the sandbox existed, kept for an application that relies on it.
 *
 * <p>
 * Every value goes through the same translation, {@link BpmnFeelTypes#toFeel(Object)}, in both modes, so a
 * <code>java.util.Date</code> is a FEEL <code>date and time</code> whether it came from a variable or from the
 * instance. Writes are not expressible in FEEL, so nothing here is writable.
 *
 * <p>
 * The compiler is told the same shape. Sandboxed, <code>kcontext</code> and the two instance names are declared with
 * the types below, so a path into a key that does not exist - a typo, or a reach into the engine - fails when the
 * expression is compiled, at build time for a model and before the first evaluation otherwise. Every other name,
 * and the whole of <code>kcontext</code> when not sandboxed, is declared {@link BuiltInType#UNKNOWN}, which the
 * compiler resolves dynamically: any path below it is accepted and resolved as it is evaluated.
 */
public final class BpmnFeelVariables {

    public static final String KCONTEXT = "kcontext";

    /** The sandboxed process instance view: each key and the FEEL type of its value, in the documented order. */
    public static final Map<String, Type> PROCESS_INSTANCE_TYPES = fields(
            "id", BuiltInType.STRING,
            "processId", BuiltInType.STRING,
            "processName", BuiltInType.STRING,
            "processVersion", BuiltInType.STRING,
            "state", BuiltInType.NUMBER,
            "businessKey", BuiltInType.STRING,
            "description", BuiltInType.STRING,
            "referenceId", BuiltInType.STRING,
            "startDate", BuiltInType.DATE_TIME,
            "endDate", BuiltInType.DATE_TIME,
            "slaDueDate", BuiltInType.DATE_TIME,
            "parentProcessInstanceId", BuiltInType.STRING,
            "rootProcessInstanceId", BuiltInType.STRING,
            "rootProcessId", BuiltInType.STRING,
            "rootProcessVersion", BuiltInType.STRING);

    /** The sandboxed node instance view: each key and the FEEL type of its value, in the documented order. */
    public static final Map<String, Type> NODE_INSTANCE_TYPES = fields(
            "id", BuiltInType.STRING,
            "nodeName", BuiltInType.STRING,
            "nodeDefinitionId", BuiltInType.STRING,
            "triggerTime", BuiltInType.DATE_TIME,
            "leaveTime", BuiltInType.DATE_TIME,
            "slaDueDate", BuiltInType.DATE_TIME);

    /**
     * The sandboxed <code>kcontext</code>: each key and its type. The three maps whose keys depend on the process
     * or the instance - the variables, the headers, the context data - are dynamic, and resolved as evaluated.
     */
    public static final Map<String, Type> KCONTEXT_TYPES = fields(
            "variables", BuiltInType.UNKNOWN,
            "processInstance", new MapBackedType("processInstance", new LinkedHashMap<>(PROCESS_INSTANCE_TYPES)),
            "nodeInstance", new MapBackedType("nodeInstance", new LinkedHashMap<>(NODE_INSTANCE_TYPES)),
            "headers", BuiltInType.UNKNOWN,
            "contextData", BuiltInType.UNKNOWN);

    /** The keys of the sandboxed <code>kcontext</code>. */
    public static final List<String> KCONTEXT_KEYS = List.copyOf(KCONTEXT_TYPES.keySet());

    /** The keys of the sandboxed process instance view. */
    public static final List<String> PROCESS_INSTANCE_KEYS = List.copyOf(PROCESS_INSTANCE_TYPES.keySet());

    /** The keys of the sandboxed node instance view. */
    public static final List<String> NODE_INSTANCE_KEYS = List.copyOf(NODE_INSTANCE_TYPES.keySet());

    /** The names a placeholder sees besides the variables: the four MVEL interpolation resolves. */
    public static final List<String> INTERPOLATION_NAMES = List.of("processInstance", "nodeInstance", "processInstanceId", "parentProcessInstanceId");

    private BpmnFeelVariables() {
    }

    private static Map<String, Type> fields(Object... namesAndTypes) {
        Map<String, Type> fields = new LinkedHashMap<>();
        for (int i = 0; i < namesAndTypes.length; i += 2) {
            fields.put((String) namesAndTypes[i], (Type) namesAndTypes[i + 1]);
        }
        return Collections.unmodifiableMap(fields);
    }

    /**
     * The compile-time type of <code>kcontext</code>: the documented shape when sandboxed, else dynamic.
     */
    public static Type kcontextType(boolean sandboxed) {
        return sandboxed ? new MapBackedType(KCONTEXT, new LinkedHashMap<>(KCONTEXT_TYPES)) : BuiltInType.UNKNOWN;
    }

    /**
     * What the compiler is told about an expression's scope: the given names - the variables and globals - as dynamic,
     * and <code>kcontext</code>, whether or not it is among them, as {@link #kcontextType(boolean)}.
     */
    public static Map<String, Type> expressionTypes(Collection<String> names, boolean sandboxed) {
        Map<String, Type> types = new HashMap<>();
        names.forEach(name -> types.put(name, BuiltInType.UNKNOWN));
        types.put(KCONTEXT, kcontextType(sandboxed));
        return types;
    }

    /**
     * What the compiler is told about a placeholder's scope: the given names as dynamic, and the two instance names as
     * their documented views when sandboxed, else dynamic.
     */
    public static Map<String, Type> interpolationTypes(Collection<String> names, boolean sandboxed) {
        Map<String, Type> types = new HashMap<>();
        names.forEach(name -> types.put(name, BuiltInType.UNKNOWN));
        types.put("processInstance", sandboxed ? new MapBackedType("processInstance", new LinkedHashMap<>(PROCESS_INSTANCE_TYPES)) : BuiltInType.UNKNOWN);
        types.put("nodeInstance", sandboxed ? new MapBackedType("nodeInstance", new LinkedHashMap<>(NODE_INSTANCE_TYPES)) : BuiltInType.UNKNOWN);
        return types;
    }

    /**
     * The scope of an expression: variables and globals by name, and <code>kcontext</code>, in the configured mode.
     */
    public static Map<String, Object> of(KogitoProcessContext context) {
        return of(context, BpmnFeelSettings.isSandboxed());
    }

    /**
     * The scope of an expression, in the given mode.
     */
    public static Map<String, Object> of(KogitoProcessContext context, boolean sandboxed) {
        Map<String, Object> variables = new HashMap<>();
        variables.putAll(suppliedVariables(context));
        variables.putAll(processVariables(context));
        variables.putAll(globals(context));
        variables.put(KCONTEXT, sandboxed ? kcontext(context) : context);
        return variables;
    }

    /**
     * What a <code>#{...}</code> placeholder can see, in the configured mode.
     *
     * Deliberately narrower than {@link #of(KogitoProcessContext)}: interpolation substitutes a value into a string
     * field, and MVEL never gave it a <code>kcontext</code> or the globals - only the process variables and the four
     * names in {@link #INTERPOLATION_NAMES}. FEEL gets the same, so a document that changes language keeps the same
     * scope.
     */
    public static Map<String, Object> forInterpolation(KogitoProcessContext context) {
        return forInterpolation(context, BpmnFeelSettings.isSandboxed());
    }

    /**
     * What a <code>#{...}</code> placeholder can see, in the given mode.
     */
    public static Map<String, Object> forInterpolation(KogitoProcessContext context, boolean sandboxed) {
        Map<String, Object> variables = new HashMap<>();
        variables.putAll(suppliedVariables(context));
        variables.putAll(processVariables(context));
        KogitoProcessInstance instance = context.getProcessInstance();
        KogitoNodeInstance nodeInstance = context.getNodeInstance();
        variables.put("processInstance", sandboxed ? processInstance(instance) : instance);
        variables.put("nodeInstance", sandboxed ? nodeInstance(nodeInstance) : nodeInstance);
        variables.put("processInstanceId", instance == null ? null : instance.getStringId());
        variables.put("parentProcessInstanceId", instance == null ? null : instance.getParentProcessInstanceId());
        return variables;
    }

    /**
     * Values handed in directly, for an expression evaluated without a process instance behind it - a data-association
     * transformation, for instance, whose scope is the data being passed rather than the process variables.
     */
    private static Map<String, Object> suppliedVariables(KogitoProcessContext context) {
        return context instanceof EmtpyKogitoProcessContext ? asFeelValues(((EmtpyKogitoProcessContext) context).getVariables()) : Map.of();
    }

    /**
     * Every value a FEEL expression can reach goes through the same translation, so a date behaves the same whether it
     * came from a process variable or from the instance itself.
     */
    private static Map<String, Object> asFeelValues(Map<String, Object> values) {
        Map<String, Object> translated = new HashMap<>();
        if (values != null) {
            values.forEach((name, value) -> translated.put(name, BpmnFeelTypes.toFeel(value)));
        }
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
     * The sandboxed <code>kcontext</code>: the keys in {@link #KCONTEXT_KEYS}, and nothing the engine would rather
     * keep to itself.
     */
    static Map<String, Object> kcontext(KogitoProcessContext context) {
        Map<String, Object> kcontext = new LinkedHashMap<>();
        kcontext.put("variables", processVariables(context));
        kcontext.put("processInstance", processInstance(context.getProcessInstance()));
        kcontext.put("nodeInstance", nodeInstance(context.getNodeInstance()));
        kcontext.put("headers", context.getProcessInstance() == null ? Map.of() : context.getHeaders());
        kcontext.put("contextData", asFeelValues(context.getContextData()));
        return kcontext;
    }

    /**
     * The sandboxed view of a process instance: the keys in {@link #PROCESS_INSTANCE_KEYS}, every one present even
     * when its value is unknown, so an expression can rely on the shape. Empty when there is no instance.
     */
    static Map<String, Object> processInstance(KogitoProcessInstance instance) {
        if (instance == null) {
            return Map.of();
        }
        KogitoWorkflowProcessInstance workflow = instance instanceof KogitoWorkflowProcessInstance ? (KogitoWorkflowProcessInstance) instance : null;
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
        values.put("endDate", workflow == null ? null : BpmnFeelTypes.toFeel(workflow.getEndDate()));
        values.put("slaDueDate", workflow == null ? null : BpmnFeelTypes.toFeel(workflow.getSlaDueDate()));
        values.put("parentProcessInstanceId", instance.getParentProcessInstanceId());
        values.put("rootProcessInstanceId", instance.getRootProcessInstanceId());
        values.put("rootProcessId", instance.getRootProcessId());
        values.put("rootProcessVersion", instance.getRootProcessVersion());
        return values;
    }

    /**
     * The sandboxed view of a node instance: the keys in {@link #NODE_INSTANCE_KEYS}. Empty when there is none.
     */
    static Map<String, Object> nodeInstance(KogitoNodeInstance instance) {
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
