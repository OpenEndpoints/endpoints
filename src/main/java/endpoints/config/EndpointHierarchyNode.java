package endpoints.config;

import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

@SuppressWarnings("serial")
public abstract class EndpointHierarchyNode {
    
    public @CheckForNull EndpointHierarchyNode parentOrNull;
    public @CheckForNull String parameterMultipleValueSeparatorOverride;
    public final @Nonnull Map<ParameterName, Parameter> parameters = new HashMap<>();
    
    public static class NodeNotFoundException extends Exception {
        protected @Nonnull NodeName nodeName;
        public NodeNotFoundException(@Nonnull NodeName nodeName, @Nonnull String msgPrefix) {
            super(msgPrefix + ": " + nodeName);
            this.nodeName = nodeName;
        }
        public NodeNotFoundException(@Nonnull NodeName nodeName) {
            this(nodeName, "Cannot find node");
        }
    }
    
    public @Nonnull Map<ParameterName, Parameter> aggregateParametersOverParents() {
        Map<ParameterName, Parameter> result;
        
        if (parentOrNull == null) result = new HashMap<>();
        else result = parentOrNull.aggregateParametersOverParents();
        
        result.putAll(parameters);
        
        return result;
    }

    public @Nonnull String getParameterMultipleValueSeparator() {
        if (parameterMultipleValueSeparatorOverride != null) return parameterMultipleValueSeparatorOverride;
        if (parentOrNull != null) return parentOrNull.getParameterMultipleValueSeparator();
        return "||";
    }
    
    public abstract @Nonnull Map<NodeName, Endpoint> getEndpointForName();
    public abstract @Nonnull Endpoint findEndpointOrThrow(@Nonnull NodeName name) throws NodeNotFoundException;
    
    public abstract void assertTemplatesValid() throws DocumentTemplateInvalidException;
}

