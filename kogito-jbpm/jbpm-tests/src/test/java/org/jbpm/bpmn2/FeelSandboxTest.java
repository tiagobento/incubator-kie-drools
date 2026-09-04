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

import java.util.Map;

import org.jbpm.bpmn2.feel.FeelKcontextModel;
import org.jbpm.bpmn2.feel.FeelKcontextOpenModel;
import org.jbpm.bpmn2.feel.FeelKcontextOpenProcess;
import org.jbpm.bpmn2.feel.FeelKcontextProcess;
import org.jbpm.process.instance.impl.feel.BpmnFeelSettings;
import org.jbpm.test.utils.ProcessTestHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.kie.kogito.Application;
import org.kie.kogito.internal.process.runtime.KogitoProcessInstance;
import org.kie.kogito.process.ProcessInstance;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a FEEL expression, a FEEL script and a <code>#{...}</code> placeholder see of the instance, through generated
 * code and through the in-memory build path, sandboxed and not.
 */
public class FeelSandboxTest extends JbpmBpmn2TestCase {

    @AfterEach
    void backToTheConfiguredMode() {
        BpmnFeelSettings.setSandboxed(null);
    }

    private static ProcessInstance<FeelKcontextModel> runKcontext(String who) {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelKcontextModel> process = FeelKcontextProcess.newProcess(app);
        FeelKcontextModel model = process.createModel();
        model.setWho(who);
        ProcessInstance<FeelKcontextModel> instance = process.createInstance(model);
        instance.start();
        return instance;
    }

    @Test
    public void testAConditionAndAScriptReadTheSandboxedKcontext() {
        ProcessInstance<FeelKcontextModel> instance = runKcontext("bob");
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        // the gateway condition read processId, state, startDate and variables; the script read nodeName and processId
        assertThat(instance.variables().getPath()).isEqualTo("hit");
        assertThat(instance.variables().getNodeSeen()).isEqualTo("Record");
        assertThat(instance.variables().getProcId()).isEqualTo("FeelKcontext");
    }

    @Test
    public void testAPlaceholderReadsTheSandboxedViewsAndTheMvelNames() {
        ProcessInstance<FeelKcontextModel> instance = runKcontext("bob");
        assertThat(instance.description()).isEqualTo("FeelKcontext for bob, instance " + instance.id());
    }

    @Test
    public void testTheSameDocumentRunsUnsandboxed() {
        // every reach in it is inside the documented shape, which the live objects answer to as well
        BpmnFeelSettings.setSandboxed(false);
        ProcessInstance<FeelKcontextModel> instance = runKcontext("alice");
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.variables().getPath()).isEqualTo("hit");
        assertThat(instance.variables().getNodeSeen()).isEqualTo("Record");
        assertThat(instance.description()).isEqualTo("FeelKcontext for alice, instance " + instance.id());
    }

    @Test
    public void testNotSandboxedAPlaceholderReachesTheProcessDefinition() {
        BpmnFeelSettings.setSandboxed(false);
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelKcontextOpenModel> process = FeelKcontextOpenProcess.newProcess(app);
        ProcessInstance<FeelKcontextOpenModel> instance = process.createInstance(process.createModel());
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.description()).isEqualTo("definition FeelKcontextOpen");
    }

    @Test
    public void testSandboxedTheSameReachIsRefused() {
        Application app = ProcessTestHelper.newApplication();
        org.kie.kogito.process.Process<FeelKcontextOpenModel> process = FeelKcontextOpenProcess.newProcess(app);
        ProcessInstance<FeelKcontextOpenModel> instance = process.createInstance(process.createModel());
        // processInstance is the documented view and "process" is not one of its keys, so the placeholder does not
        // compile. A placeholder that cannot be resolved is logged and left in place - the behaviour MVEL has always
        // had - so the instance runs, and the description shows the reach was refused rather than answered
        instance.start();
        assertThat(instance.status()).isEqualTo(ProcessInstance.STATE_COMPLETED);
        assertThat(instance.description()).isEqualTo("definition #{processInstance.process.id}");
    }

    @Test
    public void testTheSandboxedKcontextWorksThroughTheInMemoryBuildPathToo() throws Exception {
        // the legacy path builds the evaluators through ProcessDialectRegistry rather than generated code
        kruntime = createKogitoProcessRuntime("org/jbpm/bpmn2/feel/BPMN2-FeelKcontext.bpmn2");
        KogitoProcessInstance instance = kruntime.startProcess("FeelKcontext", Map.of("who", "carol"));
        assertThat(instance.getState()).isEqualTo(KogitoProcessInstance.STATE_COMPLETED);
        assertThat(instance.getVariables()).containsEntry("path", "hit").containsEntry("nodeSeen", "Record").containsEntry("procId", "FeelKcontext");
    }
}
