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
package org.jbpm.process.expression;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.jbpm.process.expression.ExpressionLanguage.Surface;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * How a language is found: by any of its identifiers, whatever the case, and with a useful message when it is not.
 */
class ExpressionLanguagesTest {

    private static ExpressionLanguage language(String id, String uri, String... aliases) {
        return new ExpressionLanguage() {
            @Override
            public String id() {
                return id;
            }

            @Override
            public String uri() {
                return uri;
            }

            @Override
            public Collection<String> identifiers() {
                List<String> all = new java.util.ArrayList<>(List.of(id, uri));
                all.addAll(List.of(aliases));
                return all;
            }

            @Override
            public Set<Surface> surfaces() {
                return EnumSet.allOf(Surface.class);
            }
        };
    }

    private final ExpressionLanguage upper = language("Upper", "urn:test:upper", "application/upper");

    @AfterEach
    void unregister() {
        ExpressionLanguages.unregister(upper);
    }

    @Test
    void aRegisteredLanguageAnswersToEveryIdentifierIgnoringCase() {
        ExpressionLanguages.register(upper);
        assertThat(ExpressionLanguages.find("Upper")).contains(upper);
        assertThat(ExpressionLanguages.find("upper")).contains(upper);
        assertThat(ExpressionLanguages.find("URN:TEST:UPPER")).contains(upper);
        assertThat(ExpressionLanguages.find(" application/upper ")).contains(upper);
        assertThat(ExpressionLanguages.idOf("application/upper")).contains("Upper");
        assertThat(ExpressionLanguages.all()).contains(upper);
    }

    @Test
    void nothingAnswersToABlankOrUnknownIdentifier() {
        assertThat(ExpressionLanguages.find(null)).isEmpty();
        assertThat(ExpressionLanguages.find("  ")).isEmpty();
        assertThat(ExpressionLanguages.find("urn:nowhere")).isEmpty();
    }

    @Test
    void requiringAnUnknownLanguageNamesItAndWhatIsAvailable() {
        ExpressionLanguages.register(upper);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ExpressionLanguages.require("urn:nowhere"))
                .withMessageContaining("No expression language answers to 'urn:nowhere'")
                .withMessageContaining("Upper (urn:test:upper)")
                .withMessageContaining("jbpm-expressions-mvel");
    }

    @Test
    void theDefaultIsMvelAndADocumentMayOverrideIt() {
        assertThat(ExpressionLanguages.DEFAULT).isEqualTo("mvel");
        assertThat(ExpressionLanguages.languageOf(new org.jbpm.ruleflow.core.RuleFlowProcess())).isEqualTo("mvel");
        org.jbpm.ruleflow.core.RuleFlowProcess process = new org.jbpm.ruleflow.core.RuleFlowProcess();
        process.setExpressionLanguage("Upper");
        assertThat(ExpressionLanguages.languageOf(process)).isEqualTo("Upper");
    }

    @Test
    void aLanguageRefusesTheSurfacesItDoesNotDeclare() {
        ExpressionLanguage conditionsOnly = new ExpressionLanguage() {
            @Override
            public String id() {
                return "conditions";
            }

            @Override
            public String uri() {
                return "urn:test:conditions";
            }

            @Override
            public Set<Surface> surfaces() {
                return EnumSet.of(Surface.CONDITION);
            }
        };
        assertThat(conditionsOnly.supports(Surface.CONDITION)).isTrue();
        assertThat(conditionsOnly.supports(Surface.SCRIPT)).isFalse();
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> conditionsOnly.script("x"))
                .withMessage("The expression language 'conditions' cannot be used for a script.");
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> conditionsOnly.interpolate("x", ExpressionScope.of(java.util.Map.of())))
                .withMessageContaining("a #{...} placeholder");
        assertThat(conditionsOnly.assignment(null, List.of(), null)).isEmpty();
        assertThat(conditionsOnly.compile(Surface.CONDITION, "x", Boolean.class, null, null)).isNull();
    }
}
