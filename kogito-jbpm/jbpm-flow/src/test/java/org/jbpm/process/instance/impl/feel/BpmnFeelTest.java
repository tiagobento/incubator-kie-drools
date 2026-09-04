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
import java.util.List;

import org.jbpm.process.instance.impl.FeelReturnValueEvaluatorException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.dmn.feel.exceptions.ExternalFunctionsDisabledException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class BpmnFeelTest {

    private static final String EXTERNAL_MAX =
            "{ maximum : function( v1, v2 ) external { java : { class : \"java.lang.Math\", method signature: \"max(long,long)\" } }, the max : maximum( 10, 20 ) }.the max";

    @AfterEach
    void backToTheConfiguredMode() {
        BpmnFeelSettings.setSandboxed(null);
    }

    @Test
    void theBpmnEngineRefusesExternalFunctions() {
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class).isThrownBy(() -> BpmnFeel.compile(EXTERNAL_MAX));
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class).isThrownBy(() -> BpmnFeel.newFeel().evaluate(EXTERNAL_MAX));
    }

    @Test
    void notSandboxedTheEngineRunsExternalFunctions() {
        // the behaviour a stock FEEL engine has, and BPMN FEEL had before the sandbox
        BpmnFeelSettings.setSandboxed(false);
        assertThat(BpmnFeel.compile(EXTERNAL_MAX)).isNotNull();
        assertThat(BpmnFeel.newFeel().evaluate(EXTERNAL_MAX)).isEqualTo(BigDecimal.valueOf(20));
    }

    @Test
    void theModeCanBeGivenExplicitlyWhateverIsConfigured() {
        assertThat(BpmnFeel.newFeel(false).evaluate(EXTERNAL_MAX)).isEqualTo(BigDecimal.valueOf(20));
        assertThat(BpmnFeel.compile(EXTERNAL_MAX, List.of(), false)).isNotNull();
        BpmnFeelSettings.setSandboxed(false);
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class).isThrownBy(() -> BpmnFeel.newFeel(true).evaluate(EXTERNAL_MAX));
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class).isThrownBy(() -> BpmnFeel.compile(EXTERNAL_MAX, List.of(), true));
    }

    @Test
    void everythingElseIsTheSameInBothModes() {
        for (boolean sandboxed : new boolean[] { true, false }) {
            assertThat(BpmnFeel.newFeel(sandboxed).evaluate("someVariable + 1", java.util.Map.of("someVariable", 41))).isEqualTo(BigDecimal.valueOf(42));
            assertThat(BpmnFeel.newFeel(sandboxed).evaluate("now()")).isNotNull();
            assertThatExceptionOfType(FeelReturnValueEvaluatorException.class).isThrownBy(() -> BpmnFeel.compile("1 +", List.of(), sandboxed));
        }
    }

    @Test
    void anInvalidExpressionFailsToCompile() {
        assertThatExceptionOfType(FeelReturnValueEvaluatorException.class)
                .isThrownBy(() -> BpmnFeel.compile("1 +"))
                .withMessageContaining("is not valid");
    }

    @Test
    void aReferenceToAnUndeclaredVariableFailsToCompile() {
        assertThatExceptionOfType(FeelReturnValueEvaluatorException.class)
                .isThrownBy(() -> BpmnFeel.compile("someVariable + 1"))
                .withMessageContaining("Unknown variable");
    }

    @Test
    void aDeclaredVariableCompilesAndEvaluates() {
        assertThat(BpmnFeel.newFeel().evaluate("someVariable + 1", java.util.Map.of("someVariable", 41)))
                .isEqualTo(BigDecimal.valueOf(42));
        assertThat(BpmnFeel.compile("someVariable + 1", List.of("someVariable"))).isNotNull();
    }

    @Test
    void theKieExtendedProfileIsAvailable() {
        // the BPMN engine keeps the profile the FEEL constraint evaluator has always used
        assertThat(BpmnFeel.newFeel().evaluate("now()")).isNotNull();
    }
}
