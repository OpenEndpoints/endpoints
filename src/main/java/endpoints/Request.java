package endpoints;

import endpoints.EndpointExecutor.EndpointExecutionFailedException;
import endpoints.config.ParameterName;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;

public interface Request {
    
    @CheckForNull InetAddress getClientIpAddress();
    
    /** Can return empty string */
    @Nonnull String getUserAgent();
    
    /** Can return empty string */
    @Nonnull String getReferrer();

    @CheckForNull String getContentTypeIfPost();
    
    @Nonnull Map<ParameterName, List<String>> getParameters();
    
    @Nonnull List<? extends UploadedFile> getUploadedFiles();
    
    @Nonnull InputStream getInputStream() throws EndpointExecutionFailedException;
    
    public static @Nonnull Request newForTesting() {
        return new Request() {
            @Override public InetAddress getClientIpAddress() { return null; }
            @Override public String getUserAgent() { return ""; }
            @Override public String getReferrer() { return ""; }
            @Override public String getContentTypeIfPost() { return null; } 
            @Override public Map<ParameterName, List<String>> getParameters() { return Map.of(); }
            @Override public List<? extends UploadedFile> getUploadedFiles() { return List.of(); }
            @Override public InputStream getInputStream() { throw new IllegalStateException(); }
        };
    }
}
