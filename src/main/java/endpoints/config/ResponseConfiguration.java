package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Set;

public abstract class ResponseConfiguration extends IntermediateValueProducerConsumer {

    public ResponseConfiguration(@CheckForNull Element config) throws ConfigurationException {
        super(config);
    }

    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException { }
    public void assertTemplatesValid() throws DocumentTemplateInvalidException { }
}
