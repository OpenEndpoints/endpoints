package endpoints.datasource;

import com.databasesandlife.util.DomVariableExpander;
import com.databasesandlife.util.DomVariableExpander.VariableNotFoundException;
import com.databasesandlife.util.DomVariableExpander.VariableSyntax;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Set;

import static com.databasesandlife.util.DomParser.getSubElements;

public class LiteralXmlCommand extends DataSourceCommand {
    
    protected final @Nonnull Element source;

    public LiteralXmlCommand(
        @Nonnull XsltCompilationThreads threads, @Nonnull File applicationDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, applicationDir, config);
        source = config;
    }
    
    @Override
    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        super.assertParametersSuffice(params, visibleIntermediateValues);
        
        try { DomVariableExpander.expand(VariableSyntax.dollarThenBraces, param -> "", source); }
        catch (VariableNotFoundException e) { throw new ConfigurationException(e); }
    }

    @Override
    public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        var result = new DataSourceCommandFetcher() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                var stringParams = context.getParametersAndIntermediateValuesAndSecrets(visibleIntermediateValues);
                return getSubElements(source, "*").stream()
                    .map(e -> DomVariableExpander
                        .expand(VariableSyntax.dollarThenBraces, var -> stringParams.get(var).get(), e)
                        .getDocumentElement())
                    .toArray(Element[]::new);
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
