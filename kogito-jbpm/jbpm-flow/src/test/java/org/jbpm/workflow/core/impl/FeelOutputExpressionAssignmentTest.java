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

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Writing through a FEEL path.
 *
 * A target names a place rather than computing a value, and a plain path is spelled the same in FEEL as in MVEL, so
 * the shared writer does the work. What is tested here is that FEEL routes to it, and that it insists on a reference.
 */
class FeelOutputExpressionAssignmentTest {

    public static class Person {
        private String name;
        private int age;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getAge() {
            return age;
        }

        public void setAge(int age) {
            this.age = age;
        }
    }

    public static class Team {
        private Person lead = new Person();

        public Person getLead() {
            return lead;
        }
    }

    private static DataDefinition definition(String name, String expression) {
        DataDefinition definition = new DataDefinition(name, name, String.class.getName());
        definition.setExpression(expression);
        return definition;
    }

    private static void write(String target, Object value, Map<String, Object> scope) throws Exception {
        FeelOutputExpressionAssignment assignment =
                new FeelOutputExpressionAssignment(definition("from", null), definition("to", target));
        Function<String, Object> resolver = scope::get;
        assignment.execute(name -> value, resolver, (key, produced) -> {
        });
    }

    @Test
    void aPropertyOfAVariableIsWritten() throws Exception {
        Person person = new Person();
        write("#{person.name}", "bob", Map.of("person", person));
        assertThat(person.getName()).isEqualTo("bob");
    }

    @Test
    void aPropertyDeeperInIsWritten() throws Exception {
        Team team = new Team();
        write("#{team.lead.name}", "bob", Map.of("team", team));
        assertThat(team.getLead().getName()).isEqualTo("bob");
    }

    @Test
    void theSharedWriterCoercesTheValueForTheSetter() throws Exception {
        // the value here comes from a plain source variable, never from a FEEL computation: reaching an output
        // assignment at all requires <from> to hold no #{...}, so no BigDecimal arrives from FEEL
        Person person = new Person();
        write("#{person.age}", BigDecimal.valueOf(41), Map.of("person", person));
        assertThat(person.getAge()).isEqualTo(41);
    }

    @Test
    void anEntryOfAMapIsWritten() throws Exception {
        Map<String, Object> holder = new HashMap<>();
        write("#{data.name}", "bob", Map.of("data", holder));
        assertThat(holder).containsEntry("name", "bob");
    }

    @Test
    void aTargetThatComputesAValueIsRefusedWhenTheAssignmentIsBuilt() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> new FeelOutputExpressionAssignment(definition("from", null), definition("to", "#{upper case(person.name)}")))
                .withMessageContaining("cannot be an assignment target");
    }

    @Test
    void aTargetThatCannotBeWrittenFails() {
        // reported by the shared writer, exactly as it reports one for an MVEL assignment
        Map<String, Object> noTeam = new HashMap<>();
        noTeam.put("team", null);
        assertThatExceptionOfType(Exception.class).isThrownBy(() -> write("#{team.lead.name}", "bob", noTeam));

        Person person = new Person();
        assertThatExceptionOfType(Exception.class).isThrownBy(() -> write("#{person.nickname}", "bob", Map.of("person", person)));
    }
}
