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
package org.jbpm.process.builder.action;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Each dialect must reach its own compiler.
 *
 * {@link ActionCompiler#accept(String)} used to compare the argument with itself and so accepted everything, which made
 * whichever compiler did not override it a silent catch-all for every dialect nobody claimed.
 */
class ActionCompilerRegistryTest {

    @Test
    void eachDialectReachesItsOwnCompiler() {
        assertThat(ActionCompilerRegistry.instance().find("java")).isInstanceOf(JavaActionCompiler.class);
        assertThat(ActionCompilerRegistry.instance().find("mvel")).isInstanceOf(MVELActionCompiler.class);
        assertThat(ActionCompilerRegistry.instance().find("FEEL")).isInstanceOf(FeelActionCompiler.class);
    }

    @Test
    void theJavaCompilerStillAnswersForTheJavaLanguageUri() {
        assertThat(ActionCompilerRegistry.instance().find("http://www.java.com/java")).isInstanceOf(JavaActionCompiler.class);
    }

    @Test
    void anUnknownDialectIsReportedInsteadOfSilentlyPickingACompiler() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> ActionCompilerRegistry.instance().find("no-such-dialect"))
                .withMessageContaining("no-such-dialect");
    }
}
