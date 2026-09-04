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

import java.util.Calendar;

import org.jbpm.bpmn2.feel.FEELCHILDProcess;
import org.jbpm.bpmn2.feel.FeelCallActivityModel;
import org.jbpm.bpmn2.feel.FeelCallActivityProcess;
import org.jbpm.bpmn2.feel.FeelMultiInstanceModel;
import org.jbpm.bpmn2.feel.FeelMultiInstanceProcess;
import org.jbpm.bpmn2.feel.FeelOnEntryOnExitModel;
import org.jbpm.bpmn2.feel.FeelOnEntryOnExitProcess;
import org.jbpm.bpmn2.feel.FeelPrecedenceModel;
import org.jbpm.bpmn2.feel.FeelPrecedenceProcess;
import org.jbpm.bpmn2.feel.FeelSlaDueDateModel;
import org.jbpm.bpmn2.feel.FeelSlaDueDateProcess;
import org.jbpm.bpmn2.feel.FeelTimerModel;
import org.jbpm.bpmn2.feel.FeelTimerProcess;
import org.jbpm.bpmn2.feel.FeelTypedVariablesModel;
import org.jbpm.bpmn2.feel.FeelTypedVariablesProcess;
import org.jbpm.bpmn2.feel.UnknownExpressionLanguageModel;
import org.jbpm.bpmn2.feel.UnknownExpressionLanguageProcess;
import org.jbpm.bpmn2.xml.XmlBPMNProcessDumper;
import org.jbpm.process.workitem.builtin.SystemOutWorkItemHandler;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.test.utils.ProcessTestHelper;
import org.jbpm.workflow.core.WorkflowProcess;
import org.jbpm.workflow.core.node.DynamicNode;
import org.jbpm.workflow.instance.impl.WorkflowProcessInstanceImpl;
import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.process.ProcessInstance;
import org.kie.kogito.process.workitems.impl.DefaultKogitoWorkItemHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * How a BPMN document decides which language an expression is written in.
 *
 * A field's own language wins; a field that declares none follows
 * <code>&lt;definitions expressionLanguage&gt;</code>; and a document that declares nothing, or something we do not
 * recognise, keeps the behaviour it has always had.
 */
public class ExpressionLanguageTest extends JbpmBpmn2TestCase {

    @Test
    public void testAFieldsOwnLanguageWinsOverTheDocumentDefault() {
        // the document says FEEL, but one flow declares MVEL and uses Java-bean syntax FEEL cannot parse
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelPrecedenceModel> process = FeelPrecedenceProcess.newProcess(app);

        FeelPrecedenceModel high = process.createModel();
        high.setScore(10);
        ProcessInstance<FeelPrecedenceModel> tookMvel = process.createInstance(high);
        tookMvel.start();
        assertThat(tookMvel.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(tookMvel.variables().getPath()).isEqualTo("mvel");

        FeelPrecedenceModel low = process.createModel();
        low.setScore(3);
        ProcessInstance<FeelPrecedenceModel> tookFeel = process.createInstance(low);
        tookFeel.start();
        assertThat(tookFeel.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(tookFeel.variables().getPath()).isEqualTo("feel");
    }

    @Test
    public void testAnUnrecognisedOnEntryScriptFormatIsReported() {
        // it used to be absorbed by MVEL, silently: a format the engine does not know is a mistake in the model
        assertThatThrownBy(() -> createKogitoProcessRuntime("org/jbpm/bpmn2/feel/BPMN2-UnknownOnEntryScriptFormat.bpmn2"))
                .hasMessageContaining("Unknown scriptFormat 'http://www.groovy-lang.org/groovy'");
    }

    @Test
    public void testAnUnrecognisedDocumentLanguageBehavesAsMvel() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<UnknownExpressionLanguageModel> process = UnknownExpressionLanguageProcess.newProcess(app);

        UnknownExpressionLanguageModel model = process.createModel();
        model.setScore(10);
        ProcessInstance<UnknownExpressionLanguageModel> instance = process.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getPath()).isEqualTo("hit");
    }

    @Test
    public void testOnEntryAndOnExitScriptsFollowTheDocumentDefault() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<FeelOnEntryOnExitModel> process = FeelOnEntryOnExitProcess.newProcess(app);

        FeelOnEntryOnExitModel model = process.createModel();
        model.setWho("bob");
        ProcessInstance<FeelOnEntryOnExitModel> instance = process.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getEntered()).isEqualTo("in-bob");
        assertThat(instance.variables().getLeft()).isEqualTo("out-bob");
    }

    @Test
    public void testTheProcessDescriptionIsInterpolatedWithTheDocumentLanguage() {
        // customDescription resolves through WorkflowProcessImpl's expression evaluator, a different code path from
        // the one a node uses
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<FeelOnEntryOnExitModel> process = FeelOnEntryOnExitProcess.newProcess(app);

        FeelOnEntryOnExitModel model = process.createModel();
        model.setWho("bob");
        ProcessInstance<FeelOnEntryOnExitModel> instance = process.createInstance(model);
        instance.start();

        assertThat(instance.description()).isEqualTo("run for BOB");
    }

    @Test
    public void testAFeelDocumentRoundTripsAsFeelAndAnMvelOneStaysMvel() throws Exception {
        // the dumper used to write a hard-coded MVEL URI into every header, so a FEEL document came back as MVEL
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/feel/BPMN2-FeelScriptTask.bpmn2");
        WorkflowProcess feelProcess = (WorkflowProcess) kruntime.getKieBase().getProcess("FeelScriptTask");
        assertThat(XmlBPMNProcessDumper.INSTANCE.dump(feelProcess))
                .contains("expressionLanguage=\"" + XmlBPMNProcessDumper.DMN_FEEL_LANGUAGE + "\"");

        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/feel/BPMN2-UnknownExpressionLanguage.bpmn2");
        WorkflowProcess mvelProcess = (WorkflowProcess) kruntime.getKieBase().getProcess("UnknownExpressionLanguage");
        assertThat(XmlBPMNProcessDumper.INSTANCE.dump(mvelProcess))
                .contains("expressionLanguage=\"" + XmlBPMNProcessDumper.MVEL_LANGUAGE + "\"");
    }

    @Test
    public void testTheDocumentLanguageIsStoredOnlyWhenItIsFeel() throws Exception {
        // MVEL stays null, so nothing downstream sees a new value for the documents that already declare it
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/feel/BPMN2-FeelScriptTask.bpmn2");
        assertThat(((WorkflowProcess) kruntime.getKieBase().getProcess("FeelScriptTask")).getExpressionLanguage())
                .isEqualTo("FEEL");

        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/feel/BPMN2-UnknownExpressionLanguage.bpmn2");
        assertThat(((WorkflowProcess) kruntime.getKieBase().getProcess("UnknownExpressionLanguage")).getExpressionLanguage())
                .isNull();
    }

    @Test
    public void testAFeelDocumentBuildsOutsideCodeGenerationToo() throws Exception {
        // the legacy build path resolves dialects through ProcessDialectRegistry rather than the codegen visitors,
        // so a FEEL document has to be buildable there as well
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/feel/BPMN2-FeelDataInputAssignment.bpmn2");
        assertThat(kruntime.getKieBase().getProcess("FeelDataInputAssignment")).isNotNull();

        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/feel/BPMN2-FeelTransformation.bpmn2");
        assertThat(kruntime.getKieBase().getProcess("FeelTransformation")).isNotNull();
    }

    @Test
    public void testACalledElementIsInterpolatedWithTheDocumentLanguage() {
        // calledElement resolves through the node interpolation path, not the assignment one
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelCallActivityModel> process = FeelCallActivityProcess.newProcess(app);
        FEELCHILDProcess.newProcess(app);

        FeelCallActivityModel model = process.createModel();
        model.setChildId("feelchild");
        ProcessInstance<FeelCallActivityModel> instance = process.createInstance(model);
        instance.start();

        // the child is only found if "upper case(childId)" was evaluated as FEEL
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }

    @Test
    public void testAnAdHocConditionFollowsTheDocumentDefault() throws Exception {
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/feel/BPMN2-FeelAdHoc.bpmn2");
        RuleFlowProcess process = (RuleFlowProcess) kruntime.getKieBase().getProcess("FeelAdHoc");

        DynamicNode adHoc = java.util.Arrays.stream(process.getNodes())
                .filter(DynamicNode.class::isInstance)
                .map(DynamicNode.class::cast)
                .findFirst()
                .orElseThrow();

        // it used to be seeded with the Java language regardless of what the document said
        assertThat(adHoc.getLanguage()).isEqualTo("FEEL");
        assertThat(adHoc.getCompletionCondition()).isEqualTo("done != null");
    }

    @Test
    public void testAnSlaDueDateIsInterpolatedWithTheDocumentLanguage() {
        // the SLA due date resolves through WorkflowProcessInstanceImpl, which carries its own copy of the #{...}
        // logic and was left on MVEL
        Application app = ProcessTestHelper.newApplication();
        // a handler that leaves the task open, so the instance is still writable when the due date is read
        ProcessTestHelper.registerHandler(app, "Human Task", new DefaultKogitoWorkItemHandler());
        org.kie.kogito.process.Process<FeelSlaDueDateModel> process = FeelSlaDueDateProcess.newProcess(app);

        FeelSlaDueDateModel model = process.createModel();
        model.setSeconds(30);
        ProcessInstance<FeelSlaDueDateModel> instance = process.createInstance(model);
        instance.start();

        instance.checkError();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
        // a due date is only set when "PT" + string(seconds) + "S" resolved to a duration FEEL could produce
        assertThat(ProcessTestHelper.executeInWorkflowState(instance, WorkflowProcessInstanceImpl::getSlaDueDate)).isNotNull();
    }

    @Test
    public void testATimerDurationIsInterpolatedWithTheDocumentLanguage() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelTimerModel> process = FeelTimerProcess.newProcess(app);

        FeelTimerModel model = process.createModel();
        model.setSeconds(30);
        ProcessInstance<FeelTimerModel> instance = process.createInstance(model);
        instance.start();

        // an unresolved placeholder would reach the duration parser verbatim and fail the node
        instance.checkError();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_ACTIVE);
    }

    @Test
    public void testFeelResultsAreWrittenBackAsTheTypeTheVariableDeclares() {
        // FEEL has a single number type, BigDecimal, which no typed variable accepts; and it does not recognise
        // java.util.Date as a temporal value, reading it as a bean instead - "when.year" answered 126, not 2026
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelTypedVariablesModel> process = FeelTypedVariablesProcess.newProcess(app);

        Calendar when = Calendar.getInstance();
        when.set(2026, Calendar.MARCH, 10, 12, 0, 0);
        when.set(Calendar.MILLISECOND, 0);

        FeelTypedVariablesModel model = process.createModel();
        model.setX(41);
        model.setWhen(when.getTime());
        ProcessInstance<FeelTypedVariablesModel> instance = process.createInstance(model);
        instance.start();

        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getTotal()).isEqualTo(42);
        assertThat(instance.variables().getYear()).isEqualTo(2026);

        Calendar expected = (Calendar) when.clone();
        expected.add(Calendar.DAY_OF_MONTH, 1);
        assertThat(instance.variables().getLater()).isEqualTo(expected.getTime());
    }

    @Test
    public void testAMultiInstanceCompletionConditionFollowsTheDocumentDefault() {
        Application app = ProcessTestHelper.newApplication();
        ProcessTestHelper.registerHandler(app, "Human Task", new SystemOutWorkItemHandler());
        org.kie.kogito.process.Process<FeelMultiInstanceModel> process = FeelMultiInstanceProcess.newProcess(app);

        FeelMultiInstanceModel model = process.createModel();
        model.setList(java.util.List.of("a", "b"));
        ProcessInstance<FeelMultiInstanceModel> instance = process.createInstance(model);
        instance.start();

        // "count(list) = 2" is FEEL: it is true from the start, so the loop completes without any task being worked
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
    }
}
