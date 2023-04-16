package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.MD5Hex;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.PlaintextParameterReplacer;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.Set;

import static com.databasesandlife.util.DomParser.*;
import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;

public class MD5Command extends DataSourceCommand {
    
    protected final @CheckForNull String idPatternOrNull;
    protected final @Nonnull String messageStringPattern;

    public MD5Command(
        @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element command
    ) throws ConfigurationException {
        super(threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, dataSourcePostProcessingXsltDir, command);
        assertNoOtherElements(command, "post-process");
        idPatternOrNull = getOptionalAttribute(command, "id");
        messageStringPattern = getMandatoryAttribute(command, "message-string");
    }

    @Override
    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        super.assertParametersSuffice(params, visibleIntermediateValues);
        PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues,
            messageStringPattern, "'message-string' attribute");
    }

    @Override
    public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        var result = new DataSourceCommandFetcher() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                var stringParams = context.getStringParametersIncludingIntermediateValues(visibleIntermediateValues);

                var md5Digest = MD5Hex.md5(replacePlainTextParameters(messageStringPattern, stringParams));

                var result = DomParser.newDocumentBuilder().newDocument();

                var hashElement = result.createElement("hash");
                hashElement.setTextContent(md5Digest);

                if (idPatternOrNull != null)
                    hashElement.setAttribute("id", replacePlainTextParameters(idPatternOrNull, stringParams));

                return new Element[] { hashElement };
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
