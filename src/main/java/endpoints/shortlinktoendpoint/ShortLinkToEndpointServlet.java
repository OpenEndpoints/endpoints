package endpoints.shortlinktoendpoint;

import com.databasesandlife.util.Timer;
import endpoints.*;
import endpoints.EndpointExecutor.InvalidRequestException;
import endpoints.config.ApplicationFactory.ApplicationNotFoundException;
import endpoints.config.EndpointHierarchyNode.NodeNotFoundException;
import endpoints.config.ParameterName;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static endpoints.generated.jooq.Tables.SHORT_LINK_TO_ENDPOINT;
import static endpoints.generated.jooq.Tables.SHORT_LINK_TO_ENDPOINT_PARAMETER;
import static java.lang.Boolean.parseBoolean;
import static java.time.Instant.now;
import static java.util.stream.Collectors.toMap;

@Slf4j
public class ShortLinkToEndpointServlet extends AbstractEndpointsServlet {
    
    @Override
    protected void doGet(@Nonnull HttpServletRequest req, @Nonnull HttpServletResponse resp)
    throws IOException {
        setCorsHeaders(req, resp);

        var path = req.getRequestURI().substring(req.getContextPath().length());
        
        try (var tx = DeploymentParameters.get().newDbTransaction();
             var ignored = new Timer(getClass().getSimpleName() + " " + path)) {
            var m = Pattern.compile("/shortlink/(\\w+)").matcher(path);
            if ( ! m.matches()) throw new InvalidRequestException("URL '" + path + "' malformed");

            var code = new ShortLinkToEndpointCode(m.group(1));
            var shortLink = tx.jooq()
                .selectFrom(SHORT_LINK_TO_ENDPOINT)
                .where(SHORT_LINK_TO_ENDPOINT.SHORT_LINK_TO_ENDPOINT_CODE.eq(code))
                .and(SHORT_LINK_TO_ENDPOINT.EXPIRES_ON.gt(now()))
                .fetchOne();
            if (shortLink == null) {
                log.error("Code '" + code.getCode() + "' not found");
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

            var request = new ServletRequest(req) {
                @Override public @Nonnull Map<ParameterName, List<String>> getParameters() {
                    return params.entrySet().stream().collect(toMap(r -> r.getKey(), r -> List.of(r.getValue())));
                }
                @Override public RequestBody getRequestBodyIfPost() { return null; }
                @Override public @Nonnull List<UploadedFile> getUploadedFiles() { return List.of(); }
            };
            
            new EndpointExecutor().execute(shortLink.getEnvironment(), shortLink.getApplication(), application, endpoint,
                parseBoolean(req.getParameter("debug")), parseBoolean(req.getParameter("verbose")),
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
        catch (InvalidRequestException e) {
            log.error("Request invalid", e);
            resp.sendError(400, "Request invalid: " + e.getMessage());
        }
        catch (Exception e) { 
            log.error("An internal error occurred", e);
            resp.sendError(500, "An internal error occurred");
        }
    }
}
