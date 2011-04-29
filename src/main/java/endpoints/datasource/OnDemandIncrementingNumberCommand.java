package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import endpoints.ApplicationTransaction;
import endpoints.EndpointExecutor.UploadedFile;
import endpoints.OnDemandIncrementingNumber;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;

public class OnDemandIncrementingNumberCommand extends DataSourceCommand {

    protected @Nonnull OnDemandIncrementingNumberType type;

    public OnDemandIncrementingNumberCommand(
        @Nonnull DbTransaction tx, @Nonnull WeaklyCachedXsltTransformer.XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element command
    ) throws ConfigurationException {
        super(tx, threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, command);
        assertNoOtherElements(command);
        
        var typeString = getMandatoryAttribute(command, "type");
        try { type = OnDemandIncrementingNumberType.valueOf(typeString); }
        catch (IllegalArgumentException e) { 
            throw new ConfigurationException("type='" + typeString + "' invalid: should be one of " + 
                Arrays.stream(OnDemandIncrementingNumberType.values()).map(x -> "'" + x.name() + "'")
                    .collect(Collectors.joining(", ")));
        }
    }

    @Override
    public @Nonnull DataSourceCommandResult execute(
        @Nonnull ApplicationTransaction tx,
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc
    ) {
        return new DataSourceCommandResult() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                var result = DomParser.newDocumentBuilder().newDocument();

                var e = result.createElement("on-demand-incrementing-number");
                e.setAttribute("type", type.name());
                e.setTextContent(Integer.toString(autoInc.get(type).getOrFetchValue(tx.db)));

                return new Element[] { e };
            }
        };
    }
}
