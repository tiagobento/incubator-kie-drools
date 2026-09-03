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
package org.jbpm.compiler.canonical;

import java.util.EnumSet;
import java.util.Set;

import org.jbpm.process.core.ContextResolver;
import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.expression.ExpressionLanguage.Surface;
import org.jbpm.process.expression.ExpressionLanguages;
import org.jbpm.workflow.core.node.ActionNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * What the generated code says for an expression: the language's own Java when it compiles ahead of time, a call
 * back into the language at runtime otherwise.
 */
class ExpressionCodegenTest {

    private final ExpressionLanguage interpreted = new ExpressionLanguage() {
        @Override
        public String id() {
            return "interpreted";
        }

        @Override
        public String uri() {
            return "urn:test:interpreted";
        }

        @Override
        public Set<Surface> surfaces() {
            return EnumSet.allOf(Surface.class);
        }
    };

    private final ExpressionLanguage compiled = new ExpressionLanguage() {
        @Override
        public String id() {
            return "compiled";
        }

        @Override
        public String uri() {
            return "urn:test:compiled";
        }

        @Override
        public Set<Surface> surfaces() {
            return EnumSet.allOf(Surface.class);
        }

        @Override
        public String compile(Surface surface, String expression, Class<?> type, String root, ContextResolver scope) {
            return surface == Surface.SCRIPT ? "kcontext -> { System.out.println(\"" + expression + "\"); }" : "kcontext -> " + expression;
        }
    };

    private final ExpressionCodegen codegen = ExpressionCodegen.of(getClass().getClassLoader());

    @AfterEach
    void unregister() {
        ExpressionLanguages.unregister(interpreted);
        ExpressionLanguages.unregister(compiled);
    }

    @Test
    void aLanguageWithoutAheadOfTimeCompilationIsCalledAtRuntime() {
        ExpressionLanguages.register(interpreted);
        assertThat(codegen.evaluator(new ActionNode(), Surface.CONDITION, "urn:test:interpreted", "a > \"b\"", Boolean.class, null).toString())
                .isEqualTo("org.jbpm.process.expression.ExpressionLanguages.require(\"interpreted\").evaluator(\"a > \\\"b\\\"\", java.lang.Boolean.class, null)");
        assertThat(codegen.script(new ActionNode(), "interpreted", "do it").toString())
                .isEqualTo("org.jbpm.process.expression.ExpressionLanguages.require(\"interpreted\").script(\"do it\")");
    }

    @Test
    void aLanguageThatCompilesSuppliesTheCodeItself() {
        ExpressionLanguages.register(compiled);
        assertThat(codegen.evaluator(new ActionNode(), Surface.CONDITION, "compiled", "true", Boolean.class, null).toString())
                .isEqualTo("kcontext -> true");
        assertThat(codegen.script(new ActionNode(), "urn:test:compiled", "hi").toString())
                .contains("System.out.println(\"hi\")");
    }

    @Test
    void anUnavailableLanguageIsReportedWhenTheCodeIsGenerated() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> codegen.script(new ActionNode(), "urn:nowhere", "x"))
                .withMessageContaining("No expression language answers to 'urn:nowhere'");
    }

    @Test
    void noDialectMeansTheDefault() {
        assertThatExceptionOfType(IllegalArgumentException.class)
                .isThrownBy(() -> codegen.script(new ActionNode(), null, "x"))
                .withMessageContaining("No expression language answers to 'mvel'");
    }
}
