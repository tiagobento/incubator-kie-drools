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
package org.jbpm.process.instance.impl.actions;

import java.util.Map;

import org.jbpm.process.instance.FeelTestProcess;
import org.jbpm.process.instance.impl.EmtpyKogitoProcessContext;
import org.jbpm.process.instance.impl.FeelReturnValueEvaluatorException;
import org.jbpm.process.instance.impl.feel.BpmnFeelSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.dmn.feel.exceptions.ExternalFunctionsDisabledException;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * A FEEL script evaluates to a context whose entries are written back to the process variables they name.
 *
 * These run without a process instance, so every entry counts as naming no declared variable.
 */
class FeelScriptActionTest {

    private static final KogitoProcessContext NO_PROCESS = new EmtpyKogitoProcessContext(Map.of("a", 1, "b", 2));

    private static final String EXTERNAL_MAX_INTO_RESULT =
            "{ maximum : function( v1, v2 ) external { java : { class : \"java.lang.Math\", method signature: \"max(long,long)\" } }, result : maximum( 1, 2 ) }";

    @AfterEach
    void backToTheConfiguredMode() {
        BpmnFeelSettings.setSandboxed(null);
    }

    private static void run(String expression) throws Exception {
        new FeelScriptAction(expression).execute(NO_PROCESS);
    }

    private static Object runInProcess(FeelScriptAction action, KogitoProcessContext context) {
        try {
            action.execute(context);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return context.getVariable("result");
    }

    @Test
    void aResultThatIsNotAContextIsRejectedWithAnActionableMessage() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> run("a + b"))
                .withMessageContaining("must evaluate to a context")
                .withMessageContaining("{ aVariable: someExpression }");
    }

    @Test
    void aNullResultIsRejectedToo() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> run("null"))
                .withMessageContaining("must evaluate to a context");
    }

    @Test
    void anEntryNamingNoProcessVariableIsIgnoredAndReported() {
        assertThatNoException().isThrownBy(() -> run("{ notAVariable: a + b }"));
    }

    @Test
    void aScriptCannotDeclareAnExternalFunction() {
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class)
                .isThrownBy(() -> run("{ escape : function( v ) external { java : { class : \"java.lang.Runtime\", method signature: \"getRuntime()\" } } }"));
    }

    @Test
    void notSandboxedAScriptMayDeclareAnExternalFunctionAndItsResultIsWrittenBack() {
        BpmnFeelSettings.setSandboxed(false);
        Object result = FeelTestProcess.inAction(Map.of("result", 0), context -> runInProcess(new FeelScriptAction(EXTERNAL_MAX_INTO_RESULT), context));
        assertThat(((Number) result).intValue()).isEqualTo(2);
    }

    @Test
    void aScriptCompiledInOneModeIsRecompiledInTheOther() {
        // the compiled expression is cached per action; a flip of the setting must not let a cached, unsandboxed
        // compilation serve a sandboxed run
        FeelScriptAction action = new FeelScriptAction(EXTERNAL_MAX_INTO_RESULT);
        BpmnFeelSettings.setSandboxed(false);
        assertThatNoException().isThrownBy(() -> action.execute(NO_PROCESS));
        BpmnFeelSettings.setSandboxed(true);
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class).isThrownBy(() -> action.execute(NO_PROCESS));
        BpmnFeelSettings.setSandboxed(false);
        assertThatNoException().isThrownBy(() -> action.execute(NO_PROCESS));
    }

    @Test
    void sandboxedAScriptReadsTheCuratedKcontext() {
        Object result = FeelTestProcess.inAction(Map.of("result", ""),
                context -> runInProcess(new FeelScriptAction("{ result: kcontext.processInstance.processId + \"/\" + kcontext.nodeInstance.nodeName }"), context));
        assertThat(result).isEqualTo(FeelTestProcess.PROCESS_ID + "/" + FeelTestProcess.ACTION_NODE_NAME);
    }

    @Test
    void notSandboxedAScriptReadsTheLiveContext() {
        BpmnFeelSettings.setSandboxed(false);
        Object result = FeelTestProcess.inAction(Map.of("result", ""),
                context -> runInProcess(new FeelScriptAction("{ result: kcontext.processInstance.process.id }"), context));
        assertThat(result).isEqualTo(FeelTestProcess.PROCESS_ID);
    }

    @Test
    void anInvalidScriptIsReportedAsSuch() {
        assertThatExceptionOfType(FeelReturnValueEvaluatorException.class)
                .isThrownBy(() -> run("{ broken: }"));
    }
}
