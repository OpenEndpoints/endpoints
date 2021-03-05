package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.PublishEnvironment;
import endpoints.config.EndpointHierarchyNode.NodeNotFoundException;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointContentMenuItem;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointFormMenuItem;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointMenuFolder;
import endpoints.config.response.ResponseConfiguration;
import endpoints.config.response.TransformationResponseConfiguration;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;

import static java.util.Collections.emptyList;

public class ServicePortalEndpointMenuItemsParser extends DomParser {

    protected @Nonnull Set<PublishEnvironment> parseEnvironments(@Nonnull Element el) throws ConfigurationException {
        var subElements = getSubElements(el, "environment");
        if (subElements.isEmpty()) {
            return EnumSet.allOf(PublishEnvironment.class);
        } else {
            try {
                var result = EnumSet.noneOf(PublishEnvironment.class);
                for (var env : subElements) result.add(PublishEnvironment.valueOf(getMandatoryAttribute(env, "name")));
                return result;
            }
            catch (IllegalArgumentException e) { throw new ConfigurationException("<environment>", e); }
        }
    }
    
    protected void assertResponseConfigurationAcceptable(@Nonnull String prefix, @Nonnull ResponseConfiguration r) 
    throws ConfigurationException {
        if ( ! (r instanceof TransformationResponseConfiguration)) 
            throw new ConfigurationException(prefix + "Must produce content (not be redirect or empty response)");
        
        var t = (TransformationResponseConfiguration) r;
        if (t.downloadFilenamePatternOrNull == null) {
            String contentType = t.transformer.defn.contentType;
            if (contentType == null) throw new ConfigurationException(prefix + "Content Type mandatory");
            if ( ! contentType.toLowerCase().contains("charset=utf-8") || ! contentType.toLowerCase().contains("text/html"))
                throw new ConfigurationException(prefix + "Content must be download, or have content type HTML and UTF-8, not '" + contentType + "'");
        }
    }
    
    protected @Nonnull NodeName findEndpoint(@Nonnull EndpointHierarchyFolderNode endpoints, @Nonnull NodeName name)
    throws ConfigurationException {
        try {
            var candidate = endpoints.findEndpointOrThrow(name);
            for (var successResponse : candidate.success)
                assertResponseConfigurationAcceptable("Endpoint '" + name.name + "': "+
                    successResponse.getHumanReadableId()+": ", successResponse);
            assertResponseConfigurationAcceptable("Endpoint '" + name.name + "': "+
                candidate.error.getHumanReadableId()+": ", candidate.error);
            return name;
        }
        catch (NodeNotFoundException e) { throw new ConfigurationException(e); }
    }
    
    protected @Nonnull List<ServicePortalEndpointMenuItem> parseChildren(
        @Nonnull EndpointHierarchyFolderNode endpoints, @Nonnull Element element
    ) throws ConfigurationException {
        assertNoOtherElements(element, "menu-folder", "content", "form", "environment");

        var result = new ArrayList<ServicePortalEndpointMenuItem>();
        for (var e : getSubElements(element, "menu-folder", "content", "form")) {
            String displayName = getMandatoryAttribute(e, "menu-item-name");
            final ServicePortalEndpointMenuItem newItem;
            switch (e.getTagName()) {
                case "content":
                    newItem = new ServicePortalEndpointContentMenuItem(displayName, parseEnvironments(e),
                        findEndpoint(endpoints, new NodeName(getMandatoryAttribute(e, "endpoint"))));
                    break;
                case "form":
                    newItem = new ServicePortalEndpointFormMenuItem(displayName, parseEnvironments(e),
                        findEndpoint(endpoints, new NodeName(getMandatoryAttribute(e, "form-endpoint"))),
                        findEndpoint(endpoints, new NodeName(getMandatoryAttribute(e, "result-endpoint"))));
                    break;
                case "menu-folder":
                    newItem = new ServicePortalEndpointMenuFolder(displayName, parseEnvironments(e),
                        parseChildren(endpoints, e));
                    break;
                default:
                    throw new RuntimeException(e.getTagName());
            }
            result.add(newItem);
        }
        return result;
    }
    
    public @Nonnull ServicePortalEndpointMenuFolder parse(@Nonnull EndpointHierarchyFolderNode endpoints, @Nonnull File file)
    throws ConfigurationException {
        try {
            if (! file.exists()) return new ServicePortalEndpointMenuFolder("Root", 
                EnumSet.allOf(PublishEnvironment.class), emptyList());
                
            var rootElement = from(file);
            if ( ! "service-portal-endpoint-menu-items".equals(rootElement.getTagName()))
                throw new ConfigurationException("Root element must be <service-portal-endpoint-menu-items>");
            
            return new ServicePortalEndpointMenuFolder(
                "Root", // Not displayed to user 
                parseEnvironments(rootElement),
                parseChildren(endpoints, rootElement));
        }
        catch (ConfigurationException e) { throw new ConfigurationException(file.getAbsolutePath(), e); }
    }
}
