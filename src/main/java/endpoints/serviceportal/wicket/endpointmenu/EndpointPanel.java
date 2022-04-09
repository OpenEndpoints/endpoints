package endpoints.serviceportal.wicket.endpointmenu;

import com.databasesandlife.util.servlet.IpAddressDeterminer;
import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.*;
import endpoints.EndpointExecutor.EndpointExecutionFailedException;
import endpoints.config.ApplicationFactory.ApplicationNotFoundException;
import endpoints.config.EndpointHierarchyNode.NodeNotFoundException;
import endpoints.config.NodeName;
import endpoints.config.ParameterName;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import lombok.SneakyThrows;
import org.apache.wicket.request.resource.ByteArrayResource;
import org.slf4j.LoggerFactory;
import org.apache.wicket.feedback.ContainerFeedbackMessageFilter;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ResourceLink;
import org.apache.wicket.markup.html.panel.Panel;
import org.apache.wicket.protocol.http.servlet.ServletWebRequest;
import org.apache.wicket.request.cycle.RequestCycle;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.http.Cookie;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;

public class EndpointPanel extends Panel {

    protected final @Nonnull PublishEnvironment environment;
    protected final @Nonnull NodeName endpointName;

    // There are the following states: not executed, error, HTML snippet, download
    protected boolean executed = false;
    /** For download */ protected @CheckForNull String downloadFileName = null;
    /** For download or HTML snippet */ protected @CheckForNull String contentType = null;
    /** For download or HTML snippet */ protected @CheckForNull byte[] body = null;

    public EndpointPanel(@Nonnull String wicketId, @Nonnull PublishEnvironment environment, @Nonnull NodeName endpoint) {
        super(wicketId);
        this.environment = environment;
        this.endpointName = endpoint;
        
        // Error message
        add(new ServicePortalFeedbackPanel("feedback", new ContainerFeedbackMessageFilter(this)));
        
        // HTML snippet content
        add(new Label("endpointContent", () -> {
            if ( ! executed) return "(not executed yet)";
            if (body == null) return "";                // error case
            if (downloadFileName != null) return "";    // download case
            if ( ! contentType.toLowerCase().matches(".*utf-?8.*")) return "(content is not UTF-8)";
            if ( ! contentType.toLowerCase().contains("text/html")) return "(content is not HTML)";
            return new String(body, UTF_8);
        }).setEscapeModelStrings(false));
        
        // Download button
        var resource = new ByteArrayResource(null) {
            @Override protected void configureResponse(ResourceResponse response, Attributes attributes) {
                super.configureResponse(response, attributes);
                response.setContentType(contentType);
            }

            @Override protected String getFilename() { return downloadFileName; }
            @Override protected byte[] getData(Attributes attributes) { return body; }
        };
        add(new ResourceLink<Void>("download", resource) {
            @Override public boolean isVisible() {
                return downloadFileName != null;
            }
        });
    }
    
    @SneakyThrows({ApplicationNotFoundException.class, NodeNotFoundException.class})
    public @Nonnull EndpointPanel execute(
        @Nonnull DbTransaction tx, 
        @Nonnull Map<ParameterName, List<String>> params, @Nonnull List<? extends UploadedFile> uploadedFiles
    ) {
        try {
            executed = true;
            downloadFileName = null;
            contentType = null;
            body = null;

            var applicationName = ServicePortalSession.get().getLoggedInApplicationDataOrThrow().application;
            var application = DeploymentParameters.get().getApplications(tx).getApplication(tx, applicationName, environment);
            var endpoint = application.getEndpoints().findEndpointOrThrow(endpointName);
            var request = new Request() {
                @Override public @CheckForNull InetAddress getClientIpAddress() {
                    return new IpAddressDeterminer().getRequestIpAddress(
                        (((ServletWebRequest) RequestCycle.get().getRequest()).getContainerRequest()));
                }
                @Override public @Nonnull Map<String, List<String>> getLowercaseHttpHeadersWithoutCookies() { return Map.of(); } 
                @Override public @Nonnull List<Cookie> getCookies() { return List.of(); }
                @Override public @Nonnull String getUserAgent() { return "Service Portal"; }
                @Override public @CheckForNull String getContentTypeIfPost() { return null; }
                @Override public @Nonnull Map<ParameterName, List<String>> getParameters() { return params; }
                @Override public @Nonnull List<? extends UploadedFile> getUploadedFiles() { return uploadedFiles; }
                @Override public @Nonnull InputStream getInputStream() { throw new IllegalStateException(); }
            };
            new EndpointExecutor().execute(environment, applicationName,
                DeploymentParameters.get().getApplications(tx).getApplication(tx, applicationName, environment), endpoint,
                false, null, request, 
                responseContent -> {
                    downloadFileName = responseContent.getFilenameOrNull();
                    contentType = responseContent.getContentType();
                    body = responseContent.getBody().toByteArray();
                });
        }
        catch (EndpointExecutionFailedException e) {
            LoggerFactory.getLogger(getClass()).warn("Error while generating EndpointPanel (displayed to user)", e);
            error(e.getMessage()); 
        }
        
        return this;
    }
    
    /** Convenience method */
    public @Nonnull EndpointPanel execute(@Nonnull DbTransaction tx) {
        return execute(tx, Map.of(), List.of());
    }
}
