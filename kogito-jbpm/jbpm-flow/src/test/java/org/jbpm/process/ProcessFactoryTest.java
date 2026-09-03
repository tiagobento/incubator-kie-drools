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
package org.jbpm.process;

import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.expression.ExpressionLanguages;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.jbpm.test.util.AbstractBaseTest;
import org.jbpm.test.util.TestJavaLanguage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.slf4j.LoggerFactory;

public class ProcessFactoryTest extends AbstractBaseTest {

    private static WorkflowElementIdentifier one = WorkflowElementIdentifierFactory.fromExternalFormat("one");
    private static WorkflowElementIdentifier two = WorkflowElementIdentifierFactory.fromExternalFormat("two");
    private static WorkflowElementIdentifier three = WorkflowElementIdentifierFactory.fromExternalFormat("three");
    private static WorkflowElementIdentifier four = WorkflowElementIdentifierFactory.fromExternalFormat("four");
    private static WorkflowElementIdentifier five = WorkflowElementIdentifierFactory.fromExternalFormat("five");

    // the scripts below are declared in Java; validating them needs the language to be known, nothing more
    private static final ExpressionLanguage JAVA = new TestJavaLanguage();

    @BeforeAll
    static void registerJava() {
        ExpressionLanguages.register(JAVA);
    }

    @AfterAll
    static void unregisterJava() {
        ExpressionLanguages.unregister(JAVA);
    }

    public void addLogger() {
        logger = LoggerFactory.getLogger(this.getClass());
    }

    @Test
    public void testProcessFactory() throws Exception {
        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess("org.drools.core.process");
        factory
                // header
                .name("My process").packageName("org.drools")
                // nodes
                .startNode(one).name("Start").done()
                .actionNode(two).name("Action")
                .action("java",
                        "System.out.println(\"Action\");")
                .done()
                .endNode(three).name("End").done()
                // connections
                .connection(one,
                        two)
                .connection(two,
                        three);
        factory.validate().getProcess();
    }
}
