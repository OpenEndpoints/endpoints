package endpoints.datasource;

import com.databasesandlife.util.DomVariableExpander;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.HttpRequestSpecification;
import endpoints.config.HttpRequestSpecification.HttpRequestFailedException;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomVariableExpander.VariableSyntax.dollarThenBraces;

public class XmlFromUrlCommand extends DataSourceCommand {
    
    protected final @Nonnull HttpRequestSpecification spec;
    
    public XmlFromUrlCommand(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull Element config
    ) throws ConfigurationException {
        super(tx, threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, config);
        spec = new HttpRequestSpecification(threads, httpXsltDirectory, config);
    }

    @Override
    public void assertParametersSuffice(@Nonnull Collection<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        spec.assertParametersSuffice(params);
    }

    @Override
    public @Nonnull DataSourceCommandResult scheduleExecution(@Nonnull TransformationContext context) {
        var result = new DataSourceCommandResult() {
            @Override protected @Nonnull Element[] populateOrThrow() throws TransformationFailedException {
                try {
                    var stringParams = context.params.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name, e -> e.getValue()));
                    var unexpanded = spec.executeAndParseResponse(context.params, context.fileUploads);
                    if (unexpanded == null) return new Element[0];
                    var expanded = DomVariableExpander.expand(dollarThenBraces, stringParams, unexpanded).getDocumentElement();
                    return new Element[] { expanded };
                }
                catch (HttpRequestFailedException e) { throw new TransformationFailedException(e); }
            }
        };
        context.threads.addTaskOffPool(result);
        return result;
    }
}
