package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import endpoints.DeploymentParameters;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;

public class OnDemandIncrementingNumberCommand extends DataSourceCommand {

    protected final @Nonnull OnDemandIncrementingNumberType type;

    public OnDemandIncrementingNumberCommand(
        @Nonnull WeaklyCachedXsltTransformer.XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element command
    ) throws ConfigurationException {
        super(threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, dataSourcePostProcessingXsltDir, command);
        assertNoOtherElements(command, "post-process");
        
        var typeString = getMandatoryAttribute(command, "type");
        try { type = OnDemandIncrementingNumberType.valueOf(typeString); }
        catch (IllegalArgumentException e) { 
            throw new ConfigurationException("type='" + typeString + "' invalid: should be one of " + 
                Arrays.stream(OnDemandIncrementingNumberType.values()).map(x -> "'" + x.name() + "'")
                    .collect(Collectors.joining(", ")));
        }
        
        var dp = DeploymentParameters.get();
        if (dp.isSingleApplicationMode() && dp.singleApplicationModeTimezoneId == null) 
            throw new ConfigurationException("Endpoints is running in single application mode and <" + command.getNodeName() + "> " +
                "was used, yet ENDPOINTS_SINGLE_APPLICATION_MODE_TIMEZONE_ID was not set");
    }

    @Override
    public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        var result = new DataSourceCommandFetcher() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                var result = DomParser.newDocumentBuilder().newDocument();

                var e = result.createElement("on-demand-incrementing-number");
                e.setAttribute("type", type.name());
                e.setTextContent(Integer.toString(context.autoInc.get(type).getOrFetchValue(context.tx.db)));

                return new Element[] { e };
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
