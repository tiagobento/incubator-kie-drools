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
package org.jbpm.process.expression;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import org.jbpm.process.core.context.variable.Variable;

/**
 * What an expression can see, for checking it while the process is built: the variables in scope with their
 * declared types, the process globals and imports, and the class loader the application is built against.
 */
public final class ValidationScope {

    private final Collection<Variable> variables;
    private final Collection<String> globals;
    private final Collection<String> imports;
    private final ClassLoader classLoader;

    public ValidationScope(Collection<Variable> variables, Collection<String> globals, Collection<String> imports, ClassLoader classLoader) {
        this.variables = List.copyOf(variables);
        this.globals = List.copyOf(globals);
        this.imports = List.copyOf(imports);
        this.classLoader = classLoader;
    }

    public Collection<Variable> variables() {
        return variables;
    }

    public Collection<String> variableNames() {
        return variables.stream().map(Variable::getName).collect(Collectors.toList());
    }

    public Collection<String> globals() {
        return globals;
    }

    public Collection<String> imports() {
        return imports;
    }

    public ClassLoader classLoader() {
        return classLoader;
    }
}
