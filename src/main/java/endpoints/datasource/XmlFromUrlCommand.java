package endpoints.datasource;

import com.databasesandlife.util.DomVariableExpander;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.ApplicationTransaction;
import endpoints.EndpointExecutor;
import endpoints.EndpointExecutor.UploadedFile;
import endpoints.OnDemandIncrementingNumber;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.config.HttpRequestSpecification;
import endpoints.config.HttpRequestSpecification.HttpRequestFailedException;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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
    public @Nonnull DataSourceCommandResult execute(
        @Nonnull ApplicationTransaction tx,
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc
    ) {
        return new DataSourceCommandResult() {
            @Override protected @Nonnull Element[] populateOrThrow() throws TransformationFailedException {
                try {
                    var stringParams = params.entrySet().stream().collect(Collectors.toMap(e -> e.getKey().name, e -> e.getValue()));
                    var unexpanded = spec.executeAndParseResponse(params, fileUploads);
                    if (unexpanded == null) return new Element[0];
                    var expanded = DomVariableExpander.expand(dollarThenBraces, stringParams, unexpanded).getDocumentElement();
                    return new Element[] { expanded };
                }
                catch (HttpRequestFailedException e) { throw new TransformationFailedException(e); }
            }
        };
    }
}
