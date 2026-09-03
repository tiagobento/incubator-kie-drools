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
package org.jbpm.process.expression.java;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.jbpm.process.expression.ValidationScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.resolution.MethodUsage;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedConstructorDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedFieldDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.declarations.ResolvedTypeParameterDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.MemoryTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import static java.lang.String.format;

/**
 * Checks a Java script while the process is built: it has to parse, and every name it uses has to be a variable in
 * scope, a global, a local it declares, a lambda parameter, or a type the process imports.
 */
final class JavaScriptValidator {

    private static final Logger LOGGER = LoggerFactory.getLogger(JavaScriptValidator.class);

    private JavaScriptValidator() {
    }

    static void validate(String script, ValidationScope scope, Consumer<String> problems) {
        String imports = scope.imports().stream().map(javaImport -> "import " + javaImport + ";").collect(Collectors.joining("\n"));
        String dummyScript =
                "import org.kie.kogito.internal.process.runtime.KogitoProcessContext;\n" +
                        "import org.jbpm.process.instance.impl.Action;\n" +
                        imports + "\n" +
                        " class Test {\n" +
                        "    Action action = kcontext -> {" + script + "\n};\n" +
                        "}";

        MemoryTypeSolver memoryTypeSolver = new MemoryTypeSolver();
        for (String fqn : scope.imports()) {
            int idx = fqn.lastIndexOf(".");
            String clazz = idx > 0 ? fqn.substring(idx + 1) : fqn;
            String pakage = idx > 0 ? fqn.substring(0, idx) : "";
            memoryTypeSolver.addDeclaration(clazz, new ImportedType(fqn, pakage, clazz));
        }

        ParseResult<CompilationUnit> parse = new JavaParser(new ParserConfiguration()
                .setSymbolResolver(new JavaSymbolSolver(new CombinedTypeSolver(new ReflectionTypeSolver(), memoryTypeSolver))))
                        .parse(dummyScript);
        if (!parse.isSuccessful()) {
            problems.accept(format("unable to parse Java content: %s", parse.getProblems()));
            return;
        }
        CompilationUnit unit = parse.getResult().orElseThrow();
        try {
            Set<String> knownVariables = new HashSet<>();
            knownVariables.add(JavaExpressionLanguage.KCONTEXT);
            knownVariables.addAll(scope.variableNames());
            knownVariables.addAll(scope.globals());
            // add local variables
            unit.findAll(VariableDeclarationExpr.class).stream().flatMap(v -> v.getVariables().stream()).map(VariableDeclarator::getNameAsString).forEach(knownVariables::add);
            // add support for lambda expressions
            unit.findAll(LambdaExpr.class).forEach(le -> le.getParameters().forEach(p -> knownVariables.add(p.getNameAsString())));
            resolveVariablesType(unit, knownVariables);
        } catch (UnsolvedSymbolException ex) {
            if (LOGGER.isErrorEnabled()) {
                VoidVisitor<Void> printer = new DefaultPrettyPrinterVisitor(new DefaultPrinterConfiguration());
                unit.findFirst(BlockStmt.class).ifPresent(block -> {
                    block.accept(printer, null);
                    LOGGER.error(printer.toString());
                });
            }
            // the name comes as "Solving x" where x is the variable name
            final String[] solving = ex.getName().split(" ");
            problems.accept(format("uses unknown variable in the script: %s", solving.length == 2 ? solving[1] : solving[0]));
        }
    }

    private static void resolveVariablesType(CompilationUnit unit, Set<String> knownVariables) {
        filterAndResolve(unit.findAll(MethodCallExpr.class).stream()
                .map(MethodCallExpr::getScope)
                .flatMap(Optional::stream), knownVariables);
        filterAndResolve(unit.findAll(AssignExpr.class).stream().map(AssignExpr::getTarget), knownVariables);
        resolveVariablesTypes(unit, knownVariables);
    }

    private static void resolveVariablesTypes(com.github.javaparser.ast.Node node, Set<String> knownVariables) {
        node.findAll(MethodCallExpr.class).stream()
                .flatMap(m -> m.getArguments().stream())
                .forEach(arg -> {
                    if (arg.isMethodCallExpr() || arg.isBinaryExpr()) {
                        resolveVariablesTypes(arg, knownVariables);
                    } else {
                        arg.findAll(NameExpr.class).stream().filter(ex -> !knownVariables.contains(ex.getNameAsString())).forEach(ex -> ex.calculateResolvedType());
                    }
                });
        node.findAll(BinaryExpr.class).stream()
                .map(BinaryExpr::asBinaryExpr)
                .forEach(bex -> {
                    processExpr(bex.getLeft(), knownVariables);
                    processExpr(bex.getRight(), knownVariables);
                });
    }

    private static void filterAndResolve(Stream<Expression> expressions, Set<String> knownVariables) {
        expressions.filter(expression -> expression.isNameExpr() && !knownVariables.contains(expression.asNameExpr().getNameAsString())).forEach(Expression::calculateResolvedType);
    }

    private static void processExpr(Expression expression, Set<String> knownVariables) {
        if (expression.isNameExpr()) {
            if (!knownVariables.contains(expression.asNameExpr().getNameAsString())) {
                expression.calculateResolvedType();
            }
        } else {
            resolveVariablesTypes(expression, knownVariables);
        }
    }

    /**
     * A type the process imports, known by name only: the symbol solver accepts it without loading it.
     */
    private static final class ImportedType implements ResolvedReferenceTypeDeclaration {

        private final String fqn;
        private final String pakage;
        private final String clazz;

        private ImportedType(String fqn, String pakage, String clazz) {
            this.fqn = fqn;
            this.pakage = pakage;
            this.clazz = clazz;
        }

        @Override
        public Optional<ResolvedReferenceTypeDeclaration> containerType() {
            return Optional.empty();
        }

        @Override
        public String getPackageName() {
            return pakage;
        }

        @Override
        public String getClassName() {
            return clazz;
        }

        @Override
        public String getQualifiedName() {
            return fqn;
        }

        @Override
        public String getName() {
            return clazz;
        }

        @Override
        public List<ResolvedTypeParameterDeclaration> getTypeParameters() {
            return Collections.emptyList();
        }

        @Override
        public List<ResolvedReferenceType> getAncestors(boolean acceptIncompleteList) {
            return Collections.emptyList();
        }

        @Override
        public List<ResolvedFieldDeclaration> getAllFields() {
            return Collections.emptyList();
        }

        @Override
        public Set<ResolvedMethodDeclaration> getDeclaredMethods() {
            return Collections.emptySet();
        }

        @Override
        public Set<MethodUsage> getAllMethods() {
            return Collections.emptySet();
        }

        @Override
        public boolean isAssignableBy(ResolvedType type) {
            return false;
        }

        @Override
        public boolean isAssignableBy(ResolvedReferenceTypeDeclaration other) {
            return false;
        }

        @Override
        public boolean hasDirectlyAnnotation(String qualifiedName) {
            return false;
        }

        @Override
        public boolean isFunctionalInterface() {
            return false;
        }

        @Override
        public List<ResolvedConstructorDeclaration> getConstructors() {
            return Collections.emptyList();
        }
    }
}
