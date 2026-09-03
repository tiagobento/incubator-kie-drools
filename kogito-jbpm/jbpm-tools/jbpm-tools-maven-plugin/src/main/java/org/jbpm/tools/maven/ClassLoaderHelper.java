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
package org.jbpm.tools.maven;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.maven.project.MavenProject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ClassLoaderHelper {
    private static Logger LOGGER = LoggerFactory.getLogger(ClassLoaderHelper.class);

    /**
     * A class loader over the project's classes and its dependencies - the expression languages a document may use
     * are among the latter - falling back to the plugin's own when the project's classpath cannot be resolved.
     *
     * @param test whether the test classpath is meant, for documents generated into the test sources
     */
    public static ClassLoader getClassLoader(MavenProject project, boolean test) {
        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        try {
            Set<URL> classPathUrls = new LinkedHashSet<>();

            List<String> classpathElements = new ArrayList<>(test ? project.getTestClasspathElements() : project.getCompileClasspathElements());
            // adding the projects classes itself
            classpathElements.add(project.getBuild().getOutputDirectory());
            classpathElements.add(project.getBuild().getTestOutputDirectory());
            for (final String classpathElement : classpathElements) {
                LOGGER.debug("adding classpath element {} to classloader", classpathElement);
                classPathUrls.add(new File(classpathElement).toURI().toURL());
            }

            return new URLClassLoader(classPathUrls.stream().toArray(URL[]::new), contextClassLoader);
        } catch (final Exception e) {
            LOGGER.warn("Could not resolve the project classpath, using the plugin's own: {}", e.getMessage());
            return contextClassLoader;
        }
    }
}
