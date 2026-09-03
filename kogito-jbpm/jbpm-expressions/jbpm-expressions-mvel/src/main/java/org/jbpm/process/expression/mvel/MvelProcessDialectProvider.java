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

import org.jbpm.process.builder.dialect.ProcessDialect;
import org.jbpm.process.builder.dialect.ProcessDialectProvider;
import org.jbpm.process.builder.dialect.mvel.MVELProcessDialect;

/**
 * MVEL on the in-memory build path, where expressions are compiled through Drools rather than interpreted.
 */
public class MvelProcessDialectProvider implements ProcessDialectProvider {

    @Override
    public String name() {
        return MvelExpressionLanguage.ID;
    }

    @Override
    public ProcessDialect dialect() {
        return new MVELProcessDialect();
    }
}
