package endpoints.config.response;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.PlaintextParameterReplacer;
import endpoints.config.ParameterName;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.util.Set;

import static com.databasesandlife.util.DomParser.*;

public class StaticResponseConfiguration extends ResponseConfiguration {

    public final @Nonnull File file;
    public final @CheckForNull String downloadFilenamePatternOrNull;

    @SneakyThrows(IOException.class)
    public static @Nonnull File findStaticFileAndAssertExists(@Nonnull File staticDir, @Nonnull String filename)
        throws ConfigurationException {
        var result = new File(staticDir, filename);
        if ( ! result.getCanonicalPath().startsWith(staticDir.getCanonicalPath()+File.separator))
            throw new ConfigurationException("Filename " +
                "'" + filename + "' attempts to reference outside application's 'static' directory");
        if ( ! result.exists())
            throw new ConfigurationException("Filename " +
                "'" + filename + "' not found in application's 'static' directory");
        return result;
    }

    public StaticResponseConfiguration(@Nonnull File staticDir, @Nonnull Element config, @Nonnull Element responseElement)
    throws ConfigurationException {
        super(config);
        assertNoOtherElements(responseElement);
        file = findStaticFileAndAssertExists(staticDir, getMandatoryAttribute(responseElement, "filename"));
        downloadFilenamePatternOrNull = getOptionalAttribute(responseElement, "download-filename");
    }

    @Override public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        if (downloadFilenamePatternOrNull != null)
            PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues,
                downloadFilenamePatternOrNull, "download-filename");
    }

    @Override public boolean isDownload() { return downloadFilenamePatternOrNull != null; }
}
