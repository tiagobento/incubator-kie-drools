<!--
  Licensed to the Apache Software Foundation (ASF) under one
  or more contributor license agreements.  See the NOTICE file
  distributed with this work for additional information
  regarding copyright ownership.  The ASF licenses this file
  to you under the Apache License, Version 2.0 (the
  "License"); you may not use this file except in compliance
  with the License.  You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

  Unless required by applicable law or agreed to in writing,
  software distributed under the License is distributed on an
  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
  KIND, either express or implied.  See the License for the
  specific language governing permissions and limitations
  under the License.
  -->

# Expression languages for BPMN

A BPMN document names the language its expressions are in, on `<definitions expressionLanguage>` for the whole
document and on `language`, `scriptFormat` or `expressionLanguage` for a single element. The engine knows no language
by name. Each one is an implementation of `org.jbpm.process.expression.ExpressionLanguage`, discovered through
`ServiceLoader`, and an application has exactly the languages whose modules are on its classpath.

| Module | Language | Identifiers a document may use |
| --- | --- | --- |
| `jbpm-expressions-mvel` | MVEL, the default when nothing declares a language | `mvel`, `http://www.mvel.org/2.0` |
| `jbpm-expressions-java` | Java, compiled into the application | `java`, `http://www.java.com/java` |
| `jbpm-expressions-feel` | FEEL, checked when the process is built | `FEEL`, `http://www.omg.org/spec/DMN/20180521/FEEL/`, `http://www.omg.org/spec/FEEL/20140401`, `application/feel` |
| `jbpm-expressions-xpath` | XPath, over XML variables | `XPath`, `http://www.w3.org/1999/XPath` |

## Choosing what the application carries

The process starters and extensions - `kogito-quarkus-processes`, `kogito-processes-spring-boot-starter`, and the
`jbpm-deps-group-*` aggregators behind them - bring all four, so an existing application keeps every language it had.
To leave one out, exclude its module where the starter is declared:

```xml
<dependency>
  <groupId>org.kie.kogito</groupId>
  <artifactId>kogito-quarkus-processes</artifactId>
  <exclusions>
    <exclusion>
      <groupId>org.kie.kogito</groupId>
      <artifactId>jbpm-expressions-mvel</artifactId>
    </exclusion>
    <exclusion>
      <groupId>org.kie.kogito</groupId>
      <artifactId>jbpm-expressions-java</artifactId>
    </exclusion>
  </exclusions>
</dependency>
```

What a language being absent means:

- A document that names it fails to parse, with a message listing the languages that are available.
- An expression that would fall back to it fails validation the same way. An element that declares no language is in
  the document's language; a document that declares none is in MVEL, and a script task or ad-hoc condition that
  declares none is in Java. A document that declares MVEL, the engine's own default, is read like one that declares
  nothing, so its script tasks stay Java. An application without those modules therefore has to declare
  `expressionLanguage` on its documents, or `scriptFormat` on its scripts.
- Nothing else changes. A language being on the classpath for another reason - `mvel2` arrives with Drools, for
  instance - does not make it a BPMN expression language; only its `jbpm-expressions-*` module does.

## Bringing your own

Implement `ExpressionLanguage` and register it under `META-INF/services/org.jbpm.process.expression.ExpressionLanguage`
in a module the application depends on. Put it in its own artifact rather than in the application module: code
generation runs against the application's dependencies before the application itself is compiled.

The interface has one method per place BPMN takes an expression, and a language declares which of those surfaces it
supports:

| Surface | Where | Runtime method |
| --- | --- | --- |
| `CONDITION` | sequence flow and gateway conditions, ad-hoc conditions, conditional start events, multi-instance completion conditions | `evaluator(expression, Boolean.class, root)` |
| `EXPRESSION` | data association transformations, correlation properties | `evaluator(expression, type, root)` |
| `SCRIPT` | script tasks, on-entry and on-exit scripts | `script(script)` |
| `INTERPOLATION` | every `#{…}` placeholder: node names, timers, signal and message names, called elements, actors, parameters, an assignment's `<from>` | `interpolate(body, scope)` |
| `ASSIGNMENT_TARGET` | a `#{…}` in an assignment's `<to>`, naming a place to write | `assign(target, value, scope)` |
| `ASSIGNMENT` | a whole `<assignment>` whose `<from>` and `<to>` are both in the language | `assignment(assignment, sources, target)` |

A document that uses the language on a surface it does not declare fails validation naming the surface. Every
runtime method has a default that refuses, so a language implements only what it declares.

Two build-time methods are optional. `validate` checks an expression while the process is built, against the
variables in scope, and reports each problem as a validation error. `compile` returns Java source for an expression
that should become part of the generated application, as Java does; a language that returns nothing is called at
runtime instead, through the generated code, and needs no code-generation logic of its own.

`org.jbpm.process.expression.PathWriter` writes a value into a dotted path such as `person.address.city` through
getters, setters and map keys, for a language that has no assignment of its own. FEEL uses it.

## In a native image

A language evaluated at runtime is found through `ServiceLoader`, which the Quarkus extension registers for native
images. The language's own runtime has to be usable in a native image as well: FEEL is, MVEL is not.
