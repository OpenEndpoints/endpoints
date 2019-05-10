package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.MD5Hex;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.PlaintextParameterReplacer;
import endpoints.TransformationContext;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collection;

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
    public @Nonnull DataSourceCommandResult scheduleExecution(@Nonnull TransformationContext context) {
        var result = new DataSourceCommandResult() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                var md5Digest = MD5Hex.md5(replacePlainTextParameters(messageStringPattern, context.params));

                var result = DomParser.newDocumentBuilder().newDocument();

                var hashElement = result.createElement("hash");
                hashElement.setTextContent(md5Digest);

                if (idPatternOrNull != null)
                    hashElement.setAttribute("id", replacePlainTextParameters(idPatternOrNull, context.params));

                return new Element[] { hashElement };
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
