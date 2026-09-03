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
package org.jbpm.process.expression.xpath;

import java.util.List;
import java.util.Map;

import org.jbpm.process.expression.ExpressionLanguage.Surface;
import org.jbpm.process.expression.ExpressionLanguages;
import org.jbpm.process.instance.impl.EmtpyKogitoProcessContext;
import org.jbpm.workflow.core.impl.DataDefinition;
import org.jbpm.workflow.core.impl.XPATHAssignmentAction;
import org.jbpm.workflow.core.node.Assignment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class XPathExpressionLanguageTest {

    private final XPathExpressionLanguage xpath = new XPathExpressionLanguage();

    @Test
    void isDiscoveredUnderItsIdentifiers() {
        assertThat(ExpressionLanguages.find("XPath")).isPresent();
        assertThat(ExpressionLanguages.find("xpath")).isPresent();
        assertThat(ExpressionLanguages.find("http://www.w3.org/1999/XPath")).map(l -> l.id()).contains("XPath");
    }

    @Test
    void evaluatesAConditionOverTheVariables() {
        Map<String, Object> variables = Map.of("count", "3");
        assertThat(xpath.evaluator("$count = 3", Boolean.class, null).evaluate(new EmtpyKogitoProcessContext(variables))).isEqualTo(true);
        assertThat(xpath.evaluator("$count > 3", Boolean.class, null).evaluate(new EmtpyKogitoProcessContext(variables))).isEqualTo(false);
    }

    @Test
    void takesWholeAssignmentsAndNothingElse() {
        assertThat(xpath.supports(Surface.CONDITION)).isTrue();
        assertThat(xpath.supports(Surface.ASSIGNMENT)).isTrue();
        assertThat(xpath.supports(Surface.SCRIPT)).isFalse();
        assertThat(xpath.supports(Surface.INTERPOLATION)).isFalse();
        DataDefinition from = new DataDefinition("from", "from", "java.lang.String");
        from.setExpression("/a");
        DataDefinition to = new DataDefinition("to", "to", "java.lang.String");
        to.setExpression("/b");
        assertThat(xpath.assignment(new Assignment("XPath", from, to), List.of(from), to)).containsInstanceOf(XPATHAssignmentAction.class);
        assertThatExceptionOfType(UnsupportedOperationException.class).isThrownBy(() -> xpath.script("x"));
    }
}
