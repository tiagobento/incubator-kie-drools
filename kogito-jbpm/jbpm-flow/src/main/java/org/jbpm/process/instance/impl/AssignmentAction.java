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
package org.jbpm.process.instance.impl;

import java.util.function.Function;

import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

public interface AssignmentAction {

    void execute(Function<String, Object> sourceResolver, Function<String, Object> targetResolver, AssignmentProducer producer) throws Exception;

    /**
     * Same, with the process context of the node the assignment belongs to.
     *
     * Only assignments that evaluate an expression in a language needing the whole variable scope, rather than a
     * resolver function, care about the context; everything else keeps the plain form.
     */
    default void execute(KogitoProcessContext context, Function<String, Object> sourceResolver, Function<String, Object> targetResolver, AssignmentProducer producer)
            throws Exception {
        execute(sourceResolver, targetResolver, producer);
    }

}
