package endpoints;

import com.databasesandlife.util.servlet.IpAddressDeterminer;
import endpoints.EndpointExecutor.EndpointExecutionFailedException;
import endpoints.config.ParameterName;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.*;

import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * Represents a HTTP request in the form of a {@link Request} which can be processed by {@link EndpointExecutor}.
 */
@RequiredArgsConstructor
public class ServletRequest implements Request {
    
    protected final @Nonnull HttpServletRequest req;

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
    
    @Override public @CheckForNull String getContentTypeIfPost() {
        return Optional.ofNullable(req.getContentType())
            .map(x -> x.replaceAll(";.*$", ""))
            .orElse(null);
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
    @Override public @Nonnull List<UploadedFile> getUploadedFiles() {
        return Optional.ofNullable(req.getContentType()).orElse("").startsWith("multipart/form-data")
            ? req.getParts().stream().filter(p -> p.getContentType() != null).map(EndpointExecutorServlet.ServletUploadedFile::new).collect(toList())
            : List.of();
    }
    
    @Override public @Nonnull InputStream getInputStream() throws EndpointExecutionFailedException {
        try { return req.getInputStream(); }
        catch (IOException e) { throw new EndpointExecutionFailedException(400, "I/O problem reading request", e); }
    }
}
