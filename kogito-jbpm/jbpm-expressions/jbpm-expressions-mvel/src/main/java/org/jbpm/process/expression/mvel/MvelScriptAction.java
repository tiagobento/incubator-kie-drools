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
package org.jbpm.process.expression.mvel;

import org.jbpm.process.expression.ExpressionScope;
import org.jbpm.process.instance.impl.Action;
import org.jbpm.workflow.instance.impl.MVELProcessHelper;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

/**
 * A script task, on-entry or on-exit script in MVEL, interpreted against the node it runs in.
 */
public class MvelScriptAction implements Action {

    private final String script;

    public MvelScriptAction(String script) {
        this.script = script;
    }

    public String getScript() {
        return script;
    }

    @Override
    public void execute(KogitoProcessContext context) throws Exception {
        ExpressionScope scope = context.getNodeInstance() != null ? ExpressionScope.of(context.getNodeInstance()) : ExpressionScope.of(context.getProcessInstance());
        MVELProcessHelper.evaluator().eval(script, MvelExpressionLanguage.resolverFactory(scope));
    }

    @Override
    public String toString() {
        return "MvelScriptAction(" + script + ")";
    }
}
