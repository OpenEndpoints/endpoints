package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import endpoints.PlaintextParameterReplacer;
import lombok.AllArgsConstructor;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static endpoints.config.EndpointHierarchyParser.parseParameterMap;

/** Execute another endpoint and return its result */
public class ForwardToEndpointResponseConfiguration extends ResponseConfiguration {
    
    @Nonnull public NodeName endpoint;
    
    /** Null means that all parameters are forwarded */
    @CheckForNull public Map<ParameterName, String> inputParameterPatterns;

    public ForwardToEndpointResponseConfiguration(@Nonnull Element config, @Nonnull Element forwardToEndpointElement) 
    throws ConfigurationException {
        super(config);
        
        assertNoOtherElements(config, "forward-to-endpoint", "input-intermediate-value");
        try {
            assertNoOtherElements(forwardToEndpointElement, "input-parameter");
            endpoint = new NodeName(getMandatoryAttribute(forwardToEndpointElement, "endpoint-name"));
            
            var patterns = parseParameterMap(forwardToEndpointElement, "input-parameter", "name");
            if (patterns.isEmpty()) inputParameterPatterns = null;
            else inputParameterPatterns = patterns;
        }
        catch (ConfigurationException e) { throw new ConfigurationException("<forward-to-endpoint>", e); }

    }

    // For unit test
    public ForwardToEndpointResponseConfiguration(@Nonnull Element config, @Nonnull NodeName endpoint) throws ConfigurationException {
        super(config);
        this.endpoint = endpoint;
        this.inputParameterPatterns = null;
    }

    @Override public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        if (inputParameterPatterns != null)
            for (var e : inputParameterPatterns.entrySet())
                PlaintextParameterReplacer.assertParametersSuffice(params, inputIntermediateValues, e.getValue(), 
                    "<input-parameter name='"+e.getKey().name+"'>");
    } 
}
