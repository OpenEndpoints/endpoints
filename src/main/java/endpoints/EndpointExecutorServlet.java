package endpoints;

import com.databasesandlife.util.Timer;
import com.databasesandlife.util.servlet.IpAddressDeterminer;
import endpoints.EndpointExecutor.EndpointExecutionFailedException;
import endpoints.EndpointExecutor.RequestInvalidException;
import endpoints.PublishEnvironment.PublishEnvironmentNotFoundException;
import endpoints.config.*;
import endpoints.config.ApplicationFactory.ApplicationNotFoundException;
import endpoints.config.EndpointHierarchyNode.NodeNotFoundException;
import endpoints.shortlinktoendpoint.ShortLinkToEndpointExpiryJob;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.lang.StringEscapeUtils;
import org.slf4j.LoggerFactory;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@SuppressWarnings("serial")
public class EndpointExecutorServlet extends AbstractEndpointsServlet {
    
    @RequiredArgsConstructor
    protected static class ServletUploadedFile extends UploadedFile {
        final @Nonnull Part part;
        
        @Override public @Nonnull String getFieldName() { return part.getName(); }
        @Override public @Nonnull String getContentType() { return part.getContentType(); }
        @SneakyThrows(IOException.class)
        @Override public @Nonnull InputStream getInputStream() { return part.getInputStream(); }
        @Override public @CheckForNull String getSubmittedFileName() {
            if ("".equals(part.getSubmittedFileName())) throw new RuntimeException("Filename for file upload field '" 
                + getFieldName() + "' may be missing or present, but may not be empty");
            return part.getSubmittedFileName(); 
        }
    }
    
    protected void logParamsForDebugging(@Nonnull HttpServletRequest servletRequest, @Nonnull Request request) {
        if ( ! DeploymentParameters.get().xsltDebugLog) return;
        
        var log = LoggerFactory.getLogger(getClass());
        
        log.info("Request class: " + servletRequest.getClass());
        log.info("Part class: " + request.getUploadedFiles().stream()
            .map(x -> ((ServletUploadedFile)x).part.getClass().toString()).findAny().orElse("(no uploads)"));
        log.info("Request class loader: " + servletRequest.getClass().getClassLoader().getName());
        
        for (var e : request.getParameters().entrySet())
            for (var v : e.getValue())
                log.info(String.format(Locale.ENGLISH, "Parameter: key='%s', length %,d chars, (escaped) = \"%s\"",
                    e.getKey().name, v.length(), StringEscapeUtils.escapeJava(v.substring(0, Math.min(1000, v.length())))));

        for (var e : request.getUploadedFiles()) {
            var bytes = e.toByteArray();

            var hexString = new StringBuilder();
            var length = bytes.length;
            if (length > 10) length = 10;
            for (int i=0;i<length;i++) {
                String x = "0" + Integer.toHexString(0xFF & bytes[i]);
                hexString.append(x.substring(x.length() - 2));
            }

            log.info(String.format(Locale.ENGLISH, "Uploaded file: field='%s', size %,d bytes, first bytes (hex) = %s",
                e.getFieldName(), bytes.length, hexString.toString()));
        }
    }

    @Override public void init() throws ServletException {
        super.init();
        new ShortLinkToEndpointExpiryJob().start();
        new ExpireRequestLogDailyJob().start();
    }

    @Override
    protected void doPost(@Nonnull HttpServletRequest req, @Nonnull HttpServletResponse resp)
    throws IOException {
//        System.out.println("***********************");
//        System.out.println("REQUEST BODY:");
//        var byteStream = new ByteArrayOutputStream();
//        IOUtils.copy(req.getInputStream(), byteStream);
//        var bytes = byteStream.toByteArray();
//        StringBuffer hexString = new StringBuffer();
//        for (int i=0;i<bytes.length;i++) {
//            String x = "0" + Integer.toHexString(0xFF & bytes[i]);
//            hexString.append(x.substring(x.length() - 2));
//        }
//        System.out.println(hexString.toString());
//        System.out.println("***********************");

        setCorsHeaders(req, resp);

        try (var ignored = new Timer(getClass().getSimpleName())) {
            var path = req.getRequestURI().substring(req.getContextPath().length());
            var m = Pattern.compile("/([\\w-]+)/([\\w-]+)").matcher(path);
            if ( ! m.matches()) throw new RequestInvalidException("Cannot understand URL '"+path+"', should be /<application>/<endpoint>");
            @CheckForNull var envName = req.getParameter("environment");
            var applicationName = new ApplicationName(m.group(1));
            var endpointName = new NodeName(m.group(2));
        
            final PublishEnvironment environment;
            final Application application;
            final Endpoint endpoint;
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                environment = PublishEnvironment.parseOrDefault(envName);
                application = DeploymentParameters.get().getApplications(tx).getApplication(tx, applicationName, environment);
                endpoint = application.getEndpoints().findEndpointOrThrow(endpointName);
                tx.commit();
            }
            catch (PublishEnvironmentNotFoundException e) { 
                resp.sendError(400, "Environment '"+envName+"' not found"); 
                return; 
            }
            catch (ApplicationNotFoundException e) {
                var envLog = (envName == null || envName.equals(PublishEnvironment.getDefault().name()))
                    ? "" : " on "+envName+" environment";
                resp.sendError(400, "Application '"+applicationName.name+"' not found"+envLog);
                return;
            }
            catch (NodeNotFoundException e) { 
                resp.sendError(400, "Endpoint '" + endpointName.name +"' not found in this application");
                return; 
            }

            var request = new ServletRequest(req);
            
            logParamsForDebugging(req, request);
                
            var suppliedHash = req.getParameter("hash");

            new EndpointExecutor().execute(environment, applicationName, application, endpoint,
                Boolean.parseBoolean(req.getParameter("debug")),
                suppliedHash, request, responseContent -> responseContent.deliver(resp));
        }
        catch (RequestInvalidException e) {
            LoggerFactory.getLogger(getClass()).error("Request invalid", e);
            resp.sendError(400, "Request invalid: " + e.getMessage());
        }
        catch (Exception e) { 
            LoggerFactory.getLogger(getClass()).error("An internal error occurred", e);
            resp.sendError(500, "An internal error occurred");
        }
    }
    
    @Override 
    protected void doGet(@Nonnull HttpServletRequest req, @Nonnull HttpServletResponse resp) throws IOException {
        doPost(req, resp);
    }
}
