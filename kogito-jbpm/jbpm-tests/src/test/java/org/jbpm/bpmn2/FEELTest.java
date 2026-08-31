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

import java.math.BigDecimal;
import java.util.Optional;

import org.jbpm.bpmn2.feel.FeelDataInputAssignmentModel;
import org.jbpm.bpmn2.feel.FeelDataInputAssignmentProcess;
import org.jbpm.bpmn2.feel.FeelDocumentDefaultGatewayModel;
import org.jbpm.bpmn2.feel.FeelDocumentDefaultGatewayProcess;
import org.jbpm.bpmn2.feel.FeelScriptTaskModel;
import org.jbpm.bpmn2.feel.FeelScriptTaskProcess;
import org.jbpm.bpmn2.feel.FeelScriptTaskUnmatchedModel;
import org.jbpm.bpmn2.feel.FeelScriptTaskUnmatchedProcess;
import org.jbpm.bpmn2.feel.FeelTransformationModel;
import org.jbpm.bpmn2.feel.FeelTransformationProcess;
import org.jbpm.bpmn2.feel.GatewayFEELModel;
import org.jbpm.bpmn2.feel.GatewayFEELProcess;
import org.jbpm.test.utils.EventTrackerProcessListener;
import org.jbpm.test.utils.ProcessTestHelper;
import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.workitem.KogitoWorkItem;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemHandler;
import org.kie.kogito.internal.process.workitem.KogitoWorkItemManager;
import org.kie.kogito.internal.process.workitem.WorkItemTransition;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.ProcessInstanceExecutionException;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

public class FEELTest extends JbpmBpmn2TestCase {
    @Test
    public void testGatewayFEEL() {
        Application app = ProcessTestHelper.newApplication();
        EventTrackerProcessListener eventTrackerProcessListener = new EventTrackerProcessListener();

        ProcessTestHelper.registerProcessEventListener(app, eventTrackerProcessListener);
        org.kie.kogito.process.Process<GatewayFEELModel> process = GatewayFEELProcess.newProcess(app);
        GatewayFEELModel model = process.createModel();
        model.setVA(Boolean.TRUE);
        model.setVB(Boolean.FALSE);
        ProcessInstance<GatewayFEELModel> procInstance1 = process.createInstance(model);
        procInstance1.start();

        assertThat(procInstance1.variables().getTask1()).isEqualTo("ok");
        assertThat(procInstance1.variables().getTask2()).isEqualTo("ok");
        assertThat(procInstance1.variables().getTask3()).isNull();

        assertThat(eventTrackerProcessListener.tracked()).anyMatch(ProcessTestHelper.triggered("Task2"))
                .anyMatch(ProcessTestHelper.triggered("VA and not(VB)"));

        model.setVA(Boolean.FALSE);
        model.setVB(Boolean.TRUE);

        ProcessInstance<GatewayFEELModel> procInstance2 = process.createInstance(model);
        procInstance2.start();

        assertThat(procInstance2.variables().getTask1()).isEqualTo("ok");
        assertThat(procInstance2.variables().getTask2()).isNull();
        assertThat(procInstance2.variables().getTask3()).isEqualTo("ok");
        assertThat(eventTrackerProcessListener.tracked()).anyMatch(ProcessTestHelper.triggered("Task3"))
                .anyMatch(ProcessTestHelper.triggered("VB or not(VA)"));
    }

    @Test
    public void testFeelScriptTaskWritesBackTheContextItReturns() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelScriptTaskModel> process = FeelScriptTaskProcess.newProcess(app);
        FeelScriptTaskModel model = process.createModel();
        model.setX(60);
        model.setY(70);

        ProcessInstance<FeelScriptTaskModel> instance = process.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        // an explicit scriptFormat, and a script with none that follows the document expressionLanguage
        assertThat(instance.variables().getTotal()).isEqualTo(BigDecimal.valueOf(130));
        assertThat(instance.variables().getTier()).isEqualTo("gold");
    }

    @Test
    public void testFeelScriptTaskTakesTheOtherBranchOfItsCondition() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelScriptTaskModel> process = FeelScriptTaskProcess.newProcess(app);
        FeelScriptTaskModel model = process.createModel();
        model.setX(1);
        model.setY(2);

        ProcessInstance<FeelScriptTaskModel> instance = process.createInstance(model);
        instance.start();

        assertThat(instance.variables().getTotal()).isEqualTo(BigDecimal.valueOf(3));
        assertThat(instance.variables().getTier()).isEqualTo("standard");
    }

    @Test
    public void testFeelScriptTaskIgnoresAnEntryThatIsNotAProcessVariable() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelScriptTaskUnmatchedModel> process = FeelScriptTaskUnmatchedProcess.newProcess(app);

        ProcessInstance<FeelScriptTaskUnmatchedModel> instance = process.createInstance(process.createModel());
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getKnown()).isEqualTo(BigDecimal.ONE);
    }

    @Test
    public void testDocumentExpressionLanguageDrivesPlaceholderInterpolation() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler,
                    KogitoWorkItem workItem, WorkItemTransition transition) {
                // "upper case(...)" is a FEEL built-in: MVEL could not have produced these values
                assertThat(workItem.getParameter("greeting")).isEqualTo("HELLO world");
                // a lone #{expression}: evaluated, not stripped of its #{} and read back as a constant
                assertThat(workItem.getParameter("shouted")).isEqualTo("HELLO");
                return Optional.empty();
            }
        });

        org.kie.kogito.process.Process<FeelDataInputAssignmentModel> process = FeelDataInputAssignmentProcess.newProcess(app);
        FeelDataInputAssignmentModel model = process.createModel();
        model.setFirst("hello");
        model.setSecond("world");

        ProcessInstance<FeelDataInputAssignmentModel> instance = process.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testGatewayConditionWithNoLanguageFollowsTheDocumentDefault() {
        // "score in [50..100]" is a FEEL range test: MVEL could not evaluate it, so reaching either branch at all
        // proves the conditions were compiled as FEEL purely from <definitions expressionLanguage>
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelDocumentDefaultGatewayModel> process = FeelDocumentDefaultGatewayProcess.newProcess(app);

        FeelDocumentDefaultGatewayModel passing = process.createModel();
        passing.setScore(70);
        ProcessInstance<FeelDocumentDefaultGatewayModel> passed = process.createInstance(passing);
        passed.start();
        assertThat(passed.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(passed.variables().getGrade()).isEqualTo("pass");

        FeelDocumentDefaultGatewayModel failing = process.createModel();
        failing.setScore(20);
        ProcessInstance<FeelDocumentDefaultGatewayModel> failed = process.createInstance(failing);
        failed.start();
        assertThat(failed.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(failed.variables().getGrade()).isEqualTo("fail");
    }

    @Test
    public void testTransformationWithNoLanguageFollowsTheDocumentDefaultAndMayReturnAnyType() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler() {
            @Override
            public Optional<WorkItemTransition> activateWorkItemHandler(KogitoWorkItemManager manager, KogitoWorkItemHandler handler,
                    KogitoWorkItem workItem, WorkItemTransition transition) {
                // a transformation returns a value, not a condition: a String here would previously have been
                // rejected by the boolean-only FEEL evaluator
                assertThat(workItem.getParameter("shouted")).isEqualTo("WORLD!");
                return Optional.empty();
            }
        });

        org.kie.kogito.process.Process<FeelTransformationModel> process = FeelTransformationProcess.newProcess(app);
        FeelTransformationModel model = process.createModel();
        model.setName("world");

        ProcessInstance<FeelTransformationModel> instance = process.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testGatewayFEELWrong() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<GatewayFEELModel> process = GatewayFEELProcess.newProcess(app);
        ProcessInstance<GatewayFEELModel> instance = process.createInstance(process.createModel());
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ERROR);
        assertThat(instance.error().isPresent()).isTrue();
        assertThatExceptionOfType(ProcessInstanceExecutionException.class)
                .isThrownBy(instance::checkError).withMessageContaining("org.jbpm.process.instance.impl.FeelReturnValueEvaluatorException")
                .withMessageContaining("ERROR Unknown variable 'VA'")
                .withMessageContaining("ERROR Unknown variable name 'VB'");

    }

}
