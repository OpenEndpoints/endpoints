package endpoints.task;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.TemporaryFile;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.config.IntermediateValueName;
import junit.framework.TestCase;
import lombok.Getter;
import lombok.SneakyThrows;
import org.postgresql.util.ReaderInputStream;

import javax.annotation.Nonnull;
import java.io.InputStream;
import java.io.StringReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Map;

public class HttpRequestTaskTest extends TestCase {
    
    protected static class Connection extends URLConnection {
        protected final @Getter @Nonnull String contentType;
        protected final @Nonnull String content;
        
        @SneakyThrows(MalformedURLException.class)
        protected static URL url() { 
            return new URL("http://www.unit-test.com/"); 
        }
        
        public Connection(@Nonnull String contentType, @Nonnull String content) {
            super(url());
            this.contentType = contentType;
            this.content = content;
        }
        
        @Override public void connect() { }

        @Override public InputStream getInputStream() {
            return new ReaderInputStream(new StringReader(content));
        }
    }
    
    @SuppressWarnings("StringBufferReplaceableByString")
    protected @Nonnull HttpRequestTask newTask(String extraXml, boolean ignoreErrors) throws ConfigurationException {
        try (var xsltDir = new TemporaryFile("foo", "bar")) {
            var xml = new StringBuilder();
            xml.append("<task ").append(ignoreErrors ? "ignore-if-error='true'" : "").append(">");
            xml.append("  <url>http://www.unit-test.com</url>");
            xml.append(extraXml);
            xml.append("</task>");
            
            var threads = new XsltCompilationThreads();
            var result = new HttpRequestTask(threads, xsltDir.file, Map.of(), xsltDir.file, DomParser.from(xml.toString()));
            threads.execute();
            
            return result;
        }
    }
    
    protected @Nonnull String output(String desc) {
        return "<output-intermediate-value name='var' regex='\\w+' " + desc + "/>";
    }

    public void testParseResults() throws Exception {
        // No output params required, so weird response is OK
        newTask("", false).parseResults(Map.of(), 
            new Connection("text/plain", "foo"));
        
        // No output params required, ignore on error is OK
        newTask("", true).parseResults(Map.of(),
            new Connection("text/plain", "foo"));

        // Output params required, ignore error is NOT OK
        try {
            newTask(output("xpath='foo'"), true).parseResults(Map.of(),
                new Connection("text/plain", "foo"));
            fail();
        }
        catch (ConfigurationException e) {
            assertTrue(e.getMessage().contains("ignore-if-error") && e.getMessage().contains("intermediate"));
        }

        // XPath and JSON response, NOT OK
        try {
            newTask(output("xpath='foo'"), false).parseResults(Map.of(),
                new Connection("application/json", "{}"));
            fail();
        }
        catch (RuntimeException e) {
            assertTrue(e.getCause().getMessage().contains("JSON,") && e.getCause().getMessage().contains("XPath"));
        }
        
        // XPath is not syntactically valid, not OK
        try {
            newTask(output("xpath='['"), false).parseResults(Map.of(),
                new Connection("text/xml", "<foo/>"));
            fail();
        }
        catch (ConfigurationException ignored) { }

        // XML is not valid, NOT OK
        try {
            newTask(output("xpath='foo'"), false).parseResults(Map.of(),
                new Connection("text/xml", "not xml"));
            fail();
        }
        catch (RuntimeException e) {
            assertTrue(e.getCause().getMessage().contains("XML"));
        }

        // XPath is found in XML, regex doesn't match, NOT OK
        try {
            newTask(output("xpath='/element/text()'"), false).parseResults(Map.of(),
                new Connection("text/xml", "<element>!</element>"));
            fail();
        }
        catch (RuntimeException e) {
            assertTrue(e.getCause().getMessage().contains("regex"));
        }

        // XPath is found in XML, OK
        {
            var results = new HashMap<IntermediateValueName, String>();
            newTask(output("xpath='/element/text()'"), false).parseResults(results,
                new Connection("text/xml", "<element>value</element>"));
            assertEquals("value", results.get(new IntermediateValueName("var")));
        }

        // JSONPath and XML response, NOT OK
        try {
            newTask(output("jsonpath='$.foo'"), false).parseResults(Map.of(),
                new Connection("text/xml", "<foo/>"));
            fail();
        }
        catch (RuntimeException e) {
            assertTrue(e.getCause().getMessage().contains("XML,") && e.getCause().getMessage().contains("JSONPath"));
        }

        // JSONPath is not syntactically valid, NOT OK
        try {
            newTask(output("jsonpath='['"), false).parseResults(Map.of(),
                new Connection("application/json", "{ \"text\": \"value\" }"));
            fail();
        }
        catch (ConfigurationException ignored) { }

        // JSON is not valid, NOT OK
        try {
            newTask(output("jsonpath='$.foo'"), false).parseResults(Map.of(),
                new Connection("application/json", "not json"));
            fail();
        }
        catch (RuntimeException e) {
            assertTrue(e.getCause().getMessage().contains("JSON"));
        }

        // JSONPath not found in JSON, NOT OK
        try {
            newTask(output("jsonpath='$.notfound'"), false).parseResults(Map.of(),
                new Connection("application/json", "{ \"text\": \"value\" }"));
            fail();
        }
        catch (RuntimeException e) {
            assertTrue(e.getCause().getMessage().contains("JSONPath not found"));
        }

        // JSONPath found in JSON, doesn't match regex, NOT OK
        try {
            newTask(output("jsonpath='$.text'"), false).parseResults(Map.of(),
                new Connection("application/json", "{ \"text\": \"!\" }"));
            fail();
        }
        catch (RuntimeException e) {
            assertTrue(e.getCause().getMessage().contains("regex"));
        }

        // JSONPath is found in JSON, OK
        {
            var results = new HashMap<IntermediateValueName, String>();
            newTask(output("jsonpath='$.text'"), false).parseResults(results,
                new Connection("application/json", "{ \"text\": \"value\" }"));
            assertEquals("value", results.get(new IntermediateValueName("var")));
        }
    }
}