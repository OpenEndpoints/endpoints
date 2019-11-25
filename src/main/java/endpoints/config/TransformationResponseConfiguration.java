package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import endpoints.PlaintextParameterReplacer;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static com.databasesandlife.util.DomParser.getOptionalAttribute;

public class TransformationResponseConfiguration extends ResponseConfiguration {
    
    public @Nonnull Transformer transformer;
    public @CheckForNull String downloadFilenamePatternOrNull;

    public TransformationResponseConfiguration(
        @Nonnull Map<String, Transformer> transformers, @Nonnull Element config, @Nonnull Element responseTransformationElement
    ) throws ConfigurationException {
        super(config);

        assertNoOtherElements(config, "response-transformation", "input-intermediate-value");
        var transformerName = getMandatoryAttribute(responseTransformationElement, "name");
        transformer = transformers.get(transformerName);
        if (transformer == null) throw new ConfigurationException("Transformer name='"+transformerName+"' not found");
        downloadFilenamePatternOrNull = getOptionalAttribute(responseTransformationElement, "download-filename");
    }

    @Override public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        transformer.assertParametersSuffice(params, inputIntermediateValues);
        if (downloadFilenamePatternOrNull != null)
            PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues, 
                downloadFilenamePatternOrNull, "download-filename");
    }

    @Override public void assertTemplatesValid() throws DocumentTemplateInvalidException {
        transformer.assertTemplatesValid();
    }

}
