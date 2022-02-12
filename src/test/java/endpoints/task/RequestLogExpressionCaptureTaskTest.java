package endpoints.task;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.config.Endpoint;
import endpoints.config.EndpointHierarchyFolderNode;
import endpoints.config.EndpointHierarchyNode;
import endpoints.config.NodeName;
import endpoints.config.response.EmptyResponseConfiguration;
import endpoints.config.response.ForwardToEndpointResponseConfiguration;
import junit.framework.TestCase;
import org.apache.commons.lang.StringUtils;

import java.util.List;
import java.util.Map;

public class RequestLogExpressionCaptureTaskTest extends TestCase {

    public void testAssertUniqueCaptureKeys() throws Exception{
        var endpoint2a = new Endpoint();
        endpoint2a.name = new NodeName("endpoint2a");
        endpoint2a.tasks.add(RequestLogExpressionCaptureTask.newForTesting("bar", ""));
        endpoint2a.success = List.of();
        endpoint2a.error = EmptyResponseConfiguration.newForTesting();

        var endpoint2b = new Endpoint();
        endpoint2b.name = new NodeName("endpoint2b");
        endpoint2b.tasks.add(RequestLogExpressionCaptureTask.newForTesting("bar", ""));
        endpoint2b.success = List.of();
        endpoint2b.error = EmptyResponseConfiguration.newForTesting();

        var endpoint1 = new Endpoint();
        endpoint1.name = new NodeName("endpoint1");
        endpoint1.tasks.add(RequestLogExpressionCaptureTask.newForTesting("foo", ""));
        endpoint1.success = List.of(ForwardToEndpointResponseConfiguration.newForTesting(endpoint2a.name));
        endpoint1.error = ForwardToEndpointResponseConfiguration.newForTesting(endpoint2b.name);
        
        var folder = new EndpointHierarchyFolderNode();
        folder.children = new EndpointHierarchyNode[] { endpoint1, endpoint2a, endpoint2b };

        // endpoint2a and 2b both use "bar" but they are different paths, that's OK 
        RequestLogExpressionCaptureTask.assertUniqueCaptureKeys(folder.getEndpointForName(), Map.of(), endpoint1);

        // Conflict across different endpoints
        endpoint2a.tasks.add(RequestLogExpressionCaptureTask.newForTesting("foo", ""));
        try { 
            RequestLogExpressionCaptureTask.assertUniqueCaptureKeys(folder.getEndpointForName(), Map.of(), endpoint1);
            fail();
        }
        catch (ConfigurationException e) {
            // Check the number of occurrences in the err msg because swe don't want 'endpoint1' -> 'endpoint1' texts in msg
            assertEquals(1, StringUtils.countMatches(e.getMessage(), "endpoint1"));
            assertEquals(1, StringUtils.countMatches(e.getMessage(), "endpoint2a"));
        }

        // Conflict across one endpoints
        endpoint1.tasks.add(RequestLogExpressionCaptureTask.newForTesting("foo", ""));
        try {
            RequestLogExpressionCaptureTask.assertUniqueCaptureKeys(folder.getEndpointForName(), Map.of(), endpoint1);
            fail();
        }
        catch (ConfigurationException e) {
            // Endpoint2a is not there because we already fail when checking endpoint1
            assertEquals(1, StringUtils.countMatches(e.getMessage(), "endpoint1"));
            assertEquals(0, StringUtils.countMatches(e.getMessage(), "endpoint2a"));
        }
    }
}