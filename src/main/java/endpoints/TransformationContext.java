package endpoints;

import com.databasesandlife.util.ThreadPool;
import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.offerready.xslt.destination.BufferedDocumentGenerationDestination;
import endpoints.config.*;
import endpoints.datasource.TransformationFailedException;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Collections.synchronizedMap;

/**
 * Represents certain things that the execution of transformations and data sources need.
 *   <p>
 * There are (at the time of writing) three times in the endpoint's execution where one is created:
 * During parameter transformation, during success flow, and during error flow.
 * If a success flow forwards to another endpoint then its success flow gets its own object.
 *   <p>
 * An {@link ApplicationTransaction} can span multiple {@link TransformationContext}s, for example, in the success case,
 * the parameters and transformed and the success flow executes within one transaction.
 */
@RequiredArgsConstructor
public class TransformationContext {

    public enum ParameterNotFoundPolicy { error, emptyString }

    public final @Nonnull PublishEnvironment environment;
    public final @Nonnull ApplicationName applicationName;
    public final @Nonnull Application application;
    public final @Nonnull ApplicationTransaction tx;
    public final @Nonnull ThreadPool threads;
    public final @Nonnull Endpoint endpoint;
    private final @Nonnull Map<ParameterName, String> params;
    public final @Nonnull ParameterNotFoundPolicy parameterNotFoundPolicy;
    public final @Nonnull Map<IntermediateValueName, String> intermediateValues = synchronizedMap(new HashMap<>());
    public final @Nonnull RequestId requestId;
    public final @Nonnull Request request;
    public final @Nonnull Map<OnDemandIncrementingNumber.OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc;
    public final @Nonnull Map<String, String> requestLogExpressionCaptures;
    public boolean alreadyDeliveredResponse = false;

    public synchronized @Nonnull Runnable scheduleTransformation(
        @Nonnull BufferedDocumentGenerationDestination outputTo,
        @Nonnull Transformer transformer,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws TransformationFailedException {
        var result = new SynchronizationPoint();
        var process = transformer.scheduleExecution(this, visibleIntermediateValues, outputTo);
        threads.addTaskWithDependencies(List.of(process), result);
        return result;
    }
    
    public static Set<String> getSystemParameterNames() {
        return Set.of("request-id");
    }

    public @Nonnull Map<ParameterName, String> getParametersIncludingSystemParameters() {
        var result = new HashMap<>(params);
        result.put(new ParameterName("request-id"), requestId.id().toString());
        return result;
    }
    
    public synchronized @Nonnull Map<IntermediateValueName, String> getVisibleIntermediateValues(
        @Nonnull Set<IntermediateValueName> visible
    ) {
        var result = new HashMap<>(intermediateValues);
        result.keySet().retainAll(visible);
        for (var n : visible) {
            if ( ! result.containsKey(n)) 
                throw new RuntimeException("Should never happen: Intermediate value '" + n.name + "' " +
                    "required but has not been produced yet");
        }
        return result;
    }

    public synchronized @Nonnull Map<String, String> getStringParametersIncludingIntermediateValues(
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        Map<String, String> result = new HashMap<>() {
            @Override public String get(Object key) {
                var result = super.get(key);
                if (parameterNotFoundPolicy == ParameterNotFoundPolicy.emptyString && result == null) result = "";
                return result;
            }
        };
        result.putAll(getParametersIncludingSystemParameters()
            .entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name, e -> e.getValue())));
        result.putAll(getVisibleIntermediateValues(visibleIntermediateValues)
            .entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name, e -> e.getValue())));
        return result;
    }
}
