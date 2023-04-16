package endpoints.task;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.PlaintextParameterReplacer;
import endpoints.RequestId;
import endpoints.TransformationContext;
import endpoints.config.Endpoint;
import endpoints.config.NodeName;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import endpoints.config.response.ForwardToEndpointResponseConfiguration;
import endpoints.generated.jooq.tables.records.RequestLogExpressionCaptureRecord;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;

public class RequestLogExpressionCaptureTask extends Task {
    
    protected final @Nonnull String key, valuePattern;
    
    public RequestLogExpressionCaptureTask(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory, @Nonnull File ooxmlDir,
        @Nonnull Map<String, Transformer> transformers, @Nonnull File staticDir,
        int indexFromZero, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, httpXsltDirectory, ooxmlDir, transformers, staticDir, indexFromZero, config);
        
        assertNoOtherElements(config);

        key = getMandatoryAttribute(config, "key");
        valuePattern = getMandatoryAttribute(config, "value");
        
        if (key.contains("${")) throw new ConfigurationException("'key' may not contain parameters such as ${foo}");
    }
    
    @SneakyThrows(ConfigurationException.class)
    public static RequestLogExpressionCaptureTask newForTesting(@Nonnull String key, @Nonnull String valuePattern) {
        var xml = DomParser.from("<foo/>");
        xml.setAttribute("key", key);
        xml.setAttribute("value", valuePattern);
        return new RequestLogExpressionCaptureTask(new XsltCompilationThreads(),
            new File("/"), new File("/"), Map.of(), new File("/"), 0, xml);
    }

    @Override
    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues, valuePattern, 
            "'value' attribute");
    }

    @Override
    public void executeThenScheduleSynchronizationPoint(
        @Nonnull TransformationContext context, @Nonnull SynchronizationPoint workComplete
    ) {
        var stringParams = context.getStringParametersIncludingIntermediateValues(inputIntermediateValues);
        
        synchronized (context.requestLogExpressionCaptures) {
            context.requestLogExpressionCaptures.put(
                replacePlainTextParameters(key, stringParams), replacePlainTextParameters(valuePattern, stringParams));
        }
        
        context.threads.addTask(workComplete);
    }

    public static void assertUniqueCaptureKeys(
        @Nonnull Map<NodeName, Endpoint> endpointForName, @Nonnull Map<String, List<NodeName>> keysSoFar, @Nonnull Endpoint endpoint
    ) throws ConfigurationException {
        var newKeysSoFar = new HashMap<>(keysSoFar);
        
        var keys = endpoint.tasks.stream()
            .filter(t -> t instanceof RequestLogExpressionCaptureTask)
            .map(t -> (RequestLogExpressionCaptureTask) t)
            .map(t -> t.key)
            .toList();
        for (var newKey : keys) {
            if (newKeysSoFar.containsKey(newKey)) {
                var path = new ArrayList<>(newKeysSoFar.get(newKey));
                path.add(endpoint.name);
                throw new ConfigurationException("Request Log Expression Capture Key '" + newKey + "' appears multiple times " +
                    "across endpoints " + path.stream().map(n -> "'" + n.name + "'").collect(Collectors.joining(" -> "))); 
            }
            newKeysSoFar.put(newKey, List.of());
        }

        for (var e : newKeysSoFar.entrySet()) {
            var newList = new ArrayList<>(e.getValue());
            newList.add(endpoint.name);
            e.setValue(newList);
        }
        
        var nextEndpoints = Stream.concat(
            endpoint.success.stream()
                .filter(s -> s instanceof ForwardToEndpointResponseConfiguration)
                .map(s -> (ForwardToEndpointResponseConfiguration) s)
                .map(s -> endpointForName.get(s.endpoint)),
            Optional.of(endpoint.error).stream()
                .filter(s -> s instanceof ForwardToEndpointResponseConfiguration)
                .map(s -> (ForwardToEndpointResponseConfiguration) s)
                .map(s -> endpointForName.get(s.endpoint))
        ).toList();
        
        for (var e : nextEndpoints) assertUniqueCaptureKeys(endpointForName, newKeysSoFar, e);
    }

    public static void writeToDb(@Nonnull DbTransaction tx, @Nonnull RequestId requestId, @Nonnull Map<String, String> vals) {
        for (var e : vals.entrySet()) {
            var record = new RequestLogExpressionCaptureRecord();
            record.setRequestId(requestId);
            record.setKey(e.getKey());
            record.setValue(e.getValue());
            tx.insert(record);
        }
    }
}
