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

import java.util.Map;

import org.jbpm.process.instance.impl.EmtpyKogitoProcessContext;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a BPMN FEEL expression can see.
 *
 * The point of the curated context is what it leaves out: FEEL resolves any no-arg getter transitively, so binding the
 * KogitoProcessContext itself put the KieBase, the model source and the whole instance tree within reach of an
 * expression. These tests pin the surface so it cannot quietly grow back.
 */
class BpmnFeelVariablesTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> kcontextOf(Map<String, Object> variables) {
        return (Map<String, Object>) BpmnFeelVariables.of(new EmtpyKogitoProcessContext(variables)).get(BpmnFeelVariables.KCONTEXT);
    }

    @Test
    void kcontextExposesExactlyTheFiveDocumentedKeys() {
        assertThat(kcontextOf(Map.of())).containsOnlyKeys("variables", "processInstance", "nodeInstance", "headers", "contextData");
    }

    @Test
    void kcontextDoesNotReachTheEngine() {
        assertThat(kcontextOf(Map.of()))
                .doesNotContainKey("kieRuntime")
                .doesNotContainKey("kogitoProcessRuntime")
                .doesNotContainKey("kieSession")
                .doesNotContainKey("kieBase");
    }

    @SuppressWarnings("unchecked")
    @Test
    void theProcessInstanceViewDoesNotReachTheProcessDefinitionOrTheModelSource() {
        // kcontext.processInstance.process.resource used to hand an expression the model it came from
        assertThat(kcontextOf(Map.of()).get("processInstance")).isInstanceOf(Map.class);
        assertThat((Map<String, Object>) kcontextOf(Map.of()).get("processInstance"))
                .doesNotContainKey("process")
                .doesNotContainKey("resource")
                .doesNotContainKey("variables");
    }

    @SuppressWarnings("unchecked")
    @Test
    void theNodeInstanceViewDoesNotReachBackUpTheInstanceTree() {
        assertThat((Map<String, Object>) kcontextOf(Map.of()).get("nodeInstance"))
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
    }

    @Test
    void interpolationExposesTheInstancesAsCuratedViewsToo() {
        Map<String, Object> scope = BpmnFeelVariables.forInterpolation(new EmtpyKogitoProcessContext(Map.of()));
        assertThat(scope.get("processInstance")).isEqualTo(Map.of());
        assertThat(scope.get("nodeInstance")).isEqualTo(Map.of());
    }

    @Test
    void theExpressionCanActuallyReadWhatIsBound() {
        assertThat(BpmnFeel.newFeel().evaluate("x + 1", BpmnFeelVariables.of(new EmtpyKogitoProcessContext(Map.of("x", 41)))))
                .isEqualTo(java.math.BigDecimal.valueOf(42));
        assertThat(BpmnFeel.newFeel().evaluate("kcontext.processInstance", BpmnFeelVariables.of(new EmtpyKogitoProcessContext(Map.of()))))
                .isEqualTo(Map.of());
    }
}
