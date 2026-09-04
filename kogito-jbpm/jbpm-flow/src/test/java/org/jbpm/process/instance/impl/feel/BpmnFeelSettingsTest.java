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
package org.jbpm.process.instance.impl.feel;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.process.workitems.impl.ConfigResolver;
import org.kie.kogito.process.workitems.impl.ConfigResolverHolder;
import org.kie.kogito.process.workitems.impl.SystemPropertiesConfigResolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.jbpm.process.instance.impl.feel.BpmnFeelSettings.SANDBOXED_PROPERTY;

class BpmnFeelSettingsTest {

    @AfterEach
    void backToTheDefaults() {
        BpmnFeelSettings.setSandboxed(null);
        System.clearProperty(SANDBOXED_PROPERTY);
        ConfigResolverHolder.setConfigResolver(new SystemPropertiesConfigResolver());
    }

    private static void configure(String value) {
        ConfigResolverHolder.setConfigResolver(new ConfigResolver() {
            @Override
            public <T> Optional<T> getConfigProperty(String name, Class<T> clazz) {
                return SANDBOXED_PROPERTY.equals(name) ? Optional.of(clazz.cast(value)) : Optional.empty();
            }
        });
    }

    @Test
    void sandboxedUnlessTheApplicationSaysOtherwise() {
        assertThat(BpmnFeelSettings.isSandboxed()).isTrue();
        assertThat(BpmnFeelSettings.configured()).isEmpty();
    }

    @Test
    void aSystemPropertyIsEnoughOutsideAnApplicationFramework() {
        // the default resolver is the system properties one, which is what a plain embedding of the engine gets
        System.setProperty(SANDBOXED_PROPERTY, "false");
        assertThat(BpmnFeelSettings.isSandboxed()).isFalse();
        System.setProperty(SANDBOXED_PROPERTY, "true");
        assertThat(BpmnFeelSettings.isSandboxed()).isTrue();
    }

    @Test
    void theApplicationConfigurationIsWhatTheResolverInTheHolderAnswers() {
        // Quarkus and Spring Boot install their own resolver at startup; from then on it is the application's configuration
        System.setProperty(SANDBOXED_PROPERTY, "true");
        configure("false");
        assertThat(BpmnFeelSettings.isSandboxed()).isFalse();
        assertThat(BpmnFeelSettings.configured()).contains(false);
    }

    @Test
    void theValueIsReadCaseInsensitivelyAndTrimmed() {
        configure(" FALSE ");
        assertThat(BpmnFeelSettings.isSandboxed()).isFalse();
        configure("True");
        assertThat(BpmnFeelSettings.isSandboxed()).isTrue();
    }

    @Test
    void anUnrecognisedValueLeavesTheEngineSandboxed() {
        // a typo must not open the engine
        configure("yes");
        assertThat(BpmnFeelSettings.isSandboxed()).isTrue();
        assertThat(BpmnFeelSettings.configured()).isEmpty();
    }

    @Test
    void aBlankValueIsNoValue() {
        configure("  ");
        assertThat(BpmnFeelSettings.configured()).isEmpty();
        assertThat(BpmnFeelSettings.isSandboxed()).isTrue();
    }

    @Test
    void aForcedValueWinsOverTheConfigurationUntilCleared() {
        configure("false");
        BpmnFeelSettings.setSandboxed(true);
        assertThat(BpmnFeelSettings.isSandboxed()).isTrue();
        BpmnFeelSettings.setSandboxed(false);
        assertThat(BpmnFeelSettings.isSandboxed()).isFalse();
        BpmnFeelSettings.setSandboxed(null);
        assertThat(BpmnFeelSettings.isSandboxed()).isFalse();
        configure("true");
        assertThat(BpmnFeelSettings.isSandboxed()).isTrue();
    }

    @Test
    void theSettingIsAskedForAsAStringSoEveryResolverCanAnswer() {
        // the system properties resolver only answers String and Integer; a Boolean request would come back empty there
        AtomicReference<Class<?>> requested = new AtomicReference<>();
        ConfigResolverHolder.setConfigResolver(new ConfigResolver() {
            @Override
            public <T> Optional<T> getConfigProperty(String name, Class<T> clazz) {
                requested.set(clazz);
                return Optional.empty();
            }
        });
        BpmnFeelSettings.isSandboxed();
        assertThat(requested.get()).isEqualTo(String.class);
    }

    @Test
    void thePropertyNameIsTheDocumentedOne() {
        assertThat(SANDBOXED_PROPERTY).isEqualTo("jbpm.expressions.feel.sandboxed");
    }
}
