package endpoints.datasource;

import com.databasesandlife.util.DomVariableExpander;
import com.databasesandlife.util.DomVariableExpander.VariableNotFoundException;
import com.databasesandlife.util.DomVariableExpander.VariableSyntax;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.getSubElements;

public class LiteralXmlCommand extends DataSourceCommand {
    
    protected @Nonnull Element source;

    public LiteralXmlCommand(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(tx, threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, config);
        source = config;
    }
    
    @Override
    public void assertParametersSuffice(@Nonnull Collection<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        var emptyParams = params.stream().collect(Collectors.toMap(param -> param.name, param -> ""));
        try { DomVariableExpander.expand(VariableSyntax.dollarThenBraces, emptyParams, source); }
        catch (VariableNotFoundException e) { throw new ConfigurationException(e); }
    }

    @Override
    public @Nonnull DataSourceCommandResult scheduleExecution(@Nonnull TransformationContext context) {
        var result = new DataSourceCommandResult() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                var stringParams = context.params.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name, e -> e.getValue()));
                return getSubElements(source, "*").stream()
                    .map(e -> DomVariableExpander.expand(VariableSyntax.dollarThenBraces, stringParams, e).getDocumentElement())
                    .toArray(Element[]::new);
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
