package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import endpoints.PlaintextParameterReplacer;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Set;

public class TransformationResponseConfiguration extends ResponseConfiguration {
    
    public @Nonnull Transformer transformer;
    public @CheckForNull String downloadFilenamePatternOrNull;

    @SuppressFBWarnings("NP_METHOD_PARAMETER_TIGHTENS_ANNOTATION")
    public TransformationResponseConfiguration(@Nonnull Element config) throws ConfigurationException {
        super(config);
    }

    @Override public void assertParametersSuffice(Set<ParameterName> params) throws ConfigurationException {
        transformer.assertParametersSuffice(params, inputIntermediateValues);
        if (downloadFilenamePatternOrNull != null)
            PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues, 
                downloadFilenamePatternOrNull, "download-filename");
    }

    @Override public void assertTemplatesValid() throws DocumentTemplateInvalidException {
        transformer.assertTemplatesValid();
    }

}
