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

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbpm.process.instance.FeelTestProcess;
import org.jbpm.process.instance.impl.EmtpyKogitoProcessContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

import static org.assertj.core.api.Assertions.assertThat;

class BpmnFeelVariablesTest {

    @AfterEach
    void backToTheConfiguredMode() {
        BpmnFeelSettings.setSandboxed(null);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> kcontextOf(Map<String, Object> variables) {
        return (Map<String, Object>) BpmnFeelVariables.of(new EmtpyKogitoProcessContext(variables)).get(BpmnFeelVariables.KCONTEXT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> sandboxedKcontext(KogitoProcessContext context) {
        return (Map<String, Object>) BpmnFeelVariables.of(context, true).get(BpmnFeelVariables.KCONTEXT);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> view(Map<String, Object> kcontext, String name) {
        return (Map<String, Object>) kcontext.get(name);
    }

    // ---------------------------------------------------------------- the sandboxed shape, on an empty context

    @Test
    void kcontextExposesExactlyTheDocumentedKeys() {
        assertThat(kcontextOf(Map.of())).containsOnlyKeys(BpmnFeelVariables.KCONTEXT_KEYS);
        assertThat(BpmnFeelVariables.KCONTEXT_KEYS).containsExactly("variables", "processInstance", "nodeInstance", "headers", "contextData");
    }

    @Test
    void kcontextDoesNotReachTheEngine() {
        assertThat(kcontextOf(Map.of()))
                .doesNotContainKey("kieRuntime")
                .doesNotContainKey("kogitoProcessRuntime")
                .doesNotContainKey("kieSession")
                .doesNotContainKey("kieBase");
    }

    @Test
    void theProcessInstanceViewDoesNotReachTheProcessDefinitionOrTheModelSource() {
        // kcontext.processInstance.process.resource used to hand an expression the model it came from
        Map<String, Object> processInstance = FeelTestProcess.inAction(Map.of(), context -> view(sandboxedKcontext(context), "processInstance"));
        assertThat(processInstance)
                .doesNotContainKey("process")
                .doesNotContainKey("resource")
                .doesNotContainKey("variables")
                .doesNotContainKey("knowledgeRuntime")
                .doesNotContainKey("nodeInstances");
    }

    @Test
    void theNodeInstanceViewDoesNotReachBackUpTheInstanceTree() {
        Map<String, Object> nodeInstance = FeelTestProcess.inAction(Map.of(), context -> view(sandboxedKcontext(context), "nodeInstance"));
        assertThat(nodeInstance)
                .doesNotContainKey("node")
                .doesNotContainKey("processInstance")
                .doesNotContainKey("nodeInstanceContainer");
    }

    @Test
    void variablesHandedInDirectlyAreBoundByName() {
        // a transformation is evaluated with no process instance behind it: its scope is the data being passed
        Map<String, Object> variables = BpmnFeelVariables.of(new EmtpyKogitoProcessContext(Map.of("first", "hello", "second", 2)));
        assertThat(variables).containsEntry("first", "hello").containsEntry("second", 2);
    }

    @Test
    void aContextWithNoProcessInstanceStillProducesAUsableKcontext() {
        Map<String, Object> kcontext = kcontextOf(Map.of("x", 1));
        assertThat(kcontext.get("processInstance")).isEqualTo(Map.of());
        assertThat(kcontext.get("nodeInstance")).isEqualTo(Map.of());
        assertThat(kcontext.get("headers")).isEqualTo(Map.of());
        assertThat(kcontext.get("contextData")).isEqualTo(Map.of());
    }

    // ---------------------------------------------------------------- the sandboxed shape, on a running instance

    @Test
    void theProcessInstanceViewHasExactlyTheDocumentedKeysInOrder() {
        Map<String, Object> processInstance = FeelTestProcess.inAction(Map.of(), context -> view(sandboxedKcontext(context), "processInstance"));
        assertThat(processInstance.keySet()).containsExactlyElementsOf(BpmnFeelVariables.PROCESS_INSTANCE_KEYS);
    }

    @Test
    void theNodeInstanceViewHasExactlyTheDocumentedKeysInOrder() {
        Map<String, Object> nodeInstance = FeelTestProcess.inAction(Map.of(), context -> view(sandboxedKcontext(context), "nodeInstance"));
        assertThat(nodeInstance.keySet()).containsExactlyElementsOf(BpmnFeelVariables.NODE_INSTANCE_KEYS);
    }

    @Test
    void theViewsCarryTheInstanceValues() {
        Map<String, Object> kcontext = FeelTestProcess.inAction(Map.of(), context -> {
            Map<String, Object> copy = new HashMap<>(sandboxedKcontext(context));
            copy.put("expectedId", context.getProcessInstance().getStringId());
            copy.put("expectedNodeInstanceId", context.getNodeInstance().getStringId());
            return copy;
        });
        Map<String, Object> processInstance = view(kcontext, "processInstance");
        assertThat(processInstance)
                .containsEntry("id", kcontext.get("expectedId"))
                .containsEntry("processId", FeelTestProcess.PROCESS_ID)
                .containsEntry("processName", FeelTestProcess.PROCESS_NAME)
                .containsEntry("processVersion", FeelTestProcess.PROCESS_VERSION)
                .containsEntry("state", 1);
        assertThat(processInstance.get("startDate")).isInstanceOf(LocalDateTime.class);
        assertThat(processInstance.get("endDate")).isNull();
        Map<String, Object> nodeInstance = view(kcontext, "nodeInstance");
        assertThat(nodeInstance)
                .containsEntry("id", kcontext.get("expectedNodeInstanceId"))
                .containsEntry("nodeName", FeelTestProcess.ACTION_NODE_NAME);
        assertThat(nodeInstance.get("triggerTime")).isInstanceOf(LocalDateTime.class);
        assertThat(nodeInstance.get("leaveTime")).isNull();
    }

    @Test
    void kcontextVariablesMirrorTheTopLevelVariables() {
        Map<String, Object> scope = FeelTestProcess.inAction(Map.of("x", 1, "who", "bob"), context -> BpmnFeelVariables.of(context, true));
        assertThat(scope).containsEntry("x", 1).containsEntry("who", "bob");
        assertThat(view(view(scope, BpmnFeelVariables.KCONTEXT), "variables")).containsEntry("x", 1).containsEntry("who", "bob");
    }

    @Test
    void contextDataIsTranslatedLikeEverythingElse() {
        Date now = new Date();
        Map<String, Object> kcontext = FeelTestProcess.inAction(Map.of(), context -> {
            context.getContextData().put("when", now);
            return sandboxedKcontext(context);
        });
        assertThat(view(kcontext, "contextData").get("when")).isInstanceOf(LocalDateTime.class);
    }

    @Test
    void everyDocumentedKeyIsReadableFromFeel() {
        FeelTestProcess.inAction(Map.of(), context -> {
            Map<String, Object> scope = BpmnFeelVariables.of(context, true);
            for (String key : BpmnFeelVariables.PROCESS_INSTANCE_KEYS) {
                assertThat(BpmnFeel.compile("kcontext.processInstance." + key, List.of(BpmnFeelVariables.KCONTEXT), true)).isNotNull();
            }
            for (String key : BpmnFeelVariables.NODE_INSTANCE_KEYS) {
                assertThat(BpmnFeel.compile("kcontext.nodeInstance." + key, List.of(BpmnFeelVariables.KCONTEXT), true)).isNotNull();
            }
            assertThat(BpmnFeel.newFeel(true).evaluate("kcontext.processInstance.processId", scope)).isEqualTo(FeelTestProcess.PROCESS_ID);
            assertThat(BpmnFeel.newFeel(true).evaluate("kcontext.nodeInstance.nodeName", scope)).isEqualTo(FeelTestProcess.ACTION_NODE_NAME);
            assertThat(BpmnFeel.newFeel(true).evaluate("kcontext.processInstance.startDate <= now()", scope)).isEqualTo(true);
            return null;
        });
    }

    @Test
    void aVariableHoldingADateArrivesAsAFeelDateAndTime() {
        Map<String, Object> scope = FeelTestProcess.inAction(Map.of("when", new Date()), context -> BpmnFeelVariables.of(context, true));
        assertThat(scope.get("when")).isInstanceOf(LocalDateTime.class);
        assertThat(view(view(scope, BpmnFeelVariables.KCONTEXT), "variables").get("when")).isInstanceOf(LocalDateTime.class);
    }

    // ---------------------------------------------------------------- not sandboxed: what MVEL gets

    @Test
    void notSandboxedKcontextIsTheProcessContextItself() {
        KogitoProcessContext context = new EmtpyKogitoProcessContext(Map.of("x", 1));
        assertThat(BpmnFeelVariables.of(context, false).get(BpmnFeelVariables.KCONTEXT)).isSameAs(context);
    }

    @Test
    void notSandboxedFeelReachesTheEngineThroughKcontext() {
        // the behaviour FEEL conditions had before the sandbox: every getter resolves, all the way down
        FeelTestProcess.inAction(Map.of(), context -> {
            Map<String, Object> scope = BpmnFeelVariables.of(context, false);
            assertThat(BpmnFeel.newFeel(false).evaluate("kcontext.kieRuntime", scope)).isNotNull();
            assertThat(BpmnFeel.newFeel(false).evaluate("kcontext.processInstance.process.id", scope)).isEqualTo(FeelTestProcess.PROCESS_ID);
            assertThat(BpmnFeel.newFeel(false).evaluate("kcontext.nodeInstance.node.name", scope)).isEqualTo(FeelTestProcess.ACTION_NODE_NAME);
            assertThat(BpmnFeel.newFeel(false).evaluate("kcontext.nodeInstance.processInstance.processId", scope)).isEqualTo(FeelTestProcess.PROCESS_ID);
            return null;
        });
    }

    @Test
    void notSandboxedTheVariablesAreStillTranslated() {
        // the translation is a fix, not a safety measure: a date is a date in both modes
        Map<String, Object> scope = FeelTestProcess.inAction(Map.of("when", new Date()), context -> BpmnFeelVariables.of(context, false));
        assertThat(scope.get("when")).isInstanceOf(LocalDateTime.class);
    }

    @Test
    void theConfiguredModeIsTheDefaultForBothScopes() {
        KogitoProcessContext context = new EmtpyKogitoProcessContext(Map.of());
        BpmnFeelSettings.setSandboxed(false);
        assertThat(BpmnFeelVariables.of(context).get(BpmnFeelVariables.KCONTEXT)).isSameAs(context);
        assertThat(BpmnFeelVariables.forInterpolation(context).get("processInstance")).isNull();
        BpmnFeelSettings.setSandboxed(true);
        assertThat(BpmnFeelVariables.of(context).get(BpmnFeelVariables.KCONTEXT)).isInstanceOf(Map.class);
        assertThat(BpmnFeelVariables.forInterpolation(context).get("processInstance")).isEqualTo(Map.of());
    }

    // ---------------------------------------------------------------- interpolation

    @Test
    void interpolationSeesNoKcontextAndNoGlobals() {
        // MVEL interpolation never had either: NodeInstanceResolverFactory and ProcessInstanceResolverFactory resolve
        // process variables and four fixed names, nothing else. FEEL must not be given a wider scope than the language
        // it is replacing, or a document cannot change language without changing meaning.
        Map<String, Object> scope = BpmnFeelVariables.forInterpolation(new EmtpyKogitoProcessContext(Map.of("x", 1)));
        assertThat(scope).doesNotContainKey(BpmnFeelVariables.KCONTEXT);
    }

    @Test
    void interpolationSeesTheProcessVariablesAndTheSameFourNamesMvelResolves() {
        Map<String, Object> scope = BpmnFeelVariables.forInterpolation(new EmtpyKogitoProcessContext(Map.of("x", 1)));
        assertThat(scope).containsEntry("x", 1);
        assertThat(scope).containsOnlyKeys("x", "processInstance", "nodeInstance", "processInstanceId", "parentProcessInstanceId");
        assertThat(BpmnFeelVariables.INTERPOLATION_NAMES).containsExactly("processInstance", "nodeInstance", "processInstanceId", "parentProcessInstanceId");
    }

    @Test
    void interpolationExposesTheInstancesAsCuratedViewsToo() {
        Map<String, Object> scope = BpmnFeelVariables.forInterpolation(new EmtpyKogitoProcessContext(Map.of()));
        assertThat(scope.get("processInstance")).isEqualTo(Map.of());
        assertThat(scope.get("nodeInstance")).isEqualTo(Map.of());
    }

    @Test
    void interpolationIdsAgreeWithTheViews() {
        // processInstanceId is kept for MVEL parity; it is the same value as processInstance.id
        Map<String, Object> scope = FeelTestProcess.inAction(Map.of(), context -> BpmnFeelVariables.forInterpolation(context, true));
        assertThat(scope.get("processInstanceId")).isNotNull().isEqualTo(view(scope, "processInstance").get("id"));
        assertThat(scope.get("parentProcessInstanceId")).isEqualTo(view(scope, "processInstance").get("parentProcessInstanceId"));
        assertThat(view(scope, "processInstance").keySet()).containsExactlyElementsOf(BpmnFeelVariables.PROCESS_INSTANCE_KEYS);
        assertThat(view(scope, "nodeInstance").keySet()).containsExactlyElementsOf(BpmnFeelVariables.NODE_INSTANCE_KEYS);
    }

    @Test
    void notSandboxedInterpolationBindsTheInstancesThemselves() {
        FeelTestProcess.inAction(Map.of(), context -> {
            Map<String, Object> scope = BpmnFeelVariables.forInterpolation(context, false);
            assertThat(scope.get("processInstance")).isSameAs(context.getProcessInstance());
            assertThat(scope.get("nodeInstance")).isSameAs(context.getNodeInstance());
            assertThat(scope.get("processInstanceId")).isEqualTo(context.getProcessInstance().getStringId());
            assertThat(scope).doesNotContainKey(BpmnFeelVariables.KCONTEXT);
            return null;
        });
    }

    @Test
    void theExpressionCanActuallyReadWhatIsBound() {
        assertThat(BpmnFeel.newFeel().evaluate("x + 1", BpmnFeelVariables.of(new EmtpyKogitoProcessContext(Map.of("x", 41)))))
                .isEqualTo(BigDecimal.valueOf(42));
        assertThat(BpmnFeel.newFeel().evaluate("kcontext.processInstance", BpmnFeelVariables.of(new EmtpyKogitoProcessContext(Map.of()))))
                .isEqualTo(Map.of());
    }
}
