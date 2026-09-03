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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.ServiceLoader;
import java.util.WeakHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import org.jbpm.util.JbpmClassLoaderUtil;
import org.jbpm.workflow.core.WorkflowProcess;
import org.kie.api.definition.process.Process;
import org.kie.kogito.internal.process.runtime.KogitoProcessContext;

/**
 * The expression languages available to this application: every {@link ExpressionLanguage} on the classpath,
 * discovered through {@link ServiceLoader}, plus any registered programmatically.
 *
 * <p>
 * A language is looked up by any of its {@link ExpressionLanguage#identifiers() identifiers}, case-insensitively.
 * Languages are discovered per class loader, since code generation runs against the application's class loader
 * rather than its own.
 */
public final class ExpressionLanguages {

    /**
     * The language an expression is in when neither it nor its document declares one.
     */
    public static final String DEFAULT = "mvel";

    private static final Map<ClassLoader, Map<String, ExpressionLanguage>> DISCOVERED = Collections.synchronizedMap(new WeakHashMap<>());
    private static final List<ExpressionLanguage> REGISTERED = new CopyOnWriteArrayList<>();

    private ExpressionLanguages() {
    }

    public static Optional<ExpressionLanguage> find(String identifier) {
        return find(identifier, JbpmClassLoaderUtil.findClassLoader());
    }

    public static Optional<ExpressionLanguage> find(String identifier, ClassLoader classLoader) {
        if (identifier == null || identifier.isBlank()) {
            return Optional.empty();
        }
        String key = normalize(identifier);
        for (ExpressionLanguage registered : REGISTERED) {
            if (answersTo(registered, key)) {
                return Optional.of(registered);
            }
        }
        return Optional.ofNullable(discovered(classLoader).get(key));
    }

    /**
     * The language answering to an identifier, or an exception naming the identifier and the languages that are
     * available, so a missing module is diagnosed at the point of use.
     */
    public static ExpressionLanguage require(String identifier) {
        return require(identifier, JbpmClassLoaderUtil.findClassLoader());
    }

    public static ExpressionLanguage require(String identifier, ClassLoader classLoader) {
        return find(identifier, classLoader).orElseThrow(() -> new IllegalArgumentException(missing(identifier, classLoader)));
    }

    /**
     * The canonical id of the language an identifier selects, if any.
     */
    public static Optional<String> idOf(String identifier) {
        return find(identifier).map(ExpressionLanguage::id);
    }

    public static Collection<ExpressionLanguage> all() {
        return all(JbpmClassLoaderUtil.findClassLoader());
    }

    public static Collection<ExpressionLanguage> all(ClassLoader classLoader) {
        Map<String, ExpressionLanguage> byId = new LinkedHashMap<>();
        REGISTERED.forEach(language -> byId.putIfAbsent(normalize(language.id()), language));
        discovered(classLoader).values().forEach(language -> byId.putIfAbsent(normalize(language.id()), language));
        return List.copyOf(byId.values());
    }

    /**
     * Makes a language available without a service registration. It takes precedence over a discovered one
     * answering to the same identifiers.
     */
    public static void register(ExpressionLanguage language) {
        REGISTERED.add(0, language);
    }

    public static void unregister(ExpressionLanguage language) {
        REGISTERED.remove(language);
    }

    /**
     * The language a process selected on <code>&lt;definitions expressionLanguage&gt;</code>, or the
     * {@link #DEFAULT} when it declared none.
     */
    public static String languageOf(Process process) {
        String declared = process instanceof WorkflowProcess ? ((WorkflowProcess) process).getExpressionLanguage() : null;
        return declared == null || declared.isBlank() ? DEFAULT : declared;
    }

    /**
     * Resolves the language of an expression at runtime: the one it declared, else the one its document declared,
     * else the {@link #DEFAULT}.
     */
    public static ExpressionLanguage of(String declared, KogitoProcessContext context) {
        if (declared != null && !declared.isBlank()) {
            return require(declared);
        }
        Process process = context == null || context.getProcessInstance() == null ? null : context.getProcessInstance().getProcess();
        return require(process == null ? DEFAULT : languageOf(process));
    }

    /**
     * A description of what is available, for messages.
     */
    public static String describeAvailable(ClassLoader classLoader) {
        Collection<ExpressionLanguage> available = all(classLoader);
        if (available.isEmpty()) {
            return "no expression language is available";
        }
        return "available: " + available.stream()
                .map(language -> language.id() + " (" + language.uri() + ")")
                .collect(Collectors.joining(", "));
    }

    private static String missing(String identifier, ClassLoader classLoader) {
        return String.format("No expression language answers to '%s'; %s. A language becomes available when the module providing it "
                + "is on the classpath - org.kie.kogito:jbpm-expressions-mvel, jbpm-expressions-feel, jbpm-expressions-java "
                + "and jbpm-expressions-xpath ship with Apache KIE, and any module may provide its own by registering an "
                + "org.jbpm.process.expression.ExpressionLanguage service.", identifier, describeAvailable(classLoader));
    }

    private static Map<String, ExpressionLanguage> discovered(ClassLoader classLoader) {
        ClassLoader loader = classLoader == null ? JbpmClassLoaderUtil.findClassLoader() : classLoader;
        return DISCOVERED.computeIfAbsent(loader, ExpressionLanguages::load);
    }

    private static Map<String, ExpressionLanguage> load(ClassLoader classLoader) {
        Map<String, ExpressionLanguage> byIdentifier = new LinkedHashMap<>();
        List<ExpressionLanguage> languages = new ArrayList<>();
        ServiceLoader.load(ExpressionLanguage.class, classLoader).forEach(languages::add);
        for (ExpressionLanguage language : languages) {
            for (String identifier : language.identifiers()) {
                byIdentifier.putIfAbsent(normalize(identifier), language);
            }
        }
        return Collections.unmodifiableMap(byIdentifier);
    }

    private static boolean answersTo(ExpressionLanguage language, String key) {
        return language.identifiers().stream().map(ExpressionLanguages::normalize).anyMatch(key::equals);
    }

    private static String normalize(String identifier) {
        return identifier.trim().toLowerCase(Locale.ROOT);
    }
}
