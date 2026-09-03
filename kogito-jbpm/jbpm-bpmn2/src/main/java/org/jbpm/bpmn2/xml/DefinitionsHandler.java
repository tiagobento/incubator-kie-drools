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
package org.jbpm.bpmn2.xml;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import org.jbpm.bpmn2.core.Definitions;
import org.jbpm.bpmn2.core.Interface;
import org.jbpm.bpmn2.core.Interface.Operation;
import org.jbpm.bpmn2.core.ItemDefinition;
import org.jbpm.compiler.xml.Handler;
import org.jbpm.compiler.xml.Parser;
import org.jbpm.compiler.xml.ProcessBuildData;
import org.jbpm.compiler.xml.core.BaseAbstractHandler;
import org.jbpm.process.core.ContextContainer;
import org.jbpm.process.core.context.variable.Variable;
import org.jbpm.process.core.context.variable.VariableScope;
import org.jbpm.process.core.datatype.DataType;
import org.jbpm.process.core.datatype.DataTypeResolver;
import org.jbpm.process.core.datatype.impl.type.UndefinedDataType;
import org.jbpm.process.expression.ExpressionLanguage;
import org.jbpm.process.expression.ExpressionLanguages;
import org.jbpm.ruleflow.core.RuleFlowProcess;
import org.jbpm.util.JbpmClassLoaderUtil;
import org.jbpm.workflow.core.NodeContainer;
import org.jbpm.workflow.core.node.ForEachNode;
import org.jbpm.workflow.core.node.WorkItemNode;
import org.kie.api.definition.process.Node;
import org.kie.api.definition.process.Process;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.Attributes2;

public class DefinitionsHandler extends BaseAbstractHandler implements Handler {

    /** Key under which the document expression language is published to the node handlers while parsing. */
    public static final String EXPRESSION_LANGUAGE = "ExpressionLanguage";

    @SuppressWarnings("unchecked")
    public DefinitionsHandler() {
        if ((this.validParents == null) && (this.validPeers == null)) {
            this.validParents = new HashSet();
            this.validParents.add(null);

            this.validPeers = new HashSet();
            this.validPeers.add(null);

            this.allowNesting = false;
        }
    }

    @Override
    public Object start(final String uri, final String localName,
            final Attributes attrs, final Parser parser)
            throws SAXException {
        parser.startElementBuilder(localName, attrs);
        // read before the children are parsed: node handlers need to know the document language while they read
        ((ProcessBuildData) parser.getData()).setMetaData(EXPRESSION_LANGUAGE, readExpressionLanguage(parser, declaredExpressionLanguage(attrs)));
        return new Definitions();
    }

    /** What the BPMN schema fills in for a document that declares no <code>expressionLanguage</code>. */
    static final String SCHEMA_DEFAULT_EXPRESSION_LANGUAGE = "http://www.w3.org/1999/XPath";

    /**
     * The <code>expressionLanguage</code> the document wrote, or <code>null</code> when it wrote none.
     *
     * The BPMN 2.0 schema declares a default for the attribute, XPath, and a validating parser reports that default
     * as if the document had written it. Here a document that says nothing has always meant the engine's default,
     * so the schema's default is not taken as a choice: an attribute the parser can tell was filled in is ignored,
     * and, for a parser that cannot tell, so is the schema's value itself.
     */
    private static String declaredExpressionLanguage(Attributes attrs) {
        String declared = attrs.getValue("expressionLanguage");
        if (declared == null) {
            return null;
        }
        if (attrs instanceof Attributes2) {
            int index = attrs.getIndex("expressionLanguage");
            return index >= 0 && ((Attributes2) attrs).isSpecified(index) ? declared : null;
        }
        return SCHEMA_DEFAULT_EXPRESSION_LANGUAGE.equals(declared) ? null : declared;
    }

    /**
     * The id of the expression language this document declared, or <code>null</code> when it declared none.
     *
     * Published to the node handlers while parsing, since a field that declares no language of its own follows it.
     */
    public static String documentExpressionLanguage(Parser parser) {
        return (String) ((ProcessBuildData) parser.getData()).getMetaData(EXPRESSION_LANGUAGE);
    }

    /**
     * The language a field with no language of its own is in: the document's, else the fallback the field has always
     * had.
     *
     * A document that declares the engine's own default declares nothing new, and is read exactly like one that
     * declares nothing: its script tasks and ad-hoc conditions keep their own default, Java, as they always have in
     * the many documents that say MVEL at the top and write their scripts in Java.
     */
    public static String documentLanguageOr(Parser parser, String fallback) {
        String declared = documentExpressionLanguage(parser);
        return declared == null || declared.equalsIgnoreCase(ExpressionLanguages.DEFAULT) ? fallback : declared;
    }

    /**
     * The id of the language a <code>language</code>, <code>scriptFormat</code> or <code>expressionLanguage</code>
     * attribute names, or a parse failure naming the attribute value and the languages that are available. A
     * language is available when the module providing it is on the classpath the document is parsed against, which
     * is the application's: the parser is handed that class loader, and the thread's own may be a build tool's.
     */
    public static String languageId(Parser parser, String declared) {
        ClassLoader classLoader = classLoader(parser);
        return ExpressionLanguages.find(declared, classLoader)
                .map(ExpressionLanguage::id)
                .orElseThrow(() -> new ProcessParsingValidationException(String.format(
                        "Unknown expression language '%s'; %s. A language is made available by adding the module that provides it to the application.",
                        declared, ExpressionLanguages.describeAvailable(classLoader))));
    }

    /**
     * The language with the given id, resolved against the class loader the document is parsed against.
     */
    public static ExpressionLanguage language(Parser parser, String id) {
        return ExpressionLanguages.require(id, classLoader(parser));
    }

    private static ClassLoader classLoader(Parser parser) {
        return parser.getClassLoader() != null ? parser.getClassLoader() : JbpmClassLoaderUtil.findClassLoader();
    }

    /**
     * The document-wide expression language, as BPMN 2.0 defines it on <code>&lt;definitions&gt;</code>.
     */
    private static String readExpressionLanguage(Parser parser, String language) {
        if (language == null || language.isBlank()) {
            return null;
        }
        return languageId(parser, language);
    }

    @Override
    public Object end(final String uri, final String localName,
            final Parser parser) throws SAXException {
        final Element element = parser.endElementBuilder();
        Definitions definitions = (Definitions) parser.getCurrent();
        String namespace = element.getAttribute("targetNamespace");
        List<Process> processes = ((ProcessBuildData) parser.getData()).getProcesses();
        Map<String, ItemDefinition> itemDefinitions = (Map<String, ItemDefinition>) ((ProcessBuildData) parser.getData()).getMetaData("ItemDefinitions");

        List<Interface> interfaces = (List<Interface>) ((ProcessBuildData) parser.getData()).getMetaData("Interfaces");

        String expressionLanguage = documentExpressionLanguage(parser);
        for (Process process : processes) {
            RuleFlowProcess ruleFlowProcess = (RuleFlowProcess) process;
            ruleFlowProcess.setMetaData("TargetNamespace", namespace);
            ruleFlowProcess.setExpressionLanguage(expressionLanguage);
            postProcessItemDefinitions(ruleFlowProcess, itemDefinitions, parser.getClassLoader());
            postProcessInterfaces(ruleFlowProcess, interfaces);
        }
        definitions.setTargetNamespace(namespace);
        return definitions;
    }

    @Override
    public Class<?> generateNodeFor() {
        return Definitions.class;
    }

    private void postProcessInterfaces(NodeContainer nodeContainer, List<Interface> interfaces) {

        for (Node node : nodeContainer.getNodes()) {
            if (node instanceof NodeContainer) {
                postProcessInterfaces((NodeContainer) node, interfaces);
            }
            if (node instanceof WorkItemNode && "Service Task".equals(((WorkItemNode) node).getMetaData("Type"))) {
                WorkItemNode workItemNode = (WorkItemNode) node;
                if (interfaces == null) {
                    throw new ProcessParsingValidationException("No interfaces found");
                }
                String operationRef = (String) workItemNode.getMetaData("OperationRef");
                String implementation = (String) workItemNode.getMetaData("Implementation");
                Operation operation = null;
                for (Interface i : interfaces) {
                    operation = i.getOperation(operationRef);
                    if (operation != null) {
                        break;
                    }
                }
                if (operation == null) {
                    throw new ProcessParsingValidationException("Could not find operation " + operationRef);
                }
                // avoid overriding parameters set by data input associations
                if (workItemNode.getWork().getParameter("Interface") == null) {
                    workItemNode.getWork().setParameter("Interface", operation.getInterface().getName());
                }
                if (workItemNode.getWork().getParameter("Operation") == null) {
                    workItemNode.getWork().setParameter("Operation", operation.getName());
                }
                if (workItemNode.getWork().getParameter("ParameterType") == null && operation.getMessage() != null) {
                    workItemNode.getWork().setParameter("ParameterType", operation.getMessage().getType());
                }
                // parameters to support web service invocation 
                if (implementation != null) {
                    workItemNode.getWork().setParameter("interfaceImplementationRef", operation.getInterface().getImplementationRef());
                    workItemNode.getWork().setParameter("operationImplementationRef", operation.getImplementationRef());
                    workItemNode.getWork().setParameter("implementation", implementation);
                }
            }
        }
    }

    private void postProcessItemDefinitions(NodeContainer nodeContainer, Map<String, ItemDefinition> itemDefinitions, ClassLoader cl) {
        if (nodeContainer instanceof ContextContainer) {
            setVariablesDataType((ContextContainer) nodeContainer, itemDefinitions, cl);
        }
        // process composite context node of for each to enhance its variables with types
        if (nodeContainer instanceof ForEachNode) {
            setVariablesDataType(((ForEachNode) nodeContainer).getCompositeNode(), itemDefinitions, cl);
        }
        for (Node node : nodeContainer.getNodes()) {
            if (node instanceof NodeContainer) {
                postProcessItemDefinitions((NodeContainer) node, itemDefinitions, cl);
            }
            if (node instanceof ContextContainer) {
                setVariablesDataType((ContextContainer) node, itemDefinitions, cl);
            }
        }
    }

    private void setVariablesDataType(ContextContainer container, Map<String, ItemDefinition> itemDefinitions, ClassLoader cl) {
        VariableScope variableScope = (VariableScope) container.getDefaultContext(VariableScope.VARIABLE_SCOPE);
        if (variableScope != null) {
            for (Variable variable : variableScope.getVariables()) {
                setVariableDataType(variable, itemDefinitions, cl);
            }
        }
    }

    private void setVariableDataType(Variable variable, Map<String, ItemDefinition> itemDefinitions, ClassLoader cl) {
        // retrieve type from item definition

        String itemSubjectRef = (String) variable.getMetaData("ItemSubjectRef");
        Object defaultValue = variable.getMetaData("defaultValue");
        if (UndefinedDataType.getInstance().equals(variable.getType()) && itemDefinitions != null && itemSubjectRef != null) {
            DataType dataType = DataTypeResolver.defaultDataType;
            ItemDefinition itemDefinition = itemDefinitions.get(itemSubjectRef);
            if (itemDefinition != null) {
                dataType = DataTypeResolver.fromType(itemDefinition.getStructureRef(), cl);
            }
            variable.setType(dataType);
            variable.setValue(dataType.verifyDataType(defaultValue) ? defaultValue : dataType.readValue((String) defaultValue));
        }
    }

}
