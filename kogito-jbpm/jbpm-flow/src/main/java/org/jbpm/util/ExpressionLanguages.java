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
package org.jbpm.util;

/**
 * The expression languages BPMN recognises on <code>&lt;definitions expressionLanguage&gt;</code>, on a per-field
 * <code>language</code>/<code>scriptFormat</code>, and on a per-assignment <code>expressionLanguage</code>.
 *
 * MVEL is the default and stays the default: a document that says nothing, or that names a language we do not know,
 * behaves exactly as it always has.
 */
public final class ExpressionLanguages {

    public static final String FEEL = "FEEL";
    public static final String MVEL = "mvel";

    public static final String JAVA_LANGUAGE = "http://www.java.com/java";
    public static final String MVEL_LANGUAGE = "http://www.mvel.org/2.0";
    public static final String RULE_LANGUAGE = "http://www.jboss.org/drools/rule";
    public static final String XPATH_LANGUAGE = "http://www.w3.org/1999/XPath";
    public static final String FEEL_LANGUAGE = "http://www.omg.org/spec/FEEL/20140401";
    public static final String DMN_FEEL_LANGUAGE = "http://www.omg.org/spec/DMN/20180521/FEEL/";
    public static final String FEEL_LANGUAGE_SHORT = "application/feel";

    private ExpressionLanguages() {
    }

    /**
     * Whether the given language selects FEEL. Matches the convention the dialect builders already use, so the FEEL
     * URIs, the short form and the bare dialect name all work.
     */
    public static boolean isFeel(String language) {
        return language != null && language.toLowerCase().contains("feel");
    }

    public static boolean isMvel(String language) {
        return language != null && (language.isBlank() || language.toLowerCase().contains("mvel"));
    }

    /**
     * Whether the given document-level language is one we act on. Anything else is reported and treated as MVEL.
     */
    public static boolean isKnownDocumentLanguage(String language) {
        return isFeel(language) || isMvel(language);
    }
}
