package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import endpoints.task.TaskCondition;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

public abstract class ResponseConfiguration extends EndpointExecutionParticipant {

    protected final @Nonnull String tagName;
    protected final @Nonnull TaskCondition condition;

    public ResponseConfiguration(@Nonnull Element config) throws ConfigurationException {
        super(config);
        tagName = config.getTagName();
        condition = new TaskCondition(config);
    }
    
    public @Nonnull String getHumanReadableId() {
        return "<" + tagName + condition.getDescriptionForDebugging() + ">";
    }

    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException { 
        condition.assertParametersSuffice(params, inputIntermediateValues);
    }
    
    public void assertTemplatesValid() throws DocumentTemplateInvalidException { }
    
    public boolean satisfiesCondition(@Nonnull Map<String, String> parameters) {
        return condition.evaluate(parameters);
    }
}
