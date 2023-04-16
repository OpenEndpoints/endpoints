package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.config.response.EmptyResponseConfiguration;
import endpoints.config.response.ForwardToEndpointResponseConfiguration;
import junit.framework.TestCase;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import java.util.List;

import static endpoints.config.EndpointHierarchyParser.assertEndpointForwardsExitAndNoCircularReferences;

public class EndpointHierarchyParserTest extends TestCase {
    
    protected @Nonnull Endpoint newEndpoint(@Nonnull String name, @CheckForNull String forwardTo) throws Exception {
        Endpoint result = new Endpoint();
        result.name = new NodeName(name);
        result.success = List.of(forwardTo == null ? EmptyResponseConfiguration.newForTesting() 
            : ForwardToEndpointResponseConfiguration.newForTesting(new NodeName(forwardTo)));
        return result;
    }

    public void testEndpointForwardsExitAndNoCircularReferences() throws Exception {
        EndpointHierarchyFolderNode root = new EndpointHierarchyFolderNode();
        
        // Test: OK
        root.children = new EndpointHierarchyNode[] {
            newEndpoint("a", null),
            newEndpoint("b", "a")
        };
        assertEndpointForwardsExitAndNoCircularReferences(root);

        // Test: Doesn't exist
        root.children = new EndpointHierarchyNode[] {
            newEndpoint("a", null),
            newEndpoint("b", "doesnt-exist")
        };
        try { assertEndpointForwardsExitAndNoCircularReferences(root); }
        catch (ConfigurationException e) { assertTrue(e.getMessage().contains("doesnt-exist")); }

        // Test: Circular
        root.children = new EndpointHierarchyNode[] {
            newEndpoint("a", "b"),
            newEndpoint("b", "a")
        };
        try { assertEndpointForwardsExitAndNoCircularReferences(root); }
        catch (ConfigurationException e) { assertTrue(e.getMessage().contains("'a' -> 'b'")); }
    }
}
