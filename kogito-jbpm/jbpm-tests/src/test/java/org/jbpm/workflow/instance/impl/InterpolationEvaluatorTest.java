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

import org.jbpm.process.expression.ExpressionScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The one leaf that differs between languages when a <code>#{...}</code> placeholder is resolved.
 */
class InterpolationEvaluatorTest {

    private static final Map<String, Object> VALUES = Map.of("a", 1, "b", 2);

    private static ExpressionScope scope() {
        return ExpressionScope.of(VALUES);
    }

    @Test
    void mvelIsUsedWhenNoLanguageIsSelected() {
        assertThat(InterpolationEvaluator.evaluate(null, "a + b", scope())).isEqualTo(3);
    }

    @Test
    void mvelIsUsedWhenTheDocumentSaysMvel() {
        assertThat(InterpolationEvaluator.evaluate("http://www.mvel.org/2.0", "a + b", scope())).isEqualTo(3);
    }

    @Test
    void feelIsUsedWhenTheDocumentSaysFeel() {
        assertThat(InterpolationEvaluator.evaluate("FEEL", "a + b", scope())).isEqualTo(BigDecimal.valueOf(3));
    }

    @Test
    void feelResolvesTheDmnUri() {
        assertThat(InterpolationEvaluator.evaluate("http://www.omg.org/spec/DMN/20180521/FEEL/", "a + b", scope())).isEqualTo(BigDecimal.valueOf(3));
    }

    @Test
    void aSinglePlaceholderPassesTheObjectItself() {
        Map<String, Object> values = Map.of("greeting", "hello", "person", new StringBuilder("bob"));
        assertThat(InterpolationEvaluator.evaluate("FEEL", "greeting", ExpressionScope.of(values))).isEqualTo("hello");
        assertThat(InterpolationEvaluator.evaluate("FEEL", "person", ExpressionScope.of(values))).isSameAs(values.get("person"));
    }

    @Test
    void feelNormalisesNumbersToBigDecimal() {
        // a documented difference from MVEL: FEEL's number type is BigDecimal, so a numeric placeholder comes back as
        // one even when the process variable was an Integer
        assertThat(InterpolationEvaluator.evaluate("FEEL", "a", scope())).isEqualTo(BigDecimal.ONE);
        assertThat(InterpolationEvaluator.evaluate(null, "a", scope())).isEqualTo(1);
    }

    @Test
    void aLanguageThatCannotInterpolateSaysSo() {
        assertThatExceptionOfType(UnsupportedOperationException.class)
                .isThrownBy(() -> InterpolationEvaluator.evaluate("XPath", "a + b", scope()))
                .withMessageContaining("cannot be used for a #{...} placeholder");
    }

    @Test
    void aLanguageThatIsNotAvailableSaysSo() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> InterpolationEvaluator.evaluate("urn:nowhere", "a + b", scope()))
                .withMessageContaining("No expression language answers to 'urn:nowhere'")
                .withMessageContaining("mvel")
                .withMessageContaining("FEEL");
    }
}
