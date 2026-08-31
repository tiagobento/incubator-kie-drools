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
package org.jbpm.bpmn2.feel;

import java.util.List;

import org.drools.compiler.rule.builder.PackageBuildContext;
import org.jbpm.process.builder.AssignmentBuilder;
import org.jbpm.workflow.core.impl.DataDefinition;
import org.jbpm.workflow.core.node.Assignment;

/**
 * A FEEL assignment needs nothing built.
 *
 * Unlike XPath, which compiles an assignment action here, FEEL changes only the leaf that evaluates a
 * <code>#{...}</code> placeholder: the action itself is the ordinary one, already chosen by
 * {@code DataAssociation.buildInterpretedAssignment} from the same heuristics every other assignment goes through.
 * This exists so the dialect can be resolved at build time without failing.
 */
public class FeelAssignmentBuilder implements AssignmentBuilder {

    @Override
    public void build(PackageBuildContext context, Assignment assignment, List<DataDefinition> sourceDefinitions, DataDefinition targetDefinition) {
        // nothing to compile
    }

}
