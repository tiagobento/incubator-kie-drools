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
package org.kie.kogito.codegen.process;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Properties;

import org.drools.codegen.common.GeneratedFile;
import org.jbpm.process.instance.impl.feel.BpmnFeelSettings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.kie.kogito.codegen.api.context.KogitoBuildContext;
import org.kie.kogito.codegen.core.io.CollectedResourceProducer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The application's {@value BpmnFeelSettings#SANDBOXED_PROPERTY} decides what its FEEL expressions may do, and the
 * build validates them with that decision in force: what the application refuses at runtime fails its build.
 */
class FeelSandboxSettingCodegenTest {

    private static final Path BASE_PATH = Paths.get("src/test/resources/").toAbsolutePath();
    private static final Path EXTERNAL_FUNCTION = BASE_PATH.resolve("feel/FeelExternalFunction.bpmn2");
    private static final Path ENGINE_REACH = BASE_PATH.resolve("feel/FeelEngineReach.bpmn2");

    @AfterEach
    void backToTheConfiguredMode() {
        BpmnFeelSettings.setSandboxed(null);
    }

    private static Collection<GeneratedFile> generate(KogitoBuildContext context, Path model) {
        return ProcessCodegen.ofCollectedResources(context, CollectedResourceProducer.fromFiles(BASE_PATH, model.toFile())).generate();
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    void anExternalFunctionFailsTheBuildByDefault(KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext context = contextBuilder.build();
        assertThatExceptionOfType(ProcessCodegenException.class).isThrownBy(() -> generate(context, EXTERNAL_FUNCTION));
        assertThat(BpmnFeelSettings.isSandboxed()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    void aReachIntoTheEngineFailsTheBuildByDefault(KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext context = contextBuilder.build();
        assertThatExceptionOfType(ProcessCodegenException.class).isThrownBy(() -> generate(context, ENGINE_REACH));
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    void anApplicationThatTurnsTheSandboxOffBuildsBoth(KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext context = contextBuilder.build();
        context.setApplicationProperty(BpmnFeelSettings.SANDBOXED_PROPERTY, "false");
        assertThat(generate(context, EXTERNAL_FUNCTION)).isNotEmpty();
        assertThat(generate(context, ENGINE_REACH)).isNotEmpty();
        // the build left the application's setting in force, which is what its generated code will run under
        assertThat(BpmnFeelSettings.isSandboxed()).isFalse();
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    void aBuildWithoutTheSettingReturnsToTheDefaultRatherThanKeepingThePreviousOne(KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext open = contextBuilder.build();
        open.setApplicationProperty(BpmnFeelSettings.SANDBOXED_PROPERTY, "false");
        assertThat(generate(open, EXTERNAL_FUNCTION)).isNotEmpty();
        // a context of its own, with properties of its own: the builder hands every context the same property store
        KogitoBuildContext unset = contextBuilder.withApplicationProperties(new Properties()).build();
        assertThatExceptionOfType(ProcessCodegenException.class).isThrownBy(() -> generate(unset, EXTERNAL_FUNCTION));
        assertThat(BpmnFeelSettings.isSandboxed()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("org.kie.kogito.codegen.api.utils.KogitoContextTestUtils#contextBuilders")
    void anExplicitTrueIsTheDefault(KogitoBuildContext.Builder contextBuilder) {
        KogitoBuildContext context = contextBuilder.build();
        context.setApplicationProperty(BpmnFeelSettings.SANDBOXED_PROPERTY, "true");
        assertThatExceptionOfType(ProcessCodegenException.class).isThrownBy(() -> generate(context, EXTERNAL_FUNCTION));
    }
}
