package endpoints.config;

import com.databasesandlife.util.*;
import com.databasesandlife.util.DomVariableExpander.VariableNotFoundException;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.net.MediaType;
import com.offerready.xslt.DocumentGenerator.StyleVisionXslt;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.DeploymentParameters;
import endpoints.EndpointExecutor.RequestInvalidException;
import endpoints.EndpointExecutor.UploadedFile;
import endpoints.PlaintextParameterReplacer;
import lombok.SneakyThrows;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.apache.log4j.Logger;
import org.json.JSONException;
import org.json.JSONTokener;
import org.json.XML;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;

import static com.databasesandlife.util.DomParser.*;
import static com.databasesandlife.util.DomVariableExpander.VariableSyntax.dollarThenBraces;
import static com.databasesandlife.util.gwtsafe.ConfigurationException.prefixExceptionMessage;
import static com.offerready.xslt.WeaklyCachedXsltTransformer.getTransformerOrScheduleCompilation;
import static endpoints.EndpointExecutor.logXmlForDebugging;
import static endpoints.PlaintextParameterReplacer.replacePlainTextParameters;
import static endpoints.datasource.ParametersCommand.createParametersElement;
import static java.lang.Boolean.parseBoolean;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.*;

public class HttpRequestSpecification {

    public enum HttpMethod {
        GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE;
    }

    protected final boolean ignoreIfError;
    protected final @Nonnull String urlPattern;
    protected final @Nonnull HttpMethod method;
    protected final @Nonnull Map<String, String> getParameterPatterns;
    protected final @Nonnull Map<String, String> requestHeaderPatterns;
    protected @CheckForNull String usernamePatternOrNull, passwordPatternOrNull;
    protected @CheckForNull Element requestBodyXmlTemplate;
    protected @CheckForNull WeaklyCachedXsltTransformer requestBodyXmlTransformer;
    protected boolean replaceXmlElementWithFileUploads;
    protected @CheckForNull JsonNode requestBodyJsonTemplate;
    protected @CheckForNull WeaklyCachedXsltTransformer requestBodyJsonTransformer;

    public static class HttpRequestFailedException extends Exception {
        public final @Nonnull String url;
        public final @CheckForNull Integer responseStatusCode;

        public HttpRequestFailedException(@Nonnull String url, @CheckForNull Integer responseStatusCode, String msg) {
            super(msg);
            this.url=url;
            this.responseStatusCode=responseStatusCode;
        }

        public HttpRequestFailedException(@Nonnull String url, @CheckForNull Integer responseStatusCode, String prefix, Throwable t) {
            super(prefixExceptionMessage(prefix, t), t);
            this.url=url;
            this.responseStatusCode=responseStatusCode;
        }
    }

    protected static @Nonnull JsonNode expandJson(@Nonnull Map<ParameterName, String> parameters, @Nonnull JsonNode input)
    throws VariableNotFoundException {
        if (input instanceof ArrayNode) {
            var result = ((ArrayNode) input).arrayNode();
            for (int i = 0; i < input.size(); i++) result.add(expandJson(parameters, input.get(i)));
            return result;
        }
        else if (input instanceof ObjectNode) {
            var result = ((ObjectNode) input).objectNode();
            for (Iterator<Map.Entry<String, JsonNode>> i = input.fields(); i.hasNext(); ) {
                var e = i.next();
                result.set(e.getKey(), expandJson(parameters, e.getValue()));
            }
            return result;
        }
        else if (input instanceof TextNode) {
            return new TextNode(PlaintextParameterReplacer.replacePlainTextParameters(input.asText(), parameters));
        }
        else return input;
    }
    
    @SneakyThrows(TransformerException.class)
    protected static @Nonnull Document replaceXmlElementWithFileUploads(
        @Nonnull List<? extends UploadedFile> fileUploads, @Nonnull Document doc 
    ) {
        return DomVariableExpander.expand(doc, x -> new IdentityForwardingSaxHandler(x) {
            @SneakyThrows({IOException.class, ConfigurationException.class, RequestInvalidException.class})
            @Override public void startElement(String uri, String localName, String qName, Attributes atts) throws SAXException {
                var uploadFieldName = atts.getValue("upload-field-name");
                if (uploadFieldName != null) {
                    var encoding = atts.getValue("encoding");
                    if ( ! "base64".equals(encoding)) throw new ConfigurationException("<"+localName+" upload-field-name='"
                        + uploadFieldName + "' encoding='"+encoding+"'> must have encoding='base64'");
                    
                    var fileList = fileUploads.stream()
                        .filter(f -> f.getFieldName().equalsIgnoreCase(uploadFieldName))
                        .collect(toList());
                    if (fileList.size() > 1) throw new RequestInvalidException(
                        "More than one <input type='file' name='" + uploadFieldName + "'> found in request");
                    if (fileList.size() < 1) throw new RequestInvalidException(
                        "<input type='file' name='" + uploadFieldName + "'> not found in request");
                    var file = fileList.iterator().next();

                    var uploadAtts = new AttributesImpl(atts);
                    if (file.getSubmittedFileName() != null)
                        uploadAtts.addAttribute("", "filename", "filename", "", file.getSubmittedFileName());
                    super.startElement(uri, localName, qName, uploadAtts);
                    
                    var bytes = IOUtils.toByteArray(file.getInputStream());
                    var base64 = Base64.encodeBase64String(bytes).toCharArray();
                    super.characters(base64, 0, base64.length);

                    // endElement will be called by the corresponding source element's endElement
                } else {
                    super.startElement(uri, localName, qName, atts);
                }
            }
        });
    }

    public HttpRequestSpecification(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory, @Nonnull Element command
    ) throws ConfigurationException {
        assertNoOtherElements(command, "url", "method", "get-parameter", "request-header",
            "basic-access-authentication", "xml-body", "json-body");

        ignoreIfError = parseBoolean(getOptionalAttribute(command, "ignore-if-error"));
        
        urlPattern = getMandatorySingleSubElement(command, "url").getTextContent();

        var methodElement = getOptionalSingleSubElement(command, "method");
        if (methodElement == null) method = HttpMethod.GET;
        else method = HttpMethod.valueOf(getMandatoryAttribute(methodElement, "name"));

        getParameterPatterns = parseMap(command, "get-parameter", "name");
        requestHeaderPatterns = parseMap(command, "request-header", "name");

        var authElement = getOptionalSingleSubElement(command, "basic-access-authentication");
        if (authElement != null) {
            assertNoOtherElements(authElement, "username", "password");
            usernamePatternOrNull = getMandatoryAttribute(authElement, "username");
            passwordPatternOrNull = getMandatoryAttribute(authElement, "password");
        }

        var requestBodyXmlTemplateContainer = getOptionalSingleSubElement(command, "xml-body");
        if (requestBodyXmlTemplateContainer != null) {
            requestBodyXmlTemplate = getOptionalSingleSubElement(requestBodyXmlTemplateContainer, "*");
            var xsltFileName = getOptionalAttribute(requestBodyXmlTemplateContainer, "xslt-file");
            if (xsltFileName != null) {
                var xsltFile = new File(httpXsltDirectory, xsltFileName);
                if ( ! xsltFile.exists()) throw new ConfigurationException("<xml-body xslt-file='"+xsltFileName+"'>: " +
                    "File '" + httpXsltDirectory.getName()+"/"+xsltFileName+"' not found");
                requestBodyXmlTransformer = getTransformerOrScheduleCompilation(threads, xsltFile.getAbsolutePath(),
                    new StyleVisionXslt(xsltFile));
            }
            if (requestBodyXmlTemplate != null && requestBodyXmlTransformer != null)
                throw new ConfigurationException("<xml-body>: must have xslt-file='x.xslt' attr, or a fixed body");
            if (requestBodyXmlTemplate == null && requestBodyXmlTransformer == null)
                throw new ConfigurationException("<xml-body>: must have xslt-file='x.xslt' attr, or a fixed body");
            replaceXmlElementWithFileUploads = Boolean.parseBoolean(getOptionalAttribute(
                requestBodyXmlTemplateContainer, "upload-files"));
        }

        var requestBodyJsonElement = getOptionalSingleSubElement(command, "json-body");
        if (requestBodyJsonElement != null) {
            var fixedJsonBody = requestBodyJsonElement.getTextContent();
            if ( ! fixedJsonBody.isEmpty()) {
                try { requestBodyJsonTemplate = new ObjectMapper().readTree(fixedJsonBody); }
                catch (JsonProcessingException e) { throw new ConfigurationException("<json-body>", e); }
                catch (IOException e) { throw new RuntimeException(e); }
            }

            var xsltFileName = getOptionalAttribute(requestBodyJsonElement, "xslt-file");
            if (xsltFileName != null) {
                var xsltFile = new File(httpXsltDirectory, xsltFileName);
                if ( ! xsltFile.exists()) throw new ConfigurationException("<json-body xslt-file='"+xsltFileName+"'>: " +
                    "File '" + httpXsltDirectory.getName()+"/"+xsltFileName+"' not found");
                requestBodyJsonTransformer = getTransformerOrScheduleCompilation(threads, xsltFile.getAbsolutePath(),
                    new StyleVisionXslt(xsltFile));
            }
            
            if (requestBodyJsonTemplate != null && requestBodyJsonTransformer != null)
                throw new ConfigurationException("<json-body>: must have xslt-file='x.xslt' attr, or a fixed body");
            if (requestBodyJsonTemplate == null && requestBodyJsonTransformer == null)
                throw new ConfigurationException("<json-body>: must have xslt-file='x.xslt' attr, or a fixed body");
        }

        if (requestBodyXmlTemplate != null && requestBodyJsonTemplate != null)
            throw new ConfigurationException("Only one of <xml-body> and <json-body> may be set");
    }

    public void assertParametersSuffice(@Nonnull Collection<ParameterName> params) throws ConfigurationException {
        PlaintextParameterReplacer.assertParametersSuffice(params, urlPattern, "<url> element");
        for (var e : getParameterPatterns.entrySet())
            PlaintextParameterReplacer.assertParametersSuffice(params, e.getValue(), "<get-parameter name='"+e.getKey()+"'> element");
        for (var e : requestHeaderPatterns.entrySet())
            PlaintextParameterReplacer.assertParametersSuffice(params, e.getValue(), "<request-header name='"+e.getKey()+"'> element");
        PlaintextParameterReplacer.assertParametersSuffice(params, usernamePatternOrNull, "'username' attribute");
        PlaintextParameterReplacer.assertParametersSuffice(params, passwordPatternOrNull, "'password' attribute");

        try {
            if (requestBodyXmlTemplate != null) {
                var emptyParams = params.stream().collect(toMap(param -> param.name, param -> ""));
                DomVariableExpander.expand(dollarThenBraces, emptyParams, requestBodyXmlTemplate);
            }
            if (requestBodyJsonTemplate != null) {
                var emptyParams = params.stream().collect(toMap(param -> param, param -> ""));
                expandJson(emptyParams, requestBodyJsonTemplate);
            }
        }
        catch (VariableNotFoundException e) { throw new ConfigurationException(e); }
    }

    /** @return null if an error occurred and this request is set to ignore errors */
    @SneakyThrows({TransformerConfigurationException.class, TransformerException.class, DocumentTemplateInvalidException.class})
    public @CheckForNull URLConnection executeAndAssertNoError(
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads
    ) throws HttpRequestFailedException {
        try {
            var baseUrl = replacePlainTextParameters(urlPattern, params); // without ?x=y parameters
            try (var ignored = new Timer("Execute HTTP request to '" + baseUrl + "'")) {
                var getParameters = new HashMap<String, String>();
                for (var e : getParameterPatterns.entrySet())
                    getParameters.put(e.getKey(), replacePlainTextParameters(e.getValue(), params));
                var urlAndParams = getParameterPatterns.isEmpty()
                    ? baseUrl
                    : baseUrl + "?" + WebEncodingUtils.encodeGetParameters(getParameters);
    
                var urlConnection = (HttpURLConnection) new URL(urlAndParams).openConnection();
                urlConnection.setRequestMethod(method.name());
    
                for (var e : requestHeaderPatterns.entrySet())
                    urlConnection.setRequestProperty(e.getKey(), replacePlainTextParameters(e.getValue(), params));
    
                if (usernamePatternOrNull != null && passwordPatternOrNull != null) {
                    var user = replacePlainTextParameters(usernamePatternOrNull, params);
                    var pw = replacePlainTextParameters(passwordPatternOrNull, params);
                    var encodedAuth = Base64.encodeBase64String((user + ":" + pw).getBytes(UTF_8));
                    urlConnection.setRequestProperty("Authorization", "Basic " + encodedAuth);
                }
    
                if (requestBodyXmlTemplate != null || requestBodyXmlTransformer != null) {
                    if ( ! requestHeaderPatterns.keySet().stream().map(x -> x.toLowerCase()).collect(toSet()).contains("content-type"))
                        urlConnection.setRequestProperty("Content-Type", "application/xml; charset=UTF-8");
                    urlConnection.setDoOutput(true);
                    try (var o = urlConnection.getOutputStream()) {
                        // Get XML (either fixed in <xml-body>, or result of XSLT) 
                        Document body;
                        if (requestBodyXmlTemplate != null) {
                            var stringParams = params.entrySet().stream().collect(toMap(e -> e.getKey().name, e -> e.getValue()));
                            body = DomVariableExpander.expand(dollarThenBraces, stringParams, requestBodyXmlTemplate);
                        } else if (requestBodyXmlTransformer != null) {
                            var parametersXml = createParametersElement("parameters", params);
                            var bodyDocument = new DOMResult();
                            requestBodyXmlTransformer.newTransformer().transform(
                                new DOMSource(parametersXml.getOwnerDocument()), bodyDocument);
                            body = (Document) bodyDocument.getNode();
                            logXmlForDebugging(getClass(), "Result of XSLT, to send to '" + baseUrl + "'", body);
                        } else {
                            throw new RuntimeException("Unreachable");
                        }
                        
                        // Add base64 uploaded files if necessary
                        if (replaceXmlElementWithFileUploads)
                            body = replaceXmlElementWithFileUploads(fileUploads, body);
                        
                        // Stream to HTTP connection
                        TransformerFactory.newInstance().newTransformer().transform(new DOMSource(body), new StreamResult(o));
                    }
                }

                if (requestBodyJsonTemplate != null || requestBodyJsonTransformer != null) {
                    if ( ! requestHeaderPatterns.keySet().stream().map(x -> x.toLowerCase()).collect(toSet()).contains("content-type"))
                        urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                    urlConnection.setDoOutput(true);
                    try (var o = urlConnection.getOutputStream()) {
                        if (requestBodyJsonTemplate != null) {
                            var body = expandJson(params, requestBodyJsonTemplate);
                            new ObjectMapper().writeValue(o, body);
                        } else if (requestBodyJsonTransformer != null) {
                            var parametersXml = createParametersElement("parameters", params);
                            StringWriter json = new StringWriter();
                            requestBodyJsonTransformer.newTransformer().transform(
                                new DOMSource(parametersXml.getOwnerDocument()), new StreamResult(json));
                            if (DeploymentParameters.get().xsltDebugLog)
                                Logger.getLogger(getClass()).info("Result of XSLT, to send to '" + baseUrl + "'\n" + json.toString());
                            IOUtils.write(json.toString(), o, UTF_8.name());
                        } else {
                            throw new RuntimeException("Unreachable");
                        }
                    }
                }
    
                if (urlConnection.getResponseCode() < 200 || urlConnection.getResponseCode() >= 300) {
                    String body = null;
                    var type = urlConnection.getContentType();
                    if (type != null && (type.contains("text") || type.contains("json") || type.contains("xml"))) {
                        try (var errorStream = urlConnection.getErrorStream()) {
                            if (errorStream != null) {
                                var responseCharset = MediaType.parse(urlConnection.getContentType()).charset().or(UTF_8);
                                body = IOUtils.toString(errorStream, responseCharset.name());
                            }
                        }
                    }
    
                    var bodyMsg = body == null ? "" :
                        " (body was: " +
                            (body.length() > 1_000 ? body.substring(0, 1_000)+"... [truncated]" : body)
                        + ")";
    
                    var reasonPhrase = urlConnection.getResponseMessage() == null || urlConnection.getResponseMessage().isEmpty()
                        ? "" : " " + urlConnection.getResponseMessage();
    
                    throw new HttpRequestFailedException(baseUrl, urlConnection.getResponseCode(), "URL '" + baseUrl
                        + "' returned " + urlConnection.getResponseCode()
                        + reasonPhrase + bodyMsg);
                }
    
                return urlConnection;
            }
            catch (IOException e) { throw new HttpRequestFailedException(baseUrl, null, "URL '" + baseUrl + "'", e); }
        }
        catch (HttpRequestFailedException e) {
            if (ignoreIfError) {
                Logger.getLogger(getClass()).warn(e.getMessage());
                return null;
            } else throw e;
        }
    }

    /** @return null if an error occurred and this request is set to ignore errors. Does not expand variables in response */
    public @CheckForNull Element executeAndParseResponse(
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads
    ) throws HttpRequestFailedException {
        var urlConnection = executeAndAssertNoError(params, fileUploads);
        if (urlConnection == null) return null;
        
        try {
            var url = urlConnection.getURL();
    
            try {
                if (urlConnection.getContentLength() == 0) {
                    var response = DomParser.newDocumentBuilder().newDocument();
                    response.appendChild(response.createElement("empty-response"));
                    return response.getDocumentElement();
                }
                else if (urlConnection.getContentType().toLowerCase().contains("json")) {
                    var responseCharset = MediaType.parse(urlConnection.getContentType()).charset().or(UTF_8);
                    String xmlString;
                    try (var reader = new InputStreamReader(urlConnection.getInputStream(), responseCharset)) {
                        xmlString = XML.toString(new JSONTokener(reader).nextValue());
                    }
                    catch (JSONException e) {
                        throw new HttpRequestFailedException(url.toExternalForm(), null, "Cannot parse JSON response from '" + url + "'", e);
                    }
    
                    // The "JSON to XML" conversion does not necessarily have a single root element. XML requires a single root element.
                    var xmlIncludingHeader = "<?xml version=\"1.0\" encoding=\"utf-8\"?><response>" + xmlString + "</response>";
    
                    try {
                        return newDocumentBuilder().parse(new ByteArrayInputStream(xmlIncludingHeader.getBytes(UTF_8))).getDocumentElement();
                    }
                    catch (SAXException e) {
                        Logger.getLogger(getClass()).info("Response JSON, converted to XML: " + xmlIncludingHeader);
                        throw new HttpRequestFailedException(url.toExternalForm(), null, "Could not convert JSON at URL '" + url + "' to valid XML", e);
                    }
                }
                else if (urlConnection.getContentType().toLowerCase().contains("xml")) {
                    try (var inputStream = urlConnection.getInputStream()) {
                        var downloadedXml = DomParser.newDocumentBuilder().parse(inputStream);
                        return downloadedXml.getDocumentElement();
                    }
                    catch (SAXException e) {
                        throw new HttpRequestFailedException(url.toExternalForm(), null, "URL '" + url + "' contained invalid XML", e);
                    }
                }
                else throw new HttpRequestFailedException(url.toExternalForm(), null, "URL '" + url + "' returned an unexpected content type " +
                        "'" + urlConnection.getContentType() + "': Expecting XML, JSON, or empty content");
            }
            catch (IOException e) { throw new HttpRequestFailedException(url.toExternalForm(), null, "URL '" + url + "'", e); }
        }
        catch (HttpRequestFailedException e) {
            if (ignoreIfError) {
                Logger.getLogger(getClass()).warn(e.getMessage());
                return null;
            }
            else throw e;
        }
    }
}
