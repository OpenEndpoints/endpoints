package endpoints.config.response;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import endpoints.LazyCachingValue;
import endpoints.condition.Condition;
import endpoints.config.EndpointExecutionParticipant;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;

public abstract class ResponseConfiguration extends EndpointExecutionParticipant {

    protected final @Nonnull String tagName;
    protected final @Nonnull Condition condition;

    public ResponseConfiguration(@Nonnull Element config) throws ConfigurationException {
        super(config);
        tagName = config.getTagName();
        condition = new Condition(config);
    }
    
    public @Nonnull String getHumanReadableId() {
        return "<" + tagName + condition.getDescriptionForDebugging() + ">";
    }

    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException { 
        condition.assertParametersSuffice(params, inputIntermediateValues);
    }
    
    public void assertTemplatesValid() throws DocumentTemplateInvalidException { }

    public boolean isConditional() { return condition.isOptional(); }
    public boolean isDownload() { return false; }
    
    public boolean satisfiesCondition(@Nonnull String parameterMultipleValueSeparator, @Nonnull Map<String, LazyCachingValue> parameters) {
        return condition.evaluate(parameterMultipleValueSeparator, parameters);
    }
}
