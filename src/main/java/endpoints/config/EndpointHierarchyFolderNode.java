package endpoints.config;

import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Set;

public class EndpointHierarchyFolderNode extends EndpointHierarchyNode {
    
    public @Nonnull EndpointHierarchyNode[] children;

    @Override
    public @Nonnull Set<NodeName> getEndpointNames() {
        var result = new HashSet<NodeName>();
        for (var c : children) result.addAll(c.getEndpointNames());
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
