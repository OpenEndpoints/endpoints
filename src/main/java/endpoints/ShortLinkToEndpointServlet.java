package endpoints;

import com.databasesandlife.util.Timer;
import com.databasesandlife.util.servlet.IpAddressDeterminer;
import endpoints.EndpointExecutor.RequestInvalidException;
import endpoints.config.ApplicationFactory.ApplicationNotFoundException;
import endpoints.config.EndpointHierarchyNode.NodeNotFoundException;
import endpoints.config.ParameterName;
import org.apache.log4j.Logger;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static endpoints.generated.jooq.Tables.SHORT_LINK_TO_ENDPOINT;
import static endpoints.generated.jooq.Tables.SHORT_LINK_TO_ENDPOINT_PARAMETER;
import static java.util.stream.Collectors.toMap;

@SuppressWarnings("serial")
public class ShortLinkToEndpointServlet extends AbstractEndpointsServlet {
    
    @Override
    protected void doGet(@Nonnull HttpServletRequest req, @Nonnull HttpServletResponse resp)
    throws IOException {
        setCorsHeaders(req, resp);

        var path = req.getRequestURI().substring(req.getContextPath().length());
        
        try (var tx = DeploymentParameters.get().newDbTransaction(); 
             var ignored = new Timer(getClass().getSimpleName() + " " + path)) {
            var m = Pattern.compile("/shortlink/(\\w+)").matcher(path);
            if ( ! m.matches()) throw new RequestInvalidException("URL '" + path + "' malformed");

            var code = new ShortLinkToEndpointCode(m.group(1));
            var shortLink = tx.jooq().fetchOne(SHORT_LINK_TO_ENDPOINT, SHORT_LINK_TO_ENDPOINT.SHORT_LINK_TO_ENDPOINT_CODE.eq(code));
            if (shortLink == null) {
                Logger.getLogger(getClass()).error("Code '" + code.getCode() + "' not found");
                resp.sendError(404, "Code '" + code.getCode() + "' not found");
                return;
            }

            var params = tx.jooq()
                .selectFrom(SHORT_LINK_TO_ENDPOINT_PARAMETER)
                .where(SHORT_LINK_TO_ENDPOINT_PARAMETER.SHORT_LINK_TO_ENDPOINT_CODE.eq(code))
                .fetchMap(SHORT_LINK_TO_ENDPOINT_PARAMETER.PARAMETER_NAME, SHORT_LINK_TO_ENDPOINT_PARAMETER.PARAMETER_VALUE);

            var application = DeploymentParameters.get().getApplications(tx).getApplication(
                tx, shortLink.getApplication(), shortLink.getEnvironment());
            var endpoint = application.getEndpoints().findEndpointOrThrow(shortLink.getEndpoint());

            var request = new Request() {
                @Override public @CheckForNull InetAddress getClientIpAddress() { 
                    return new IpAddressDeterminer().getRequestIpAddress(req); 
                }
                @Override public @Nonnull String getUserAgent() {
                    return Optional.ofNullable(req.getHeader("User-Agent")).orElse("");
                }
                @Override public @Nonnull String getReferrer() {
                    return Optional.ofNullable(req.getHeader("Referer")).orElse("");
                }
                @Override public @CheckForNull String getContentTypeIfPost() { return null; }
                @Override public @Nonnull Map<ParameterName, List<String>> getParameters() {
                    return params.entrySet().stream().collect(toMap(r -> r.getKey(), r -> List.of(r.getValue())));
                }
                @Override public @Nonnull List<UploadedFile> getUploadedFiles() { return List.of(); }
                @Override public @Nonnull InputStream getInputStream() { throw new IllegalStateException(); }
            };
            
            new EndpointExecutor().execute(shortLink.getEnvironment(), shortLink.getApplication(), application, endpoint,
                Boolean.parseBoolean(req.getParameter("debug")),
                null, request, responseContent -> responseContent.deliver(resp));
            
            tx.commit();
        }
        catch (ApplicationNotFoundException e) {
            resp.sendError(400, "Application specified in short link not found " +
                "or not published on the environment specified in this short link");
        }
        catch (NodeNotFoundException e) {
            resp.sendError(400, "Endpoint specified in this short link not found in the application");
        }
        catch (RequestInvalidException e) {
            Logger.getLogger(getClass()).error("Request invalid", e);
            resp.sendError(400, "Request invalid: " + e.getMessage());
        }
        catch (Exception e) { 
            Logger.getLogger(getClass()).error("An internal error occurred", e);
            resp.sendError(500, "An internal error occurred");
        }
    }
}
