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
package org.kie.dmn.feel.lang.impl;

import java.math.BigDecimal;
import java.util.Collections;

import org.junit.jupiter.api.Test;
import org.kie.dmn.feel.FEEL;
import org.kie.dmn.feel.exceptions.ExternalFunctionsDisabledException;
import org.kie.dmn.feel.parser.feel11.profiles.KieExtendedFEELProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNoException;

class FEELExternalFunctionsDisabledTest {

    private static final String EXTERNAL_MAX = "{ maximum : function( v1, v2 ) external { java : { class : \"java.lang.Math\", method signature: \"max(long,long)\" } }, the max : maximum( 10, 20 ) }.the max";

    private static FEEL feel(boolean externalFunctionsDisabled) {
        FEELBuilder.Builder builder = FEELBuilder.builder()
                .withProfiles(Collections.singletonList(new KieExtendedFEELProfile()));
        if (externalFunctionsDisabled) {
            builder = builder.withExternalFunctionsDisabled();
        }
        return builder.build();
    }

    @Test
    void externalFunctionsAreEnabledByDefault() {
        assertThat(feel(false).evaluate(EXTERNAL_MAX)).isEqualTo(BigDecimal.valueOf(20));
    }

    @Test
    void disablingRejectsAnExternalFunctionAtCompileTime() {
        FEEL feel = feel(true);
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class)
                .isThrownBy(() -> feel.evaluate(EXTERNAL_MAX)).withMessageContaining("External functions are disabled");
    }

    @Test
    void disablingRejectsAnExternalFunctionEvenWhenItIsNeverCalled() {
        FEEL feel = feel(true);
        assertThatExceptionOfType(ExternalFunctionsDisabledException.class).isThrownBy(() -> feel.evaluate(
                "{ unused : function( v ) external { java : { class : \"java.lang.Math\", method signature: \"abs(double)\" } }, answer : 42 }.answer"));
    }

    @Test
    void aStringLiteralContainingExternalIsNotAnExternalFunction() {
        // detection looks at the AST, not at the text, so a string that merely says "external" is untouched
        assertThat(feel(true).evaluate("\"external\"")).isEqualTo("external");
        assertThat(feel(true).evaluate("\"function( v ) external { java }\""))
                .isEqualTo("function( v ) external { java }");
    }

    @Test
    void anOrdinaryFunctionDefinitionStillWorksWhenExternalsAreDisabled() {
        assertThatNoException().isThrownBy(
                () -> feel(true).evaluate("{ double it : function( v ) v * 2, result : double it( 21 ) }.result"));
        assertThat(feel(true).evaluate("{ double it : function( v ) v * 2, result : double it( 21 ) }.result"))
                .isEqualTo(BigDecimal.valueOf(42));
    }
}
