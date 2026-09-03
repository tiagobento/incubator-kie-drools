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

import org.jbpm.process.instance.impl.EmtpyKogitoProcessContext;
import org.jbpm.process.instance.impl.FeelReturnValueEvaluatorException;
import org.junit.jupiter.api.Test;
import org.kie.dmn.feel.exceptions.ExternalFunctionsDisabledException;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * A FEEL script evaluates to a context whose entries are written back to the process variables they name.
 *
 * These run without a process instance, so every entry counts as naming no declared variable.
 */
class FeelScriptActionTest {

    private static final KogitoProcessContext NO_PROCESS = new EmtpyKogitoProcessContext(Map.of("a", 1, "b", 2));

    private static void run(String expression) throws Exception {
        new FeelScriptAction(expression).execute(NO_PROCESS);
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
    void anInvalidScriptIsReportedAsSuch() {
        assertThatExceptionOfType(FeelReturnValueEvaluatorException.class)
                .isThrownBy(() -> run("{ broken: }"));
    }
}
