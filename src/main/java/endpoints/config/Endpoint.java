package endpoints.config;

import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import endpoints.config.response.ResponseConfiguration;
import endpoints.task.Task;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

// Endpoint is not serializable on purpose; because it references compiled XSLT templates
// If you need to serialize an Endpoint, reference a NodeName instead and find it from the application when needed
public class Endpoint extends EndpointHierarchyNode {

    public @Nonnull NodeName name;
    public @CheckForNull ParameterTransformation parameterTransformation;
    public @Nonnull ParametersForHash parametersForHash;
    public @Nonnull List<ResponseConfiguration> success;
    public @Nonnull ResponseConfiguration error;
    public final @Nonnull List<Task> tasks = new ArrayList<>();

    @Override
    public @Nonnull Set<NodeName> getEndpointNames() {
        return Set.of(name);
    }

    @Override
    public @Nonnull Endpoint findEndpointOrThrow(@Nonnull NodeName name) throws NodeNotFoundException {
        if (name.equals(this.name)) return this;
        else throw new NodeNotFoundException(name);
    }

    @Override
    public void assertTemplatesValid() throws DocumentTemplateInvalidException { 
        if (parameterTransformation != null) 
            parameterTransformation.assertTemplatesValid();
        
        for (var s : success) s.assertTemplatesValid();
        error.assertTemplatesValid();
        
        for (var t : tasks) t.assertTemplatesValid();
    }
}
