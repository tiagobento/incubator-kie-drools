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
package org.jbpm.test.util;

import java.util.EnumSet;
import java.util.Set;

import org.jbpm.process.expression.ExpressionLanguage;

/**
 * The Java language as the tests of this module need it: known to the validator, and nothing more.
 *
 * The processes built here carry their scripts as Java lambdas already, so no Java ever has to be compiled or run;
 * but a process that declares a script in <code>java</code> is only valid when a language of that name is
 * available, and the module providing the real one depends on this one.
 */
public class TestJavaLanguage implements ExpressionLanguage {

    @Override
    public String id() {
        return "java";
    }

    @Override
    public String uri() {
        return "http://www.java.com/java";
    }

    @Override
    public Set<Surface> surfaces() {
        return EnumSet.of(Surface.CONDITION, Surface.EXPRESSION, Surface.SCRIPT);
    }
}
