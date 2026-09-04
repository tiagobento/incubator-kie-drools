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

import java.util.Locale;
import java.util.Optional;

import org.kie.api.annotations.KieProperty;
import org.kie.kogito.process.workitems.impl.ConfigResolverHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The settings of the FEEL engine BPMN uses, read from the application's configuration.
 *
 * <p>
 * There is one: {@value #SANDBOXED_PROPERTY}, <code>true</code> unless the application says otherwise. Sandboxed, FEEL
 * evaluates what a model author can reasonably mean and nothing more: <code>kcontext</code> is a read-only view of the
 * instance with a documented set of keys, and an <code>external</code> function definition, which invokes an arbitrary
 * class reflectively, is refused. Not sandboxed, FEEL is handed the same objects MVEL is handed - <code>kcontext</code>
 * is the live process context, every getter on it and below it resolves, and <code>external</code> functions run - which
 * is how FEEL conditions behaved before the sandbox existed.
 *
 * <p>
 * The value is resolved through the {@link ConfigResolverHolder}, so in a Quarkus or Spring Boot application it is the
 * application's own configuration, and elsewhere a system property. It is consulted at build time, when process
 * validation compiles every FEEL expression, and at runtime, when one is evaluated; the two have to agree, so the same
 * property serves both. {@link #setSandboxed(Boolean)} forces a value regardless of configuration, for a build tool
 * applying the properties of the project it is building and for tests.
 */
public final class BpmnFeelSettings {

    @KieProperty(type = "boolean", defaultValue = "true", allowedValues = "true,false")
    public static final String SANDBOXED_PROPERTY = "jbpm.expressions.feel.sandboxed";

    private static final Logger LOGGER = LoggerFactory.getLogger(BpmnFeelSettings.class);

    private static volatile Boolean forced;

    private BpmnFeelSettings() {
    }

    /**
     * Whether BPMN FEEL is sandboxed: a forced value when one is set, else the configured one, else <code>true</code>.
     */
    public static boolean isSandboxed() {
        Boolean value = forced;
        if (value != null) {
            return value;
        }
        return configured().orElse(true);
    }

    /**
     * Forces the setting, regardless of configuration, until called again; <code>null</code> returns to the
     * configuration.
     */
    public static void setSandboxed(Boolean sandboxed) {
        forced = sandboxed;
    }

    /**
     * The configured value, when the configuration names one. A value other than <code>true</code> or
     * <code>false</code> is reported and ignored, so a typo leaves the engine sandboxed rather than opening it.
     */
    public static Optional<Boolean> configured() {
        Optional<String> value = ConfigResolverHolder.getConfigResolver().getConfigProperty(SANDBOXED_PROPERTY, String.class);
        if (value.isEmpty() || value.get().isBlank()) {
            return Optional.empty();
        }
        switch (value.get().trim().toLowerCase(Locale.ROOT)) {
            case "true":
                return Optional.of(true);
            case "false":
                return Optional.of(false);
            default:
                LOGGER.warn("Ignoring '{}' as the value of {}: expected true or false. FEEL stays sandboxed.", value.get(), SANDBOXED_PROPERTY);
                return Optional.empty();
        }
    }
}
