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
package org.jbpm.bpmn2;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.expression.ExpressionLanguages;
import org.jbpm.process.expression.ExpressionScope;
import org.jbpm.process.expression.ValidationScope;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * A language the engine has never heard of, brought by the application: the document names it, the parser
 * resolves it, the validator asks it about every expression, and the runtime hands it the conditions and scripts.
 *
 * The language here is deliberately tiny. A condition is the word <code>yes</code> or <code>no</code>; a script names
 * a variable to upper-case; a placeholder names a variable to read.
 */
public class CustomExpressionLanguageTest extends JbpmBpmn2TestCase {

    private static final String DOCUMENT = "org/jbpm/bpmn2/expression/BPMN2-CustomLanguage.bpmn2";

    private final List<String> validated = new java.util.ArrayList<>();

    private final ExpressionLanguage upper = new ExpressionLanguage() {
        @Override
        public String id() {
            return "upper";
        }

        @Override
        public String uri() {
            return "urn:test:upper";
        }

        @Override
        public Set<Surface> surfaces() {
            return EnumSet.of(Surface.CONDITION, Surface.SCRIPT, Surface.INTERPOLATION);
        }

        @Override
        public void validate(Surface surface, String expression, ValidationScope scope, Consumer<String> problems) {
            validated.add(surface + ":" + expression);
            if (surface == Surface.CONDITION && !List.of("yes", "no").contains(expression.trim())) {
                problems.accept("a condition is 'yes' or 'no', not '" + expression + "'");
            }
            if (surface == Surface.SCRIPT && !scope.variableNames().contains(expression.trim())) {
                problems.accept("'" + expression + "' is not a variable this script could upper-case");
            }
        }

        @Override
        public ReturnValueEvaluator evaluator(String expression, Class<?> type, String root) {
            return context -> "yes".equals(expression.trim());
        }

        @Override
        public Action script(String script) {
            String variable = script.trim();
            return context -> context.setVariable(variable, String.valueOf(context.getVariable(variable)).toUpperCase());
        }

        @Override
        public Object interpolate(String placeholderBody, ExpressionScope scope) {
            return scope.get(placeholderBody.trim());
        }
    };

    @BeforeEach
    void register() {
        ExpressionLanguages.register(upper);
    }

    @AfterEach
    void unregister() {
        ExpressionLanguages.unregister(upper);
    }

    @Test
    public void testADocumentInALanguageTheApplicationBroughtRunsThroughIt() throws Exception {
        kruntime = createKogitoProcessRuntime(DOCUMENT);

        KogitoProcessInstance instance = kruntime.startProcess("CustomLanguage", Map.of("who", "bob", "path", "quiet"));

        assertThat(instance.getState()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(instance.getVariables()).containsEntry("who", "BOB").containsEntry("path", "quiet");
    }

    @Test
    public void testTheLanguageIsAskedToCheckEveryExpressionWhenTheProcessIsBuilt() throws Exception {
        kruntime = createKogitoProcessRuntime(DOCUMENT);

        assertThat(validated).containsExactlyInAnyOrder("CONDITION:yes", "CONDITION:no", "SCRIPT:who", "SCRIPT:path");
    }

    @Test
    public void testWhatTheLanguageRefusesFailsTheBuild() {
        ExpressionLanguages.unregister(upper);
        ExpressionLanguages.register(new ExpressionLanguage() {
            @Override
            public String id() {
                return "upper";
            }

            @Override
            public String uri() {
                return "urn:test:upper";
            }

            @Override
            public Set<Surface> surfaces() {
                return EnumSet.of(Surface.CONDITION);
            }
        });
        try {
            Throwable failure = catchThrowable(() -> createKogitoProcessRuntime(DOCUMENT));
            assertThat(failure).isNotNull();
            assertThat(failure.getMessage()).contains("The expression language 'upper' cannot be used for a script: 'who'");
        } finally {
            ExpressionLanguages.find("urn:test:upper").ifPresent(ExpressionLanguages::unregister);
        }
    }
}
