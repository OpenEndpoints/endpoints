package endpoints.task;

import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.DeploymentParameters;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.NodeName;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import endpoints.generated.jooq.tables.records.ShortLinkToEndpointParameterRecord;
import endpoints.generated.jooq.tables.records.ShortLinkToEndpointRecord;
import endpoints.shortlinktoendpoint.ShortLinkToEndpointCode;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static com.databasesandlife.util.DomParser.*;

public class CreateShortLinkToEndpointTask extends Task {
    
    protected final @Nonnull NodeName destinationEndpoint;
    protected final @Nonnull IntermediateValueName outputIntermediateValue;
    protected final @Nonnull Duration expiry;

    public CreateShortLinkToEndpointTask(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory, @Nonnull File ooxmlDir,
        @Nonnull Map<String, Transformer> transformers, @Nonnull File staticDir,
        int indexFromZero, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, httpXsltDirectory, ooxmlDir, transformers, staticDir, indexFromZero, config);
        assertNoOtherElements(config);
        destinationEndpoint = new NodeName(getMandatoryAttribute(config, "destination-endpoint-name"));
        outputIntermediateValue = new IntermediateValueName(getMandatoryAttribute(config, "output-intermediate-value"));
        expiry = Duration.ofMinutes(parseMandatoryIntAttribute(config, "expires-in-minutes"));
    }

    @Override 
    public @Nonnull Set<IntermediateValueName> getOutputIntermediateValues() {
        var result = new HashSet<>(super.getOutputIntermediateValues());
        result.add(outputIntermediateValue);
        return result;
    }

    @Override
    protected void executeThenScheduleSynchronizationPoint(
        @Nonnull TransformationContext context, @Nonnull SynchronizationPoint workComplete
    ) {
        synchronized (context.tx.db) {
            var result = ShortLinkToEndpointCode.newRandom();
            var now = Instant.now();
            
            var linkRecord = new ShortLinkToEndpointRecord();
            linkRecord.setShortLinkToEndpointCode(result);
            linkRecord.setApplication(context.applicationName);
            linkRecord.setEnvironment(context.environment);
            linkRecord.setEndpoint(destinationEndpoint);
            linkRecord.setCreatedOn(now);
            linkRecord.setExpiresOn(now.plus(expiry));
            context.tx.db.insert(linkRecord);

            var params = context.getStringParametersIncludingIntermediateValues(inputIntermediateValues);
            for (var e : params.entrySet()) {
                var parameterRecord = new ShortLinkToEndpointParameterRecord();
                parameterRecord.setShortLinkToEndpointCode(result);
                parameterRecord.setParameterName(new ParameterName(e.getKey()));
                parameterRecord.setParameterValue(e.getValue());
                context.tx.db.insert(parameterRecord);
            }
            
            context.intermediateValues.put(outputIntermediateValue,
                DeploymentParameters.get().baseUrl.toExternalForm() + "shortlink/" + result.getCode());
            
            context.threads.addTask(workComplete);
        }
    }
}
