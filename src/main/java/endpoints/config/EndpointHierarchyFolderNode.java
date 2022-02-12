package endpoints.config;

import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;

import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;

public class EndpointHierarchyFolderNode extends EndpointHierarchyNode {
    
    public @Nonnull EndpointHierarchyNode[] children;

    @Override
    public @Nonnull Map<NodeName, Endpoint> getEndpointForName() {
        var result = new HashMap<NodeName, Endpoint>();
        for (var c : children) result.putAll(c.getEndpointForName());
        return result;
    }

    @Override
    public @Nonnull Endpoint findEndpointOrThrow(@Nonnull NodeName name) throws NodeNotFoundException {
        for (var child : children) {
            try { return child.findEndpointOrThrow(name); }
            catch (NodeNotFoundException ignored) { }
        }
        
        throw new NodeNotFoundException(name);
    }
    
    @Override
    public void assertTemplatesValid() throws DocumentTemplateInvalidException {
        for (var child : children) child.assertTemplatesValid();
    }
}
