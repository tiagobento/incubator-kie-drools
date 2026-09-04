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
package org.jbpm.process.instance.impl;

import java.util.Map;

import org.jbpm.process.instance.FeelTestProcess;
import org.jbpm.process.instance.impl.feel.BpmnFeelSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.dmn.feel.exceptions.ExternalFunctionsDisabledException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FeelReturnValueEvaluatorTest {

    private static final String EXTERNAL_MAX_IS_TWENTY =
            "{ maximum : function( v1, v2 ) external { java : { class : \"java.lang.Math\", method signature: \"max(long,long)\" } }, ok : maximum( 10, 20 ) = 20 }.ok";

    @AfterEach
    void backToTheConfiguredMode() {
        BpmnFeelSettings.setSandboxed(null);
    }

    @Test
    void aConditionReadsTheSandboxedKcontext() {
        Object result = FeelTestProcess.inAction(Map.of(), context -> new FeelReturnValueEvaluator(
                "kcontext.processInstance.processId = \"" + FeelTestProcess.PROCESS_ID + "\" and kcontext.nodeInstance.nodeName = \"" + FeelTestProcess.ACTION_NODE_NAME + "\"")
                        .evaluate(context));
        assertThat(result).isEqualTo(true);
    }

    @Test
    void sandboxedAConditionCannotReachTheEngine() {
        // kcontext is declared to the compiler with its five keys; a path into anything else fails to compile, so a
        // reach into the engine is refused before it is ever evaluated
        assertThatExceptionOfType(FeelReturnValueEvaluatorException.class)
                .isThrownBy(() -> FeelTestProcess.inAction(Map.of(), context -> new FeelReturnValueEvaluator("kcontext.kieRuntime = null").evaluate(context)))
                .withMessageContaining("Unknown variable 'kcontext.kieRuntime'");
        assertThatExceptionOfType(FeelReturnValueEvaluatorException.class)
                .isThrownBy(() -> FeelTestProcess.inAction(Map.of(), context -> new FeelReturnValueEvaluator("kcontext.processInstance.process != null").evaluate(context)))
                .withMessageContaining("Unknown variable 'kcontext.processInstance.process'");
    }

    @Test
    void sandboxedATypoInADocumentedKeyIsACompileError() {
        assertThatExceptionOfType(FeelReturnValueEvaluatorException.class)
                .isThrownBy(() -> FeelTestProcess.inAction(Map.of(), context -> new FeelReturnValueEvaluator("kcontext.processInstance.procesId != null").evaluate(context)))
                .withMessageContaining("Unknown variable 'kcontext.processInstance.procesId'");
    }

    @Test
    void notSandboxedAConditionReachesTheEngine() {
        BpmnFeelSettings.setSandboxed(false);
        Object result = FeelTestProcess.inAction(Map.of(), context -> new FeelReturnValueEvaluator(
                "kcontext.kieRuntime != null and kcontext.processInstance.process.id = \"" + FeelTestProcess.PROCESS_ID + "\"").evaluate(context));
        assertThat(result).isEqualTo(true);
    }

    @Test
    void notSandboxedAConditionMayCallAnExternalFunction() {
        BpmnFeelSettings.setSandboxed(false);
        assertThat(new FeelReturnValueEvaluator(EXTERNAL_MAX_IS_TWENTY).evaluate(new EmtpyKogitoProcessContext(Map.of()))).isEqualTo(true);
    }

    @Test
    void sandboxedAConditionMayNotCallAnExternalFunction() {
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class)
                .isThrownBy(() -> new FeelReturnValueEvaluator(EXTERNAL_MAX_IS_TWENTY).evaluate(new EmtpyKogitoProcessContext(Map.of())));
    }

    @Test
    void anEvaluatorCompiledInOneModeIsRecompiledInTheOther() {
        // the compiled expression is cached per evaluator; a flip of the setting must not let a cached, unsandboxed
        // compilation serve a sandboxed evaluation
        FeelReturnValueEvaluator evaluator = new FeelReturnValueEvaluator(EXTERNAL_MAX_IS_TWENTY);
        EmtpyKogitoProcessContext context = new EmtpyKogitoProcessContext(Map.of());
        BpmnFeelSettings.setSandboxed(false);
        assertThat(evaluator.evaluate(context)).isEqualTo(true);
        BpmnFeelSettings.setSandboxed(true);
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class).isThrownBy(() -> evaluator.evaluate(context));
        BpmnFeelSettings.setSandboxed(false);
        assertThat(evaluator.evaluate(context)).isEqualTo(true);
    }

    @Test
    void theVariablesAreBoundByNameInBothModes() {
        for (boolean sandboxed : new boolean[] { true, false }) {
            BpmnFeelSettings.setSandboxed(sandboxed);
            Object result = FeelTestProcess.inAction(Map.of("score", 7), context -> new FeelReturnValueEvaluator("score > 5").evaluate(context));
            assertThat(result).as("sandboxed=%s", sandboxed).isEqualTo(true);
        }
    }

    @Test
    void theVariablesAreAlsoReachableThroughKcontextInEachModesOwnWay() {
        // sandboxed, kcontext.variables is the documented map; not sandboxed, kcontext is the live context and the
        // variables hang off the process instance as they always did
        Object sandboxed = FeelTestProcess.inAction(Map.of("score", 7), context -> new FeelReturnValueEvaluator("kcontext.variables.score = 7").evaluate(context));
        assertThat(sandboxed).isEqualTo(true);
        BpmnFeelSettings.setSandboxed(false);
        Object open = FeelTestProcess.inAction(Map.of("score", 7), context -> new FeelReturnValueEvaluator("kcontext.processInstance.variables.score = 7").evaluate(context));
        assertThat(open).isEqualTo(true);
    }
}
