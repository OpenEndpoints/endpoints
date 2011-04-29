package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.EndpointExecutor.UploadedFile;
import endpoints.config.HttpRequestSpecification.HttpRequestFailedException;
import junit.framework.TestCase;
import lombok.SneakyThrows;
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

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Collections.emptyList;

public class HttpRequestSpecificationTest extends TestCase {

    int port = 34895;

    @FunctionalInterface
    interface DeliverResponse {
        public void deliverResponse(@Nonnull HttpServletRequest request, @Nonnull HttpServletResponse response) throws IOException;
    }

    @SneakyThrows({ConfigurationException.class, IOException.class})
    public @Nonnull Element runTest(@Nonnull String configXml, @Nonnull DeliverResponse deliverResponse)
    throws HttpRequestFailedException {
        var httpXslt = "" +
            "<xsl:stylesheet version='1.0' xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>" +
            "  <xsl:template match='/'>" +
            "    <xml-from-xslt/>" +
            "  </xsl:template>" +
            "</xsl:stylesheet>";
        var xsltDir = File.createTempFile("xslt-files", "");
        if ( ! xsltDir.delete()) throw new RuntimeException();
        if ( ! xsltDir.mkdir()) throw new RuntimeException();
        var xsltFile = new File(xsltDir, "unit-test.xslt");
        FileUtils.writeStringToFile(xsltFile, httpXslt, StandardCharsets.UTF_8.name());
        
        var config =
            "<task class='endpoints.task.HttpRequestTask'>" +
            "  <url>http://localhost:"+port+"/</url>" +
            configXml +
            "</task>";
        var threads = new XsltCompilationThreads();
        var configElement = DomParser.from(new ByteArrayInputStream(config.getBytes(UTF_8)));
        var httpSpec = new HttpRequestSpecification(threads, xsltDir, configElement);
        threads.execute();

        var server = new Server(port);
        server.setHandler(new AbstractHandler() {
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

        try {
            var result = httpSpec.executeAndParseResponse(params, emptyList());
            assertNotNull(result);
            return result;
        }
        finally {
            try { server.stop(); }
            catch (Exception e) { e.printStackTrace(); }
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

    public void testExecuteAndParseResponse() throws Exception {
        // Test normal case
        runTest("", (req, resp) -> { });

        // Test non-200 status code
        try { runTest("", (req, resp) -> resp.setStatus(HttpServletResponse.SC_CONFLICT)); fail(); }
        catch (HttpRequestFailedException ignored) { }

        // Test non GET method
        runTest("<method name='POST'/>", (req, resp) -> {
            if ( ! req.getMethod().equals("POST")) resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test GET parameter incl parameter expansion
        runTest("<get-parameter name='foo'>${foo}</get-parameter>", (req, resp) -> {
            if ( ! req.getParameter("foo").equals("bar")) resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test header incl parameter expansion
        runTest("<request-header name='foo'>${foo}</request-header>", (req, resp) -> {
            if ( ! req.getHeader("foo").equals("bar")) resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test XML request body (fixed) incl parameter expansion
        runTest("<xml-body> <your-tag>${foo}</your-tag> </xml-body>", (req, resp) -> {
            if ( ! IOUtils.toString(req.getInputStream(), UTF_8.name()).contains("<your-tag>bar</your-tag>"))
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test XML request body (xslt) incl parameter expansion
        runTest("<xml-body xslt-file='unit-test.xslt'/>", (req, resp) -> {
            if ( ! IOUtils.toString(req.getInputStream(), UTF_8.name()).contains("<xml-from-xslt/>"))
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test JSON request body incl parameter expansion
        runTest("<json-body>{ \"key\": \"${foo}\" }</json-body>", (req, resp) -> {
            if ( ! IOUtils.toString(req.getInputStream(), UTF_8.name()).contains("\"key\":\"bar\""))
                resp.setStatus(HttpServletResponse.SC_CONFLICT);
        });

        // Test XML response body
        assertEquals("output", runTest("", (req, resp) -> {
            resp.setContentType("application/xml");
            IOUtils.write("<output>stuff</output>", resp.getOutputStream());
        }).getTagName());

        // Test JSON response body
        var jsonResponse = runTest("", (req, resp) -> {
            resp.setContentType("application/json");
            IOUtils.write("{ \"output\": \"stuff\" }", resp.getOutputStream());
        });
        assertEquals("response", jsonResponse.getTagName());   // HttpRequestSpecification adds this wrapper XML element
        assertEquals("output", DomParser.getSubElements(jsonResponse, "*").get(0).getTagName());

        // Test empty
        assertEquals("empty-response", runTest("", (req, resp) -> {
            resp.setContentType("something/invalid");
        }).getTagName());
    }
}