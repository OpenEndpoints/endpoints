package endpoints.config.response;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.HttpRequestSpecification;
import endpoints.PlaintextParameterReplacer;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import java.io.File;
import java.util.Set;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static com.databasesandlife.util.DomParser.getOptionalAttribute;

public class UrlResponseConfiguration extends ResponseConfiguration {
    
    public final @Nonnull HttpRequestSpecification spec;
    public final @CheckForNull String downloadFilenamePatternOrNull;

    public UrlResponseConfiguration(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory, 
        @Nonnull Element config, @Nonnull Element responseElement
    ) throws ConfigurationException {
        super(config);
        spec = new HttpRequestSpecification(threads, httpXsltDirectory, responseElement);
        downloadFilenamePatternOrNull = getOptionalAttribute(responseElement, "download-filename");
    }

    @Override public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        if (downloadFilenamePatternOrNull != null)
            PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues,
                downloadFilenamePatternOrNull, "download-filename");
    }

    public boolean isDownload() { return downloadFilenamePatternOrNull != null; }
}
