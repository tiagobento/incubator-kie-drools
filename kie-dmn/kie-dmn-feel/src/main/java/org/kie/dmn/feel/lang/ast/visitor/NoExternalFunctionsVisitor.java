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
package org.kie.dmn.feel.lang.ast.visitor;

import org.kie.dmn.feel.lang.ast.ASTNode;
import org.kie.dmn.feel.lang.ast.FunctionDefNode;

/**
 * Detects whether an expression declares an <code>external</code> function. An external function definition performs a
 * reflective invocation of an arbitrary class, so hosts that evaluate expressions coming from outside the application
 * (BPMN, for instance) need to reject them. <code>external</code> is a reserved keyword and can therefore only appear
 * as the EXTERNAL token of a function definition, which makes this check immune to false positives from string
 * literals.
 */
public class NoExternalFunctionsVisitor extends DefaultedVisitor<Boolean> {

    @Override
    public Boolean defaultVisit(ASTNode n) {
        for (ASTNode child : n.getChildrenNode()) {
            if (child != null && Boolean.TRUE.equals(child.accept(this))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Boolean visit(FunctionDefNode n) {
        return n.isExternal() || defaultVisit(n);
    }
}
