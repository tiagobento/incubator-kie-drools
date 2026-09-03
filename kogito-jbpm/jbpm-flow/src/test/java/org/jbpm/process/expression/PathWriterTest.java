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

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

class PathWriterTest {

    public static class Address {
        public String city;
    }

    public static class Person {
        private String name;
        private Address address = new Address();
        private final Map<String, Object> extra = new HashMap<>();

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Address getAddress() {
            return address;
        }

        public Map<String, Object> getExtra() {
            return extra;
        }
    }

    @Test
    void writesThroughSettersPublicFieldsAndMaps() {
        Person person = new Person();
        ExpressionScope scope = ExpressionScope.of(Map.of("person", person));
        assertThat(PathWriter.write("person.name", "bob", scope)).isEqualTo("bob");
        assertThat(PathWriter.write("person.address.city", "Lisbon", scope)).isEqualTo("Lisbon");
        assertThat(PathWriter.write("person.extra.nickname", "bobby", scope)).isEqualTo("bobby");
        assertThat(person.getName()).isEqualTo("bob");
        assertThat(person.getAddress().city).isEqualTo("Lisbon");
        assertThat(person.getExtra()).containsEntry("nickname", "bobby");
    }

    @Test
    void aBareNameIsHandedBackForTheCallerToStore() {
        assertThat(PathWriter.write("total", 3, ExpressionScope.of(Map.of()))).isEqualTo(3);
    }

    @Test
    void namesTheStepThatCannotBeWalked() {
        Map<String, Object> variables = new HashMap<>();
        variables.put("person", null);
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PathWriter.write("person.name", "bob", ExpressionScope.of(variables)))
                .withMessageContaining("'person' is null");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PathWriter.write("nobody.name", "bob", ExpressionScope.of(Map.of())))
                .withMessageContaining("'nobody' is not a variable in scope");
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> PathWriter.write("person.nickname", "bob", ExpressionScope.of(Map.of("person", new Person()))))
                .withMessageContaining("no writable property 'nickname'");
    }
}
