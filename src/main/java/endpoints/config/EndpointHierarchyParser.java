package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.DocumentGenerator.StyleVisionXslt;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.datasource.DataSourceCommand;
import endpoints.task.Task;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Collectors;

import static com.offerready.xslt.WeaklyCachedXsltTransformer.getTransformerOrScheduleCompilation;
import static java.util.stream.Collectors.toList;

public class EndpointHierarchyParser extends DomParser {
    
    public static Map<ParameterName, String> parseParameterMap(Element container, String elementName, String keyAttribute)
        throws ConfigurationException {
        Map<ParameterName, String> result = new HashMap<>();
        for (Element e : getSubElements(container, elementName))
            result.put(new ParameterName(getMandatoryAttribute(e, keyAttribute)), e.getTextContent());
        return result;
    }

    /** @return map with one entry (similar to a "pair", but more convenient) */
    protected static @Nonnull Map<ParameterName, Parameter> parseParameter(@Nonnull Element element)
    throws ConfigurationException {
        assertNoOtherElements(element); // element is empty

        var p = new Parameter();
        p.defaultValueOrNull = getOptionalAttribute(element, "default-value");

        var result = new HashMap<ParameterName, Parameter>();
        result.put(new ParameterName(getMandatoryAttribute(element, "name")), p);
        return result;
    }
    
    protected static void setEndpointHierarchyNodeAttributes(
        @Nonnull EndpointHierarchyNode node, @CheckForNull EndpointHierarchyFolderNode parentOrNull, @Nonnull Element element
    ) throws ConfigurationException {
        node.parentOrNull = parentOrNull;
        node.parameterMultipleValueSeparatorOverride = getOptionalAttribute(element, "multiple-value-separator");
        for (var p : getSubElements(element, "parameter")) node.parameters.putAll(parseParameter(p));
    }
    
    protected static @Nonnull ResponseConfiguration parseResponseConfiguration(
        @Nonnull Map<String, Transformer> transformers, @Nonnull Set<ParameterName> params, @CheckForNull Element element
    ) throws ConfigurationException {
        if (element == null) return new EmptyResponseConfiguration();

        var responseTransformationElement = getOptionalSingleSubElement(element, "response-transformation");
        var redirectToElement = getOptionalSingleSubElement(element, "redirect-to");
        var forwardToEndpointElement = getOptionalSingleSubElement(element, "forward-to-endpoint");

        final ResponseConfiguration result;
        
        if (responseTransformationElement != null)
            result = new TransformationResponseConfiguration(transformers, element, responseTransformationElement);
        else if (redirectToElement != null)
            result = new RedirectResponseConfiguration(element, redirectToElement);
        else if (forwardToEndpointElement != null)
            result = new ForwardToEndpointResponseConfiguration(element, forwardToEndpointElement);
        else
            result = new EmptyResponseConfiguration(element);
        
        result.assertParametersSuffice(params);
        
        return result;
    }

    @SneakyThrows({SecurityException.class, ClassNotFoundException.class, NoSuchMethodException.class,
        IllegalAccessException.class, InstantiationException.class, InvocationTargetException.class})
    protected static @Nonnull Task parseTask(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory,
        @Nonnull Map<String, Transformer> transformers, @Nonnull File staticDir,
        @Nonnull Set<ParameterName> params, @Nonnull Element element
    ) throws ConfigurationException {
        var taskClassName = getMandatoryAttribute(element, "class");
        try {
            try {
                @SuppressWarnings("unchecked") var taskClass = (Class<? extends Task>) Class.forName(taskClassName);
                var constructor = taskClass.getConstructor(XsltCompilationThreads.class, File.class, Map.class, File.class, Element.class);
                var result = constructor.newInstance(threads, httpXsltDirectory, transformers, staticDir, element);
                result.assertParametersSuffice(params);
                
                if ( ! result.getOutputIntermediateValues().isEmpty() && result.condition.isOptional())
                    throw new ConfigurationException("Task cannot be optional (if=x equals=x etc.), and also output " +
                        "intermediate values. Another task might need those intermediate values, and it can't use them " +
                        "if they have not been created because the task didn't execute because it's optional condition " +
                        "was not satisfied.");
                
                for (var output : result.getOutputIntermediateValues())
                    if (params.contains(new ParameterName(output.name)))
                        throw new ConfigurationException("Task produces <output-intermediate-value name='" + output.name + "'> " +
                            "but there is already a <parameter name='" + output.name + "'> defined. " +
                            "Outputs and parameters may not have the same name, " +
                            "otherwise variable syntax '${" + output.name + "}' would be ambiguous.");
                
                return result;
            }
            catch (InvocationTargetException e) {
                if (e.getCause() instanceof ConfigurationException) throw (ConfigurationException) e.getCause();
                else throw e;
            }
        }
        catch (ConfigurationException e) {
            throw new ConfigurationException("<task class='"+taskClassName+"'>", e);
        }
    }

    protected static @CheckForNull ParameterTransformation parseParameterTransformation(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, 
        @Nonnull File xmlFromApplicationDir, @Nonnull File parameterTransformerXsltDirectory, 
        @CheckForNull Element element
    ) throws ConfigurationException {
        try {
            if (element == null) return null;

            var xsltName = getMandatoryAttribute(element, "xslt");
            var parameterTransformerFile = new File(parameterTransformerXsltDirectory, xsltName);
            if ( ! parameterTransformerFile.exists()) throw new ConfigurationException("XSLT file '"+xsltName+"' not found");
            var parameterTransformer = getTransformerOrScheduleCompilation(threads, parameterTransformerFile.getAbsolutePath(),
                    new StyleVisionXslt(parameterTransformerFile));

            var dataSourceCommands = new ArrayList<DataSourceCommand>();
            for (var command : getSubElements(element, "*"))
                dataSourceCommands.add(DataSourceCommand.newForConfig(
                    tx, threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, command));

            var result = new ParameterTransformation();
            result.dataSourceCommands = dataSourceCommands;
            result.xslt = parameterTransformer;
            return result;
        }
        catch (ConfigurationException e) { throw new ConfigurationException("<parameter-transformation>", e); }
    }
    
    protected static @Nonnull Endpoint parseEndpoint(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads, @Nonnull Map<String, Transformer> transformers,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File staticDir, @Nonnull File parameterTransformerXsltDirectory,
        @Nonnull EndpointHierarchyFolderNode parentOrNull, @Nonnull Element element
    ) throws ConfigurationException {
        var name = new NodeName(getMandatoryAttribute(element, "name"));
        try {
            assertNoOtherElements(element, "parameter", "parameter-transformation", "include-in-hash",
                "success", "error", "task");

            var result = new Endpoint();
            
            setEndpointHierarchyNodeAttributes(result, parentOrNull, element);
            
            result.name = new NodeName(getMandatoryAttribute(element, "name"));
            result.parameterTransformation = parseParameterTransformation(tx, threads, applicationDir, 
                httpXsltDirectory, xmlFromApplicationDir,
                parameterTransformerXsltDirectory, getOptionalSingleSubElement(element, "parameter-transformation"));

            var includeInHashElement = getOptionalSingleSubElement(element, "include-in-hash");
            var includeInHashParameters = includeInHashElement == null ? new ArrayList<String>() 
                : parseList(includeInHashElement, "parameter", "name");
            result.parametersForHash = new ParametersForHash(includeInHashParameters.stream().map(ParameterName::new).collect(toList()));
            for (var p : result.parametersForHash.parameters)
                if ( ! result.aggregateParametersOverParents().containsKey(p))
                    throw new ConfigurationException("Endpoint has <include-in-hash> parameter '" + p.name + "'" +
                        " but this endpoint does not have a parameter of that name");

            try { result.success = parseResponseConfiguration(transformers, result.aggregateParametersOverParents().keySet(),
                getOptionalSingleSubElement(element, "success")); }
            catch (ConfigurationException e) { throw new ConfigurationException("<success>", e); }
            
            try { result.error = parseResponseConfiguration(transformers, new HashSet<>(),  // errors don't have access to params
                getOptionalSingleSubElement(element, "error")); }
            catch (ConfigurationException e) { throw new ConfigurationException("<error>", e); }
            
            if (result.error instanceof ForwardToEndpointResponseConfiguration)
                throw new ConfigurationException("<forward-to-endpoint> was used in <error>, may only be used in <success>");
            
            if ( ! result.error.inputIntermediateValues.isEmpty()) 
                throw new ConfigurationException("<error> may not have consume <input-intermediate-value>s, " +
                    "as the tasks which produce those intermediate values might not have been successful");
            
            for (var taskElement : getSubElements(element, "task"))
                result.tasks.add(parseTask(threads, httpXsltDirectory, 
                    transformers, staticDir, result.aggregateParametersOverParents().keySet(), taskElement));
            
            var successAndTasks = new ArrayList<IntermediateValueProducerConsumer>();
            successAndTasks.add(result.success);
            successAndTasks.addAll(result.tasks);
            IntermediateValueProducerConsumer.assertNoCircularDependencies(successAndTasks);

            return result;
        }
        catch (ConfigurationException e) { throw new ConfigurationException("<endpoint name='" + name.name + "'>", e); }
    }
    
    protected static @Nonnull EndpointHierarchyFolderNode parseFolderNode(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads, @Nonnull Map<String, Transformer> transformers,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File staticDir, @Nonnull File parameterTransformerXsltDirectory,
        @CheckForNull EndpointHierarchyFolderNode parentOrNull, @Nonnull Element element
    ) throws ConfigurationException {
        assertNoOtherElements(element, "parameter", "endpoint-folder", "endpoint");

        var result = new EndpointHierarchyFolderNode();
        setEndpointHierarchyNodeAttributes(result, parentOrNull, element);

        var children = new ArrayList<EndpointHierarchyNode>();
        for (var el : getSubElements(element, "endpoint-folder"))
            children.add(parseFolderNode(tx, threads, transformers, applicationDir, httpXsltDirectory, xmlFromApplicationDir, staticDir,
                parameterTransformerXsltDirectory, result, el));
        for (var el : getSubElements(element, "endpoint"))
            children.add(parseEndpoint(tx, threads, transformers, applicationDir, httpXsltDirectory, xmlFromApplicationDir, staticDir,
                parameterTransformerXsltDirectory, result, el));
        result.children = children.toArray(new EndpointHierarchyNode[0]);
        
        return result;
    }
    
    protected static void assertUniqueEndpointNames(@Nonnull Set<NodeName> documentIdsFound, @Nonnull EndpointHierarchyNode n)
    throws ConfigurationException {
        if (n instanceof Endpoint) {
            var id = ((Endpoint) n).name;
            if (documentIdsFound.contains(id)) throw new ConfigurationException("More than one <document name='" + id + "'>");
            documentIdsFound.add(id);
        }
        else if (n instanceof EndpointHierarchyFolderNode) {
            for (var child : ((EndpointHierarchyFolderNode) n).children) assertUniqueEndpointNames(documentIdsFound, child);
        }
        else throw new RuntimeException("Unreachable: " + n.getClass());
    }
    
    protected static void collectEndpointForwards(@Nonnull Map<NodeName, Set<NodeName>> result, @Nonnull EndpointHierarchyNode n) {
        if (n instanceof Endpoint) {
            var endpoint = (Endpoint) n;
            result.putIfAbsent(endpoint.name, new HashSet<>());
            if (endpoint.success instanceof ForwardToEndpointResponseConfiguration)
                result.get(endpoint.name).add(((ForwardToEndpointResponseConfiguration) endpoint.success).endpoint);
            if (endpoint.error instanceof ForwardToEndpointResponseConfiguration)
                result.get(endpoint.name).add(((ForwardToEndpointResponseConfiguration) endpoint.error).endpoint);
        }
        else if (n instanceof EndpointHierarchyFolderNode) {
            for (var child : ((EndpointHierarchyFolderNode) n).children)
                collectEndpointForwards(result, child);
        }
        else throw new RuntimeException("Unreachable: " + n.getClass());
    }

    protected static void assertNoCircularReferencesStartingFrom(
        Map<NodeName, Set<NodeName>> references, List<NodeName> soFar, NodeName current
    ) throws ConfigurationException {
        if (soFar.contains(current)) {
            soFar.add(current);
            throw new ConfigurationException("Circular references in <forward-to-endpoint> chain: " +
                soFar.stream().map(x -> "'" + x.name + "'").collect(Collectors.joining(" -> ")));
        }
        
        soFar.add(current);
        for (var dest : references.get(current))
            assertNoCircularReferencesStartingFrom(references, soFar, dest);
    }

    protected static void assertEndpointForwardsExitAndNoCircularReferences(@Nonnull EndpointHierarchyNode root) 
    throws ConfigurationException {
        var forwardTo = new HashMap<NodeName, Set<NodeName>>();
        collectEndpointForwards(forwardTo, root);
        
        for (var from : forwardTo.keySet())
            for (var to : forwardTo.get(from))
                if ( ! forwardTo.containsKey(to))
                    throw new ConfigurationException("Endpoint '" + from.name + "' has <forward-to-endpoint> " +
                        "to '" + to.name + "', but there is no endpoint called '" + to.name + "'");
        
        for (var start : forwardTo.keySet())
            assertNoCircularReferencesStartingFrom(forwardTo, new ArrayList<>(), start);
    }
    
    public static @Nonnull EndpointHierarchyFolderNode parse(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads, @Nonnull Map<String, Transformer> transformers,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File staticDir, @Nonnull File parameterTransformerXsltDirectory,
        @Nonnull InputStream xml
    ) throws ConfigurationException {
        try (var ignored = new Timer(EndpointHierarchyFolderNode.class.getSimpleName()+".parse")){
            var root = from(xml);
            
            if ( ! "endpoint-folder".equals(root.getNodeName()))
                throw new ConfigurationException("Root element must be <endpoint-folder>");
            var result = parseFolderNode(tx, threads, transformers, applicationDir, httpXsltDirectory, xmlFromApplicationDir, staticDir,
                parameterTransformerXsltDirectory, null, root);
            
            assertUniqueEndpointNames(new HashSet<>(), result);
            assertEndpointForwardsExitAndNoCircularReferences(result);
            
            return result;
        }
    }

    public static @Nonnull EndpointHierarchyFolderNode parse(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads, @Nonnull Map<String, Transformer> transformers,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File staticDir, @Nonnull File parameterTransformerXsltDirectory,
        @Nonnull File file
    ) throws ConfigurationException {
        try (var i = new FileInputStream(file)) {
            return parse(tx, threads, transformers, applicationDir, httpXsltDirectory,
                xmlFromApplicationDir, staticDir, parameterTransformerXsltDirectory, i);
        }
        catch (Exception e) { throw new ConfigurationException(file.getAbsolutePath(), e); }
    }
}
