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
package org.jbpm.workflow.instance.impl;

import java.math.BigDecimal;
import java.util.Map;

import org.jbpm.process.instance.impl.feel.BpmnFeelSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.dmn.feel.exceptions.ExternalFunctionsDisabledException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class FeelInterpolationTest {

    private static final String EXTERNAL_MAX =
            "{ maximum : function( v1, v2 ) external { java : { class : \"java.lang.Math\", method signature: \"max(long,long)\" } }, the max : maximum( 10, 20 ) }.the max";

    @AfterEach
    void backToTheConfiguredMode() {
        BpmnFeelSettings.setSandboxed(null);
    }

    @Test
    void aPlaceholderIsEvaluatedAgainstTheNamesItIsGiven() {
        assertThat(FeelInterpolation.evaluate("x + 1", Map.of("x", 41))).isEqualTo(BigDecimal.valueOf(42));
    }

    @Test
    void sandboxedAPlaceholderMayNotDeclareAnExternalFunction() {
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class).isThrownBy(() -> FeelInterpolation.evaluate(EXTERNAL_MAX, Map.of()));
    }

    @Test
    void notSandboxedAPlaceholderMay() {
        BpmnFeelSettings.setSandboxed(false);
        assertThat(FeelInterpolation.evaluate(EXTERNAL_MAX, Map.of())).isEqualTo(BigDecimal.valueOf(20));
    }

    @Test
    void thePlaceholderCacheKeepsTheModesApart() {
        // the same body and the same names, compiled once unsandboxed, must not be served to a sandboxed evaluation
        BpmnFeelSettings.setSandboxed(false);
        assertThat(FeelInterpolation.evaluate(EXTERNAL_MAX, Map.of())).isEqualTo(BigDecimal.valueOf(20));
        BpmnFeelSettings.setSandboxed(true);
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class).isThrownBy(() -> FeelInterpolation.evaluate(EXTERNAL_MAX, Map.of()));
        BpmnFeelSettings.setSandboxed(false);
        assertThat(FeelInterpolation.evaluate(EXTERNAL_MAX, Map.of())).isEqualTo(BigDecimal.valueOf(20));
    }
}
