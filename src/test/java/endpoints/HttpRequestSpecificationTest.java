package endpoints;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.TemporaryFile;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.ApplicationTransaction;
import endpoints.HttpRequestSpecification;
import endpoints.UploadedFile;
import endpoints.TransformationContext;
import endpoints.HttpRequestSpecification.HttpRequestFailedException;
import endpoints.config.Application;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import endpoints.datasource.TransformationFailedException;
import junit.framework.TestCase;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static com.databasesandlife.util.ThreadPool.unwrapException;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;

public class HttpRequestSpecificationTest extends TestCase {

    int port = 34895;

    @FunctionalInterface
    interface DeliverResponse {
        void deliverResponse(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response) 
        throws IOException, ConfigurationException;
    }

    @SneakyThrows({ConfigurationException.class, IOException.class})
    public Element runTest(boolean ignoreErrors, @Nonnull String configXml, @Nonnull DeliverResponse deliverResponse)
    throws HttpRequestFailedException, TransformationFailedException {
        try (var xsltDir = new TemporaryFile("xslt-files", "dir")) {
            var httpXslt = "" +
                "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
                "  <xsl:template match='/'>" +
                "    <xml-from-xslt/>" +
                "  </xsl:template>" +
                "</xsl:stylesheet>";
            if ( ! xsltDir.file.delete()) throw new RuntimeException();
            if ( ! xsltDir.file.mkdir()) throw new RuntimeException();
            var xsltFile = new File(xsltDir.file, "unit-test.xslt");
            FileUtils.writeStringToFile(xsltFile, httpXslt, StandardCharsets.UTF_8.name());

            var config =
                "<task class='endpoints.task.HttpRequestTask' "+(ignoreErrors?"ignore-if-error='true'":"")+">" +
                (configXml.contains("<url>") ? "" : "  <url>http://localhost:"+port+"/</url>") +
                configXml +
                "</task>";
            var threads = new XsltCompilationThreads();
            var configElement = DomParser.from(new ByteArrayInputStream(config.getBytes(UTF_8)));
            var httpSpec = new HttpRequestSpecification(threads, xsltDir.file, configElement);
            threads.execute();

            var server = new Server(port);
            server.setHandler(new AbstractHandler() {
                @SneakyThrows(ConfigurationException.class)
                @Override public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
                throws IOException {
                    response.setStatus(HttpServletResponse.SC_OK);
                    deliverResponse.deliverResponse(request, response);
                    baseRequest.setHandled(true);
                }
            });

            var params = new HashMap<ParameterName, String>();
            params.put(new ParameterName("foo"), "bar");

            try { server.start(); }
            catch (Exception e) { throw new RuntimeException(e); }

            var application = Application.newForTesting(Map.of("t", Transformer.newIdentityTransformerForTesting()));
            try (var tx = new ApplicationTransaction(application)) {
                var context = new TransformationContext(application, tx, params, emptyList(), emptyMap());
                var resultContainer = new Object() {
                    public Element element;
                };
                httpSpec.scheduleExecutionAndParseResponse(context, emptySet(), e -> resultContainer.element = e);
                
                // In EndpointsExecutor, only the threads.execute catches Exceptions
                // so we simulate that behaviour here. That's because all the HTTP requests must happen
                // in the threads otherwise they will be executed in sequence (which is wrong)
                // although everything will appear to work.
                try { context.threads.execute(); }
                catch (RuntimeException e) {
                    unwrapException(e, HttpRequestFailedException.class);
                    throw e;
                }

                return resultContainer.element;
            }
            finally {
                try { server.stop(); }
                catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    public void testReplaceXmlElementWithFileUploads() throws Exception {
        var uploadedFile = new UploadedFile() {
            @Nonnull @Override public String getFieldName() { return "foo"; }
            @SneakyThrows(IOException.class)
            @Nonnull @Override public InputStream getInputStream() { return IOUtils.toInputStream("wt", UTF_8.name()); }
            @Nonnull @Override public String getContentType() { return "text/plain"; } 
            @Nonnull @Override public String getSubmittedFileName() { return "password.txt"; }
        };
        
        var sourceXml = DomParser.from(IOUtils.toInputStream("<foo><upload upload-field-name='foo' encoding='base64'/></foo>", 
            UTF_8.name()));
        
        var destXml = HttpRequestSpecification.replaceXmlElementWithFileUploads(
            Collections.singletonList(uploadedFile), sourceXml.getOwnerDocument());

        var root = destXml.getDocumentElement();
        assertEquals("foo", root.getNodeName());
        assertEquals(1, root.getChildNodes().getLength());
        
        var upload = root.getFirstChild();
        assertEquals("upload", upload.getNodeName());
        assertEquals(1, upload.getChildNodes().getLength()); // text content
        assertEquals("base64", upload.getAttributes().getNamedItem("encoding").getTextContent());
        assertEquals("password.txt", upload.getAttributes().getNamedItem("filename").getTextContent());
        assertEquals("d3Q=", upload.getTextContent());
    }
    
    protected void runTestFail(@Nonnull String configXml, @Nonnull DeliverResponse deliverResponse)
    throws TransformationFailedException, HttpRequestFailedException {
        try { runTest(false, configXml, deliverResponse); fail(); }
        catch (HttpRequestFailedException ignored) { }

        runTest(true, configXml, deliverResponse);
    }
    
    public void runTestInclVariousHttpFails(@Nonnull String configXml, @Nonnull DeliverResponse deliverResponse) 
    throws HttpRequestFailedException, TransformationFailedException {
        runTest(false, configXml, deliverResponse);

        // Test URL doesn't exist
        runTestFail("<url>https://dfglkjfgkjldgkjgdfkl.com/</url>" + configXml, deliverResponse);

        // Test non-200 status code
        runTestFail(configXml, (req, resp) -> resp.setStatus(HttpServletResponse.SC_CONFLICT));
    }

    public void testExecuteAndParseResponse() throws Exception {
        // Test non GET method
        runTest(false, "<method name='POST'/>", (req, resp) -> {
            if ( ! req.getMethod().equals("POST")) resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test GET parameter incl parameter expansion
        runTest(false, "<get-parameter name='foo'>${foo}</get-parameter>", (req, resp) -> {
            if ( ! req.getParameter("foo").equals("bar")) resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test header incl parameter expansion
        runTest(false, "<request-header name='foo'>${foo}</request-header>", (req, resp) -> {
            if ( ! req.getHeader("foo").equals("bar")) resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });
        
        // ---------------- Various request bodies ------------------------

        // Test empty request body
        runTestInclVariousHttpFails("", (req, resp) -> { });

        // Test XML request body (fixed) incl parameter expansion
        runTestInclVariousHttpFails("<xml-body> <your-tag>${foo}</your-tag> </xml-body>", (req, resp) -> {
            if ( ! IOUtils.toString(req.getInputStream(), UTF_8.name()).contains("<your-tag>bar</your-tag>"))
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test XML request body (fixed) incl referencing other XSLTs within an element
        runTestInclVariousHttpFails("<xml-body expand-transformations='true'> " +
                "<your-tag xslt-transformation='t' encoding='base64'/> </xml-body>", (req, resp) -> {
            var xml = DomParser.from(req.getInputStream());
            if ( ! xml.getNodeName().equals("your-tag")) resp.setStatus(HttpServletResponse.SC_CONFLICT);
            var base64 = Base64.decodeBase64(xml.getTextContent());
            var file = DomParser.from(new ByteArrayInputStream(base64));
            if ( ! file.getNodeName().equals("transformation-input")) resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test XML request body (xslt)
        runTestInclVariousHttpFails("<xml-body xslt-file='unit-test.xslt'/>", (req, resp) -> {
            if ( ! IOUtils.toString(req.getInputStream(), UTF_8.name()).contains("<xml-from-xslt/>"))
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test JSON request body incl parameter expansion
        runTestInclVariousHttpFails("<json-body>{ \"key\": \"${foo}\" }</json-body>", (req, resp) -> {
            if ( ! IOUtils.toString(req.getInputStream(), UTF_8.name()).contains("\"key\":\"bar\""))
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // ---------------- Various response bodies ------------------------

        // Test non-XML/JSON case
        runTestFail("", (req, resp) -> {
            resp.setContentType("text/plain");
            IOUtils.write("some-text", resp.getOutputStream());
        });

        // Test invalid XML
        runTestFail("", (req, resp) -> {
            resp.setContentType("application/xml");
            IOUtils.write("not-valid-xml", resp.getOutputStream());
        });

        // Test XML response body
        assertEquals("output", runTest(false, "", (req, resp) -> {
            resp.setContentType("application/xml");
            IOUtils.write("<output>stuff</output>", resp.getOutputStream());
        }).getTagName());

        // Test invalid JSON
        runTestFail("", (req, resp) -> {
            resp.setContentType("application/json");
            IOUtils.write("{'foo'", resp.getOutputStream());
        });

        // Test JSON response body
        var jsonResponse = runTest(false, "", (req, resp) -> {
            resp.setContentType("application/json");
            IOUtils.write("{ \"output\": \"stuff\" }", resp.getOutputStream());
        });
        assertEquals("response", jsonResponse.getTagName());   // HttpRequestSpecification adds this wrapper XML element
        assertEquals("output", DomParser.getSubElements(jsonResponse, "*").get(0).getTagName());

        // Test empty
        assertEquals("empty-response", runTest(false, "", (req, resp) -> {
            resp.setContentType("something/invalid");
        }).getTagName());
    }
}