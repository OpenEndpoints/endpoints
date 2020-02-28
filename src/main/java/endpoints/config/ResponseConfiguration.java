package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import lombok.Getter;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.Set;

public abstract class ResponseConfiguration extends EndpointExecutionParticipant {

    protected @Getter @Nonnull String humanReadableId;

    public ResponseConfiguration(@Nonnull Element config) throws ConfigurationException {
        super(config);
        humanReadableId = "<" + config.getTagName() + ">";
    }

    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException { }
    public void assertTemplatesValid() throws DocumentTemplateInvalidException { }
}
