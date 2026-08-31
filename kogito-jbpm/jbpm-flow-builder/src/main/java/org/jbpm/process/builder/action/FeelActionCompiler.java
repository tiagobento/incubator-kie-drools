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

import org.jbpm.process.instance.impl.actions.FeelScriptAction;
import org.jbpm.util.ExpressionLanguages;
import org.jbpm.workflow.core.impl.NodeImpl;
import org.kie.kogito.internal.utils.ConversionUtils;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;

/**
 * Compiles an onEntry/onExit script written in FEEL, with the same semantics as a FEEL script task: the expression
 * evaluates to a context whose entries are written back to the process variables they name.
 */
public class FeelActionCompiler implements ActionCompiler {

    @Override
    public String[] dialects() {
        return new String[] { ExpressionLanguages.FEEL };
    }

    @Override
    public Expression buildAction(NodeImpl nodeImpl, String script) {
        return new ObjectCreationExpr(null,
                StaticJavaParser.parseClassOrInterfaceType(FeelScriptAction.class.getName()),
                NodeList.nodeList(new StringLiteralExpr(ConversionUtils.sanitizeString(script))));
    }
}
