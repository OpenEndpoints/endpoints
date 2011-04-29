package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.ApplicationTransaction;
import endpoints.EndpointExecutor;
import endpoints.EndpointExecutor.UploadedFile;
import endpoints.OnDemandIncrementingNumber;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.config.ParameterName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class DataSource {
    
    public final @Nonnull List<DataSourceCommand> commands = new ArrayList<>();

    /** Checks that no variables other than those supplied are necessary to execute all commands */
    public void assertParametersSuffice(@Nonnull Collection<ParameterName> params) throws ConfigurationException {
        for (var c : commands) {
            try { c.assertParametersSuffice(params); }
            catch (ConfigurationException e) { throw new ConfigurationException(c.getClass().getSimpleName(), e); }
        }
    }

    public @Nonnull Document execute(
        @Nonnull ApplicationTransaction tx, 
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc
    )
    throws TransformationFailedException {
        var futures = commands.stream().map(c -> c.execute(tx, params, fileUploads, autoInc)).collect(Collectors.toList());
        var result = DomParser.newDocumentBuilder().newDocument();
        result.appendChild(result.createElement("transformation-input"));
        for (var r : futures)
            for (var element : r.getOrThrow())
                result.getDocumentElement().appendChild(result.importNode(element, true));
        return result;
    }
}
