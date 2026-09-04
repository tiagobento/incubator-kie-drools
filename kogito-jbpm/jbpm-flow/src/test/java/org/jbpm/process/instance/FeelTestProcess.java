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
package org.jbpm.process.instance;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.jbpm.process.core.datatype.DataTypeResolver;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.ruleflow.core.RuleFlowProcessFactory;
import org.jbpm.ruleflow.core.WorkflowElementIdentifierFactory;
import org.kie.api.definition.process.WorkflowElementIdentifier;
import org.kie.kogito.Application;
import org.kie.kogito.Config;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;
import org.kie.kogito.process.Processes;
import org.kie.kogito.process.impl.AbstractProcessConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Runs a three-node process - start, action, end - and hands the test the live {@link KogitoProcessContext} the action
 * node sees, with a real process instance and a real node instance behind it. The FEEL scope is built from exactly
 * that context at runtime, so a test that wants the real thing rather than an empty context comes here.
 */
public final class FeelTestProcess {

    public static final String PROCESS_ID = "org.jbpm.feel.Captured";
    public static final String PROCESS_NAME = "Captured";
    public static final String PROCESS_VERSION = "1.0";
    public static final String ACTION_NODE_NAME = "Action";

    private static final WorkflowElementIdentifier START = WorkflowElementIdentifierFactory.fromExternalFormat("start");
    private static final WorkflowElementIdentifier ACTION = WorkflowElementIdentifierFactory.fromExternalFormat("action");
    private static final WorkflowElementIdentifier END = WorkflowElementIdentifierFactory.fromExternalFormat("end");

    private FeelTestProcess() {
    }

    /**
     * Starts an instance with the given variables, declared with the type <code>Object</code>, and returns what
     * <code>body</code> computed while the action node was running. Whatever <code>body</code> threw is rethrown.
     */
    public static <T> T inAction(Map<String, Object> variables, Function<KogitoProcessContext, T> body) {
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<RuntimeException> failure = new AtomicReference<>();

        RuleFlowProcessFactory factory = RuleFlowProcessFactory.createProcess(PROCESS_ID)
                .name(PROCESS_NAME)
                .version(PROCESS_VERSION)
                .packageName("org.jbpm");
        variables.keySet().forEach(name -> factory.variable(name, DataTypeResolver.fromClass(Object.class)));
        RuleFlowProcess process = factory
                .startNode(START).name("Start").done()
                .actionNode(ACTION).name(ACTION_NODE_NAME).action(context -> {
                    try {
                        result.set(body.apply(context));
                    } catch (RuntimeException e) {
                        failure.set(e);
                    }
                }).done()
                .endNode(END).name("End").done()
                .connection(START, ACTION)
                .connection(ACTION, END)
                .validate()
                .getProcess();

        Application application = mock(Application.class);
        Config config = mock(Config.class);
        when(application.config()).thenReturn(config);
        when(config.get(any())).thenReturn(mock(AbstractProcessConfig.class));
        when(application.get(Processes.class)).thenReturn(mock(Processes.class));
        LightProcessRuntime runtime = new LightProcessRuntime(
                new LightProcessRuntimeContext(Collections.singletonList(process)), new LightProcessRuntimeServiceProvider(), application);

        runtime.startProcess(PROCESS_ID, variables);
        if (failure.get() != null) {
            throw failure.get();
        }
        return result.get();
    }
}
