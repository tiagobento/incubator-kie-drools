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
package org.jbpm.process.expression.feel;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jbpm.process.expression.ExpressionLanguage.Surface;
import org.jbpm.process.expression.ExpressionScope;
import org.jbpm.process.expression.ValidationScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Writing through a FEEL assignment target.
 *
 * A target names a place rather than computing a value, so FEEL having no assignment does not matter: the path is
 * walked and the value written into what its last step names. What is tested here is that FEEL insists on a
 * reference, both when the process is built and when the assignment runs.
 */
class FeelAssignmentTargetTest {

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

    private static final FeelExpressionLanguage FEEL = new FeelExpressionLanguage();
    private static final ValidationScope NO_VARIABLES = new ValidationScope(List.of(), List.of(), List.of(), null);

    private static Object write(String target, Object value, Map<String, Object> scope) {
        return FEEL.assign(target, value, ExpressionScope.of(scope));
    }

    @Test
    void aPropertyOfAVariableIsWritten() {
        Person person = new Person();
        write("person.name", "bob", Map.of("person", person));
        assertThat(person.getName()).isEqualTo("bob");
    }

    @Test
    void aPropertyDeeperInIsWritten() {
        Team team = new Team();
        write("team.lead.name", "bob", Map.of("team", team));
        assertThat(team.getLead().getName()).isEqualTo("bob");
    }

    @Test
    void aBareVariableIsHandedBackForTheCallerToStore() {
        assertThat(write("total", 41, Map.of())).isEqualTo(41);
    }

    @Test
    void anEntryOfAMapIsWritten() {
        Map<String, Object> holder = new HashMap<>();
        write("data.name", "bob", Map.of("data", holder));
        assertThat(holder).containsEntry("name", "bob");
    }

    @Test
    void aSetterTakingAPrimitiveAcceptsItsWrapper() {
        Person person = new Person();
        write("person.age", 41, Map.of("person", person));
        assertThat(person.getAge()).isEqualTo(41);
    }

    @Test
    void aTargetThatComputesAValueIsRefusedWhenTheAssignmentRuns() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> write("upper case(person.name)", "bob", Map.of("person", new Person())))
                .withMessageContaining("cannot be an assignment target");
    }

    @Test
    void aTargetThatComputesAValueIsReportedWhenTheProcessIsBuilt() {
        List<String> problems = new ArrayList<>();
        FEEL.validate(Surface.ASSIGNMENT_TARGET, "#{upper case(person.name)}", NO_VARIABLES, problems::add);
        assertThat(problems).singleElement().asString().contains("cannot be an assignment target");

        problems.clear();
        FEEL.validate(Surface.ASSIGNMENT_TARGET, "#{person.name}", NO_VARIABLES, problems::add);
        assertThat(problems).isEmpty();
    }

    @Test
    void aTargetThatCannotBeWrittenFails() {
        Map<String, Object> noTeam = new HashMap<>();
        noTeam.put("team", null);
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> write("team.lead.name", "bob", noTeam));

        Person person = new Person();
        assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> write("person.nickname", "bob", Map.of("person", person)));
    }

    @Test
    void aBigDecimalDoesNotFitAnIntSetterAndSaysSo() {
        // FEEL numbers are BigDecimal; a setter taking an int is not a place they fit, and the writer reports it
        // rather than storing something else
        Person person = new Person();
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> write("person.age", BigDecimal.valueOf(41), Map.of("person", person)));
    }
}
