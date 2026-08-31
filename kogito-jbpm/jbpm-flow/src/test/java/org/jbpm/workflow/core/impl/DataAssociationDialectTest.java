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

import java.util.List;

import org.jbpm.util.ExpressionLanguages;
import org.jbpm.workflow.core.node.Assignment;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Which assignment action a data association builds for a given dialect.
 *
 * FEEL is expected to change only the <code>#{...}</code> leaf: every other kind of assignment, and every other
 * dialect, must keep the action it had.
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
    void anExpressionSourceWithNoDialectStaysOnMvel() {
        Object action = actionFor(null, definition("from", "#{a + b}"), definition("to", "target"));
        assertThat(action).isInstanceOf(InputExpressionAssignment.class);
    }

    @Test
    void anExpressionSourceInFeelIsStillAnInputExpressionAssignment() {
        Object action = actionFor(ExpressionLanguages.FEEL, definition("from", "#{a + b}"), definition("to", "target"));
        assertThat(action).isInstanceOf(InputExpressionAssignment.class);
    }

    @Test
    void aPlainSourceInFeelKeepsTheOrdinaryAssignment() {
        // FEEL does not change assignments that never evaluate a placeholder
        Object action = actionFor(ExpressionLanguages.FEEL, definition("from", null), definition("to", null));
        assertThat(action).isInstanceOf(SimpleExpressionAssignment.class);
    }

    @Test
    void aConstantSourceInFeelKeepsTheStaticAssignment() {
        Object action = actionFor(ExpressionLanguages.FEEL, definition("from", "\"hello\""), definition("to", null));
        assertThat(action).isInstanceOf(StaticAssignment.class);
    }

    @Test
    void anExpressionTargetInFeelWritesThroughThePathItNames() {
        Object action = actionFor(ExpressionLanguages.FEEL, definition("from", null), definition("to", "#{person.name}"));
        assertThat(action).isInstanceOf(FeelOutputExpressionAssignment.class);
    }

    @Test
    void aFeelTargetThatComputesAValueHasNowhereToWriteAndIsRejected() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> actionFor(ExpressionLanguages.FEEL, definition("from", null), definition("to", "#{upper case(person.name)}")))
                .withMessageContaining("cannot be an assignment target");
    }

    @Test
    void anExpressionTargetWithNoDialectStaysOnMvel() {
        Object action = actionFor(null, definition("from", null), definition("to", "#{person.name}"));
        assertThat(action).isInstanceOf(OutputExpressionAssignment.class);
    }

    @Test
    void aUserRegisteredDialectIsLeftForTheProcessDialectRegistry() {
        Object action = actionFor("custom", definition("from", "#{a}"), definition("to", "target"));
        assertThat(action).isNull();
    }
}
