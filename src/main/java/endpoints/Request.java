package endpoints;

import endpoints.EndpointExecutor.EndpointExecutionFailedException;
import endpoints.config.ParameterName;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.http.Cookie;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

/**
 * This basically models an HTTP request which is processed by endpoints.
 * This is not entirely accurate as this can be "faked" e.g. by embedding the software into a Wicket page, or unit test, etc.
 * Further, if e.g. a short link is used, the short link is first decoded, the Request object models the request saved
 * with the short link, it does not represent the parameterless request which was made to the shortlink servlet.
 */
public interface Request {
    
    @CheckForNull InetAddress getClientIpAddress();
    
    /** @return Keys are lowercase (as HTTP headers are case-insensitive) */
    @Nonnull Map<String, List<String>> getLowercaseHttpHeadersWithoutCookies();

    @Nonnull List<Cookie> getCookies();

    /** Can return empty string */
    @Nonnull String getUserAgent();

    @CheckForNull String getContentTypeIfPost();
    
    @Nonnull Map<ParameterName, List<String>> getParameters();
    
    @Nonnull List<? extends UploadedFile> getUploadedFiles();
    
    @Nonnull InputStream getInputStream() throws EndpointExecutionFailedException;
    
    public static @Nonnull Request newForTesting() {
        return new Request() {
            @Override public InetAddress getClientIpAddress() { return null; }
            @Override public Map<String, List<String>> getLowercaseHttpHeadersWithoutCookies() { return Map.of(); }
            @Override public List<Cookie> getCookies() { return List.of(); }
            @Override public String getUserAgent() { return ""; }
            @Override public String getContentTypeIfPost() { return null; } 
            @Override public Map<ParameterName, List<String>> getParameters() { return Map.of(); }
            @Override public List<? extends UploadedFile> getUploadedFiles() { return List.of(); }
            @Override public InputStream getInputStream() { throw new IllegalStateException(); }
        };
    }
}
