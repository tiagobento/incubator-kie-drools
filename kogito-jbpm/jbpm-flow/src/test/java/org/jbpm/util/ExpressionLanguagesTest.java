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
package org.jbpm.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ExpressionLanguagesTest {

    @Test
    void feelIsRecognisedByEveryFormAModelCanUse() {
        assertThat(ExpressionLanguages.isFeel(ExpressionLanguages.FEEL_LANGUAGE)).isTrue();
        assertThat(ExpressionLanguages.isFeel(ExpressionLanguages.DMN_FEEL_LANGUAGE)).isTrue();
        assertThat(ExpressionLanguages.isFeel(ExpressionLanguages.FEEL_LANGUAGE_SHORT)).isTrue();
        assertThat(ExpressionLanguages.isFeel("FEEL")).isTrue();
        assertThat(ExpressionLanguages.isFeel("feel")).isTrue();
    }

    @Test
    void otherLanguagesAreNotFeel() {
        assertThat(ExpressionLanguages.isFeel(null)).isFalse();
        assertThat(ExpressionLanguages.isFeel("")).isFalse();
        assertThat(ExpressionLanguages.isFeel(ExpressionLanguages.MVEL_LANGUAGE)).isFalse();
        assertThat(ExpressionLanguages.isFeel(ExpressionLanguages.JAVA_LANGUAGE)).isFalse();
        assertThat(ExpressionLanguages.isFeel(ExpressionLanguages.XPATH_LANGUAGE)).isFalse();
        assertThat(ExpressionLanguages.isFeel(ExpressionLanguages.RULE_LANGUAGE)).isFalse();
    }

    @Test
    void mvelCoversTheUriTheDialectNameAndAnEmptyDeclaration() {
        assertThat(ExpressionLanguages.isMvel(ExpressionLanguages.MVEL_LANGUAGE)).isTrue();
        assertThat(ExpressionLanguages.isMvel("mvel")).isTrue();
        assertThat(ExpressionLanguages.isMvel("")).isTrue();
        assertThat(ExpressionLanguages.isMvel(null)).isFalse();
        assertThat(ExpressionLanguages.isMvel(ExpressionLanguages.FEEL_LANGUAGE)).isFalse();
    }

    @Test
    void onlyFeelAndMvelAreKnownDocumentLanguages() {
        assertThat(ExpressionLanguages.isKnownDocumentLanguage(ExpressionLanguages.MVEL_LANGUAGE)).isTrue();
        assertThat(ExpressionLanguages.isKnownDocumentLanguage(ExpressionLanguages.DMN_FEEL_LANGUAGE)).isTrue();
        // the values BPMN documents in this repository carry on a per-assignment expressionLanguage
        assertThat(ExpressionLanguages.isKnownDocumentLanguage("XPath")).isFalse();
        assertThat(ExpressionLanguages.isKnownDocumentLanguage("custom")).isFalse();
    }
}
