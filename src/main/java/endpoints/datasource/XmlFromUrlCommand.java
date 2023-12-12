package endpoints.datasource;

import com.databasesandlife.util.DomVariableExpander;
import com.databasesandlife.util.ThreadPool.ScheduleDependencyInAnyOrder;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.HttpRequestSpecification;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.Set;

import static com.databasesandlife.util.DomParser.getOptionalAttribute;
import static com.databasesandlife.util.DomVariableExpander.VariableSyntax.dollarThenBraces;
import static endpoints.config.ApplicationFactory.httpXsltDir;

public class XmlFromUrlCommand extends DataSourceCommand {

    protected final @CheckForNull String outputWrapperElementName;
    protected final @Nonnull HttpRequestSpecification spec;
    protected final boolean expandParametersInResponse;
    
    public XmlFromUrlCommand(
        @Nonnull XsltCompilationThreads threads, @Nonnull File applicationDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, applicationDir, config);
        outputWrapperElementName = getOptionalAttribute(config, "tag");
        spec = new HttpRequestSpecification(threads, new File(applicationDir, httpXsltDir), config);
        expandParametersInResponse = Boolean.parseBoolean(getOptionalAttribute(config, "expand-parameters-in-response", "true"));
    }

    @Override
    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        super.assertParametersSuffice(params, visibleIntermediateValues);
        spec.assertParametersSuffice(params, visibleIntermediateValues);
    }

    @Override
    public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        abstract class LaterResult extends DataSourceCommandFetcher implements ScheduleDependencyInAnyOrder { }
        
        var stringParams = context.getParametersAndIntermediateValuesAndSecrets(visibleIntermediateValues);
        var result = new LaterResult() {
            public Element unexpanded;
            @Override protected @Nonnull Element[] populateOrThrow() {
                if (unexpanded == null) return new Element[0];
                var expanded = expandParametersInResponse
                    ? DomVariableExpander.expand(dollarThenBraces, p -> stringParams.get(p).get(), unexpanded).getDocumentElement()
                    : unexpanded;

                Element wrapped;
                if (outputWrapperElementName == null) wrapped = expanded;
                else {
                    wrapped = expanded.getOwnerDocument().createElement(outputWrapperElementName);
                    wrapped.appendChild(expanded);
                }

                return new Element[] { wrapped };
            }
        };
        spec.scheduleExecutionAndParseResponse(context, visibleIntermediateValues, element -> {
            result.unexpanded = element;
            context.threads.addTask(result);
        });
        
        return result;
    }
}
