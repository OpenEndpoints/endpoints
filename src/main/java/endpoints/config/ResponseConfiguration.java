package endpoints.config;

import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;

public abstract class ResponseConfiguration {

    public void assertTemplatesValid() throws DocumentTemplateInvalidException { }
}
