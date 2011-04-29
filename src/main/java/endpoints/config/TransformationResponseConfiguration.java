package endpoints.config;

import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

public class TransformationResponseConfiguration extends ResponseConfiguration {
    
    public @Nonnull Transformer transformer;
    public @CheckForNull String downloadFilenamePatternOrNull;

    @Override public void assertTemplatesValid() throws DocumentTemplateInvalidException {
        transformer.assertTemplatesValid();
    }

}
