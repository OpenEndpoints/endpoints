package endpoints;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.IOException;
import java.io.InputStream;

public abstract class UploadedFile {
    
    protected boolean xmlParsingAttempted = false;
    protected @CheckForNull Element xmlDocumentOrNull;
    
    public abstract @Nonnull String getFieldName();
    
    public abstract @Nonnull String getContentType();

    /**
     * This is safe to call multiple times.
     * @implNote Both Wicket and Servlet are based on Jetty, which reads and stores the entire content when preparing the request.
     */
    public abstract @Nonnull InputStream getInputStream();
    
    /** 
     * @return null means that no filename was submitted. Will not be empty.
     */
    public abstract @CheckForNull String getSubmittedFileName();

    /** 
     * @implNote Note that this creates a copy of the byte buffer each time it's called 
     */
    @SneakyThrows(IOException.class)
    public @Nonnull byte[] toByteArray() {
        return getInputStream().readAllBytes();
    }
    
    public synchronized @CheckForNull Element getXmlDocumentOrNull() {
        if (! xmlParsingAttempted) {
            try { xmlDocumentOrNull = DomParser.from(getInputStream()); }
            catch (ConfigurationException ignored) { xmlDocumentOrNull = null; }
            
            xmlParsingAttempted = true;
        }
        return xmlDocumentOrNull;
    }
}
