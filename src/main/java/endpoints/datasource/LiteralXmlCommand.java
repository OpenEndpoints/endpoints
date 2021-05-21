package endpoints.datasource;

import com.databasesandlife.util.DomVariableExpander;
import com.databasesandlife.util.DomVariableExpander.VariableNotFoundException;
import com.databasesandlife.util.DomVariableExpander.VariableSyntax;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.getSubElements;

public class LiteralXmlCommand extends DataSourceCommand {
    
    protected @Nonnull Element source;

    public LiteralXmlCommand(
        @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, dataSourcePostProcessingXsltDir, config);
        source = config;
    }
    
    @Override
    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        super.assertParametersSuffice(params, visibleIntermediateValues);

        var stringKeys = new HashSet<String>();
        stringKeys.addAll(params.stream().map(k -> k.name).collect(Collectors.toSet()));
        stringKeys.addAll(visibleIntermediateValues.stream().map(k -> k.name).collect(Collectors.toSet()));
        stringKeys.addAll(TransformationContext.getSystemParameterNames());
        var emptyParams = stringKeys.stream().collect(Collectors.toMap(param -> param, param -> ""));
        
        try { DomVariableExpander.expand(VariableSyntax.dollarThenBraces, emptyParams, source); }
        catch (VariableNotFoundException e) { throw new ConfigurationException(e); }
    }

    @Override
    public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        var result = new DataSourceCommandFetcher() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                var stringParams = context.getStringParametersIncludingIntermediateValues(visibleIntermediateValues);
                return getSubElements(source, "*").stream()
                    .map(e -> DomVariableExpander.expand(VariableSyntax.dollarThenBraces, stringParams, e).getDocumentElement())
                    .toArray(Element[]::new);
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
