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
package org.jbpm.process.builder.dialect;

import java.util.Locale;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.jbpm.process.expression.ExpressionLanguages;
import org.jbpm.util.JbpmClassLoaderUtil;

/**
 * The dialects of the in-memory build path, where a process is built from its model rather than generated as code.
 *
 * <p>
 * A language that compiles ahead of time on this path - Java through the Drools compiler, MVEL likewise -
 * registers a {@link ProcessDialectProvider}. Every other {@link org.jbpm.process.expression.ExpressionLanguage}
 * is served as is: its expressions are handed to it when the process runs.
 */
public class ProcessDialectRegistry {

    private ProcessDialectRegistry() {

    }

    private static final ConcurrentMap<String, ProcessDialect> dialects = new ConcurrentHashMap<>();

    static {
        ServiceLoader.load(ProcessDialectProvider.class, JbpmClassLoaderUtil.findClassLoader())
                .forEach(provider -> dialects.put(key(provider.name()), provider.dialect()));
    }

    /**
     * The dialect for a language, or an exception naming the language and the ones available.
     */
    public static ProcessDialect getDialect(String dialect) {
        return find(dialect).orElseThrow(() -> new IllegalArgumentException(
                String.format("No expression language answers to '%s'; %s.", dialect, ExpressionLanguages.describeAvailable(JbpmClassLoaderUtil.findClassLoader()))));
    }

    public static Optional<ProcessDialect> find(String dialect) {
        if (dialect == null) {
            return Optional.empty();
        }
        ProcessDialect explicit = dialects.get(key(dialect));
        if (explicit != null) {
            return Optional.of(explicit);
        }
        return ExpressionLanguages.find(dialect)
                .map(language -> dialects.computeIfAbsent(key(language.id()), id -> new ExpressionLanguageProcessDialect(language)));
    }

    public static void setDialect(String dialectName, ProcessDialect dialect) {
        dialects.put(key(dialectName), dialect);
    }

    private static String key(String dialect) {
        return dialect.trim().toLowerCase(Locale.ROOT);
    }

}
