package endpoints;

import com.databasesandlife.util.servlet.IpAddressDeterminer;
import endpoints.EndpointExecutor.EndpointExecutionFailedException;
import endpoints.config.ParameterName;
import lombok.Getter;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Represents a HTTP request in the form of a {@link Request} which can be processed by {@link EndpointExecutor}.
 */
public class ServletRequest implements Request {
    
    protected final @Nonnull HttpServletRequest req;
    protected final @Nonnull byte[] requestBody;

    public ServletRequest(@Nonnull HttpServletRequest req) throws EndpointExecutionFailedException {
        this.req = req;
        
        // We have to support the body being read multiple times, firstly for processing, and secondly for the request log
        try { requestBody = IOUtils.toByteArray(req.getInputStream()); }
        catch (IOException e) { throw new EndpointExecutionFailedException(400, "I/O problem reading request", e); }
    }

    @Override public @CheckForNull InetAddress getClientIpAddress() {
        return new IpAddressDeterminer().getRequestIpAddress(req);
    }

    @Override public @Nonnull Map<String, List<String>> getLowercaseHttpHeadersWithoutCookies() {
        var result = new HashMap<String, List<String>>();
        for (var e = req.getHeaderNames(); e.hasMoreElements(); ) {
            var name = e.nextElement().toLowerCase();
            if (name.equals("cookie")) continue;
            var values = new ArrayList<String>();
            for (var v = req.getHeaders(name); v.hasMoreElements(); ) values.add(v.nextElement());
            result.put(name, values);
        }
        return result;
    }

    @Override public @Nonnull List<Cookie> getCookies() {
        return Optional.ofNullable(req.getCookies()).map(Arrays::asList).orElse(List.of());
    }

    @Override public @Nonnull String getUserAgent() {
        return Optional.ofNullable(req.getHeader("User-Agent")).orElse("");
    }
    
    @Override public @CheckForNull RequestBody getRequestBodyIfPost() {
        if (req.getContentType() == null) return null;
        
        var contentType = req.getContentType().replaceAll(";.*$", "");
        return new RequestBody(contentType, requestBody);
    }
    
    @Override public @Nonnull Map<ParameterName, List<String>> getParameters() {
        return req.getParameterMap().entrySet().stream()
            .filter(e -> ! e.getKey().equalsIgnoreCase("debug"))
            .collect(toMap(
                e -> new ParameterName(e.getKey()),
                e -> List.of(e.getValue())
            ));
    }
    
    @SneakyThrows({ServletException.class, IOException.class})
    @Override public @Nonnull List<? extends UploadedFile> getUploadedFiles() {
        return Optional.ofNullable(req.getContentType()).orElse("").startsWith("multipart/form-data")
            ? req.getParts().stream()
                .filter(p -> p.getContentType() != null)
                .map(EndpointExecutorServlet.ServletUploadedFile::new)
                .toList()
            : List.of();
    }
}
