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
package org.jbpm.workflow.core.impl;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jbpm.util.PatternConstants;

/**
 * An assignment whose <code>&lt;to&gt;</code> is a FEEL path.
 *
 * FEEL has no assignment, but a target names a place rather than computing a value, so that does not matter - and a
 * plain path is spelled the same in FEEL as in MVEL. Writing one is therefore not language-specific, and
 * {@link OutputExpressionAssignment} already does it. All FEEL adds is the insistence that the target really is a
 * place: <code>#{person.name}</code> is one, <code>#{upper case(person.name)}</code> is a value with nowhere to write.
 */
public class FeelOutputExpressionAssignment extends OutputExpressionAssignment {

    /** A reference: a name, or names separated by dots. */
    private static final Pattern REFERENCE = Pattern.compile("\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*(\\.\\s*[A-Za-z_$][A-Za-z0-9_$]*\\s*)*");

    public FeelOutputExpressionAssignment(DataDefinition from, DataDefinition to) {
        super(from, requireReference(to));
    }

    private static DataDefinition requireReference(DataDefinition to) {
        Matcher matcher = PatternConstants.PARAMETER_MATCHER.matcher(to.getExpression());
        String body = matcher.find() ? matcher.group(1) : to.getExpression();
        if (!REFERENCE.matcher(body).matches()) {
            throw new IllegalArgumentException(String.format(
                    "'%s' cannot be an assignment target in FEEL. A target has to be a variable, or a property of one, "
                            + "such as #{person.name} - not an expression computing a value.",
                    body.trim()));
        }
        return to;
    }
}
