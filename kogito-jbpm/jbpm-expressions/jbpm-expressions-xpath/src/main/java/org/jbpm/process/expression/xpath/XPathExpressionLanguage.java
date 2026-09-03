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
package org.jbpm.process.expression.xpath;

import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.instance.impl.AssignmentAction;
import org.jbpm.process.instance.impl.ReturnValueEvaluator;
import org.jbpm.process.instance.impl.XPATHReturnValueEvaluator;
import org.jbpm.workflow.core.impl.DataDefinition;
import org.jbpm.workflow.core.impl.XPATHAssignmentAction;
import org.jbpm.workflow.core.node.Assignment;

/**
 * XPath over XML variables: conditions, and assignments that copy a fragment of one document into another.
 */
public class XPathExpressionLanguage implements ExpressionLanguage {

    public static final String ID = "XPath";
    public static final String URI = "http://www.w3.org/1999/XPath";

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String uri() {
        return URI;
    }

    @Override
    public Collection<String> identifiers() {
        return List.of(ID, URI);
    }

    @Override
    public Set<Surface> surfaces() {
        return EnumSet.of(Surface.CONDITION, Surface.ASSIGNMENT);
    }

    @Override
    public ReturnValueEvaluator evaluator(String expression, Class<?> type, String root) {
        return new XPATHReturnValueEvaluator(expression);
    }

    @Override
    public Optional<AssignmentAction> assignment(Assignment assignment, List<DataDefinition> sources, DataDefinition target) {
        return Optional.of(new XPATHAssignmentAction(assignment, sources, target));
    }
}
