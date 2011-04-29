package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.ApplicationTransaction;
import endpoints.EndpointExecutor;
import endpoints.EndpointExecutor.UploadedFile;
import endpoints.OnDemandIncrementingNumber;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ParametersCommand extends DataSourceCommand {

    public ParametersCommand(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(tx, threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, config);
    }

    public static @Nonnull Element[] createParametersElements(@Nonnull Map<ParameterName, String> params) {
        var doc = DomParser.newDocumentBuilder().newDocument();
        var result = new ArrayList<Element>();
        for (var p : params.entrySet()) {
            var el = doc.createElement("parameter");
            el.setAttribute("name", p.getKey().name);
            el.setAttribute("value", p.getValue());
            result.add(el);
        }
        return result.toArray(new Element[0]);
    }

    public static @Nonnull Element createParametersElement(@Nonnull String tagName, @Nonnull Map<ParameterName, String> params) {
        var doc = DomParser.newDocumentBuilder().newDocument();
        var result = doc.createElement(tagName);
        doc.appendChild(result);
        for (var e : createParametersElements(params))
            result.appendChild(doc.importNode(e, true));
        return result;
    }

    @Override
    public @Nonnull DataSourceCommandResult execute(
        @Nonnull ApplicationTransaction tx,
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc
    ) {
        return new DataSourceCommandResult() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                return new Element[] {
                    createParametersElement("parameters", params)
                };
            }
        };
    }
}
