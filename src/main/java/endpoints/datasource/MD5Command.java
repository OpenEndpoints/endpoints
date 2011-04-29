package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.MD5Hex;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.ApplicationTransaction;
import endpoints.EndpointExecutor;
import endpoints.EndpointExecutor.UploadedFile;
import endpoints.OnDemandIncrementingNumber;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.PlaintextParameterReplacer;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static com.databasesandlife.util.DomParser.*;
import static endpoints.PlaintextParameterReplacer.replacePlainTextParameters;

public class MD5Command extends DataSourceCommand {
    
    protected @CheckForNull String idPatternOrNull;
    protected @Nonnull String messageStringPattern;

    public MD5Command(
        @Nonnull DbTransaction tx, @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir, @Nonnull Element command
    ) throws ConfigurationException {
        super(tx, threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, command);
        assertNoOtherElements(command);
        idPatternOrNull = getOptionalAttribute(command, "id");
        messageStringPattern = getMandatoryAttribute(command, "message-string");
    }

    @Override
    public void assertParametersSuffice(@Nonnull Collection<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        PlaintextParameterReplacer.assertParametersSuffice(params, messageStringPattern, "'message-string' attribute");
    }

    @Override
    public @Nonnull DataSourceCommandResult execute(
        @Nonnull ApplicationTransaction tx, 
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc
    ) {
        return new DataSourceCommandResult() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                var md5Digest = MD5Hex.md5(replacePlainTextParameters(messageStringPattern, params));

                var result = DomParser.newDocumentBuilder().newDocument();

                var hashElement = result.createElement("hash");
                hashElement.setTextContent(md5Digest);

                if (idPatternOrNull != null)
                    hashElement.setAttribute("id", replacePlainTextParameters(idPatternOrNull, params));

                return new Element[] { hashElement };
            }
        };
    }
}
