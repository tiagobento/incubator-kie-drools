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
package org.jbpm.workflow.core.impl;

import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.expression.ExpressionLanguages;
import org.jbpm.process.instance.impl.AssignmentAction;
import org.jbpm.workflow.core.node.Assignment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Which assignment action a data association builds for a given language.
 *
 * A language only ever changes the <code>#{...}</code> leaf, unless it takes whole assignments: every other kind of
 * assignment keeps the action it had, whatever the language.
 */
class DataAssociationDialectTest {

    private static DataDefinition definition(String name, String expression) {
        DataDefinition definition = new DataDefinition(name, name, String.class.getName());
        definition.setExpression(expression);
        return definition;
    }

    private static Object actionFor(String dialect, DataDefinition from, DataDefinition to) {
        Assignment assignment = new Assignment(dialect, from, to);
        new DataAssociation(List.of(from), to, List.of(assignment), null);
        return assignment.getMetaData("Action");
    }

    @Test
    void anExpressionSourceWithNoDialectFollowsTheDocument() {
        Object action = actionFor(null, definition("from", "#{a + b}"), definition("to", "target"));
        assertThat(action).isInstanceOf(InputExpressionAssignment.class);
    }

    @Test
    void anExpressionSourceInFeelIsStillAnInputExpressionAssignment() {
        Object action = actionFor("FEEL", definition("from", "#{a + b}"), definition("to", "target"));
        assertThat(action).isInstanceOf(InputExpressionAssignment.class);
    }

    @Test
    void aPlainSourceInFeelKeepsTheOrdinaryAssignment() {
        // FEEL does not change assignments that never evaluate a placeholder
        Object action = actionFor("FEEL", definition("from", null), definition("to", null));
        assertThat(action).isInstanceOf(SimpleExpressionAssignment.class);
    }

    @Test
    void aConstantSourceInFeelKeepsTheStaticAssignment() {
        Object action = actionFor("FEEL", definition("from", "\"hello\""), definition("to", null));
        assertThat(action).isInstanceOf(StaticAssignment.class);
    }

    @Test
    void anExpressionTargetIsWrittenThroughTheLanguageWhateverItIs() {
        assertThat(actionFor("FEEL", definition("from", null), definition("to", "#{person.name}"))).isInstanceOf(OutputExpressionAssignment.class);
        assertThat(actionFor(null, definition("from", null), definition("to", "#{person.name}"))).isInstanceOf(OutputExpressionAssignment.class);
        assertThat(actionFor("mvel", definition("from", null), definition("to", "#{person.name}"))).isInstanceOf(OutputExpressionAssignment.class);
    }

    @Test
    void aLanguageThatTakesWholeAssignmentsBuildsTheAction() {
        AssignmentAction whole = (source, target, producer) -> {
        };
        ExpressionLanguage custom = new ExpressionLanguage() {
            @Override
            public String id() {
                return "custom";
            }

            @Override
            public String uri() {
                return "urn:custom";
            }

            @Override
            public Set<Surface> surfaces() {
                return EnumSet.of(Surface.ASSIGNMENT);
            }

            @Override
            public Optional<AssignmentAction> assignment(Assignment assignment, List<DataDefinition> sources, DataDefinition target) {
                return Optional.of(whole);
            }
        };
        ExpressionLanguages.register(custom);
        try {
            assertThat(actionFor("custom", definition("from", "from_expression"), definition("to", "to_expression"))).isSameAs(whole);
        } finally {
            ExpressionLanguages.unregister(custom);
        }
    }

    @Test
    void xpathTakesWholeAssignments() {
        assertThat(actionFor("XPath", definition("from", "/a/b"), definition("to", "/c"))).isInstanceOf(XPATHAssignmentAction.class);
    }

    @Test
    void aLanguageThatIsNotAvailableIsReportedWhenTheAssociationIsBuilt() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> actionFor("nowhere", definition("from", "#{a}"), definition("to", "target")))
                .withMessageContaining("No expression language answers to 'nowhere'");
    }
}
