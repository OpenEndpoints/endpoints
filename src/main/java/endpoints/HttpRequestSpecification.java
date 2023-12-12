package endpoints;

import com.databasesandlife.util.*;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.DomVariableExpander.VariableNotFoundException;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.google.common.net.MediaType;
import com.offerready.xslt.DocumentGenerator.StyleVisionXslt;
import com.offerready.xslt.JsonXmlConverter;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.EndpointExecutor.InvalidRequestException;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import endpoints.datasource.TransformationFailedException;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.io.IOUtils;
import org.json.JSONException;
import org.jsoup.Jsoup;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.*;
import static com.databasesandlife.util.DomVariableExpander.VariableSyntax.dollarThenBraces;
import static com.databasesandlife.util.gwtsafe.ConfigurationException.prefixExceptionMessage;
import static com.offerready.xslt.WeaklyCachedXsltTransformer.getTransformerOrScheduleCompilation;
import static endpoints.EndpointExecutor.logXmlForDebugging;
import static endpoints.PlaintextParameterReplacer.replacePlainTextParameters;
import static endpoints.datasource.ParametersCommand.createParametersElement;
import static java.lang.Boolean.parseBoolean;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;
import static org.jsoup.nodes.Document.OutputSettings.Syntax.xml;

@Slf4j
public class HttpRequestSpecification {

    public enum HttpMethod {
        GET, HEAD, POST, PUT, PATCH, DELETE, OPTIONS, TRACE
    }

    public final boolean ignoreIfError;
    protected final @Nonnull String urlPattern;
    protected final @Nonnull HttpMethod method;
    protected final @Nonnull Map<String, String> getParameterPatterns;
    protected final @Nonnull Map<String, String> requestHeaderPatterns;
    protected @CheckForNull String usernamePatternOrNull, passwordPatternOrNull;
    protected final @Nonnull Map<String, String> postParameterPatterns;
    protected @CheckForNull Element requestBodyXmlTemplate;
    protected @CheckForNull WeaklyCachedXsltTransformer requestBodyXmlTransformer;
    protected boolean replaceXmlElementWithFileUploads, replaceXmlElementsWithTransformerResults;
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

    protected static @Nonnull JsonNode expandJson(@Nonnull Map<String, LazyCachingValue> parameters, @Nonnull JsonNode input)
    throws VariableNotFoundException {
        if (input instanceof ArrayNode arrayNode) {
            var result = arrayNode.arrayNode();
            for (int i = 0; i < input.size(); i++) result.add(expandJson(parameters, input.get(i)));
            return result;
        }
        else if (input instanceof ObjectNode objectNode) {
            var result = objectNode.objectNode();
            for (Iterator<Map.Entry<String, JsonNode>> i = input.fields(); i.hasNext(); ) {
                var e = i.next();
                result.set(e.getKey(), expandJson(parameters, e.getValue()));
            }
            return result;
        }
        else if (input instanceof TextNode) {
            return new TextNode(replacePlainTextParameters(input.asText(), parameters));
        }
        else return input;
    }
    
    @SneakyThrows(TransformerException.class)
    protected static @Nonnull Document replaceXmlElementWithFileUploads(
        @Nonnull List<? extends UploadedFile> fileUploads, @Nonnull Document doc 
    ) {
        return DomVariableExpander.expand(doc, x -> new IdentityForwardingSaxHandler(x) {
            @SneakyThrows({ConfigurationException.class, InvalidRequestException.class})
            @Override public void startElement(String uri, String localName, String qName, Attributes attrs) throws SAXException {
                var uploadFieldName = attrs.getValue("upload-field-name");
                if (uploadFieldName != null) {
                    var encoding = attrs.getValue("encoding");
                    if ( ! "base64".equals(encoding)) throw new ConfigurationException("<"+localName+" upload-field-name='"
                        + uploadFieldName + "' encoding='"+encoding+"'> must have encoding='base64'");
                    
                    var fileList = fileUploads.stream()
                        .filter(f -> f.getFieldName().equalsIgnoreCase(uploadFieldName))
                        .toList();
                    if (fileList.size() > 1) throw new InvalidRequestException(
                        "More than one <input type='file' name='" + uploadFieldName + "'> found in request");
                    if (fileList.size() < 1) throw new InvalidRequestException(
                        "<input type='file' name='" + uploadFieldName + "'> not found in request");
                    var file = fileList.iterator().next();

                    var uploadAttrs = new AttributesImpl(attrs);
                    if (file.getSubmittedFileName() != null)
                        uploadAttrs.addAttribute("", "filename", "filename", "", file.getSubmittedFileName());
                    super.startElement(uri, localName, qName, uploadAttrs);
                    
                    // Deliberately don't set these intermediate results e.g. file.toByteArray() in variables.
                    // Java GC cannot release objects as long as they're bound to a local variable in a running block.
                    // These can be large e.g. for a 100MB file, base64 is 125MB, as 2-byte string that is 256MB.
                    // We create a byte[], then a String (base64), then a char[], then super.characters(..) creates a String.
                    // Try not to require these all to be in memory simultaneously.
                    var base64 = Base64.encodeBase64String(file.toByteArray()).toCharArray();
                    
                    super.characters(base64, 0, base64.length);

                    // endElement will be called by the corresponding source element's endElement
                } else {
                    super.startElement(uri, localName, qName, attrs);
                }
            }
        });
    }

    public HttpRequestSpecification(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory, @Nonnull Element command
    ) throws ConfigurationException {
        assertNoOtherElements(command, "post-process", "after", "input-intermediate-value", "output-intermediate-value",
            "url", "method", "get-parameter", "request-header",
            "basic-access-authentication", "post-parameter", "xml-body", "json-body");

        ignoreIfError = parseBoolean(getOptionalAttribute(command, "ignore-if-error"));
        
        urlPattern = getMandatorySingleSubElement(command, "url").getTextContent();

        var methodElement = getOptionalSingleSubElement(command, "method");
        if (methodElement == null) method = HttpMethod.GET;
        else method = HttpMethod.valueOf(getMandatoryAttribute(methodElement, "name"));

        getParameterPatterns = parseMap(command, "get-parameter", "name");
        requestHeaderPatterns = parseMap(command, "request-header", "name");
        postParameterPatterns = parseMap(command, "post-parameter", "name");

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
            replaceXmlElementsWithTransformerResults = Boolean.parseBoolean(getOptionalAttribute(
                requestBodyXmlTemplateContainer, "expand-transformations"));
        }

        var requestBodyJsonElement = getOptionalSingleSubElement(command, "json-body");
        if (requestBodyJsonElement != null) {
            var fixedJsonBody = requestBodyJsonElement.getTextContent();
            if ( ! fixedJsonBody.isEmpty()) {
                try { requestBodyJsonTemplate = new ObjectMapper().readTree(fixedJsonBody); }
                catch (JsonProcessingException e) { throw new ConfigurationException("<json-body>", e); }
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

        int bodyCount = 0;
        if ( ! postParameterPatterns.isEmpty()) bodyCount++;
        if (requestBodyXmlTemplate != null) bodyCount++;
        if (requestBodyJsonTemplate != null) bodyCount++;
        if (bodyCount > 1)
            throw new ConfigurationException("An HTTP request can only have one body. " +
                "Yet multiple types were set, out of <post-parameter>, <xml-body>, <json-body>");
    }

    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        var stringKeys = PlaintextParameterReplacer.getKeys(params, visibleIntermediateValues);
        var emptyParams = stringKeys.stream().collect(Collectors.toMap(param -> param, param -> LazyCachingValue.newFixed("")));

        PlaintextParameterReplacer.assertParametersSuffice(stringKeys, urlPattern, "<url> element");
        for (var e : getParameterPatterns.entrySet())
            PlaintextParameterReplacer.assertParametersSuffice(stringKeys, 
                e.getValue(), "<get-parameter name='"+e.getKey()+"'> element");
        for (var e : requestHeaderPatterns.entrySet())
            PlaintextParameterReplacer.assertParametersSuffice(stringKeys, 
                e.getValue(), "<request-header name='"+e.getKey()+"'> element");
        for (var e : postParameterPatterns.entrySet())
            PlaintextParameterReplacer.assertParametersSuffice(stringKeys, 
                e.getValue(), "<post-parameter name='"+e.getKey()+"'> element");
        PlaintextParameterReplacer.assertParametersSuffice(stringKeys, usernamePatternOrNull, "'username' attribute");
        PlaintextParameterReplacer.assertParametersSuffice(stringKeys, passwordPatternOrNull, "'password' attribute");

        try {
            if (requestBodyXmlTemplate != null)
                DomVariableExpander.expand(dollarThenBraces, param -> "", requestBodyXmlTemplate);
            if (requestBodyJsonTemplate != null)
                expandJson(emptyParams, requestBodyJsonTemplate);
        }
        catch (VariableNotFoundException e) { throw new ConfigurationException(e); }
    }

    public void throwException(@Nonnull String url, @Nonnull Exception e) {
        if (e instanceof IOException) {
            e = new HttpRequestFailedException(url, null, "URL '" + url + "'", e);
        }
        if (e instanceof HttpRequestFailedException) {
            if (ignoreIfError) {
                LoggerFactory.getLogger(HttpRequestSpecification.this.getClass()).warn(e.getMessage());
                return;
            } else {
                throw new RuntimeException(e);
            }
        }
        throw new RuntimeException(e);
    }

    /** @param after URLConnection is null if an error occurred and this request is set to ignore errors */
    public void scheduleExecutionAndAssertNoError(
        @Nonnull TransformationContext context,    
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues,
        @Nonnull Consumer<URLConnection> after
    )  {
        var stringParams = context.getParametersAndIntermediateValuesAndSecrets(visibleIntermediateValues);
        var baseUrl = replacePlainTextParameters(urlPattern, stringParams); // without ?x=y parameters
        var precursorTasks = new ArrayList<Runnable>();
        try {
            var getParameters = new HashMap<String, String>();
            for (var e : getParameterPatterns.entrySet())
                getParameters.put(e.getKey(), replacePlainTextParameters(e.getValue(), stringParams));
            var urlAndParams = getParameterPatterns.isEmpty()
                ? baseUrl
                : baseUrl + "?" + WebEncodingUtils.encodeGetParameters(getParameters);

            var urlConnection = (HttpURLConnection) new URL(urlAndParams).openConnection();
            urlConnection.setRequestMethod(method.name());

            for (var e : requestHeaderPatterns.entrySet())
                urlConnection.setRequestProperty(e.getKey(), replacePlainTextParameters(e.getValue(), stringParams));

            if (usernamePatternOrNull != null && passwordPatternOrNull != null) {
                var user = replacePlainTextParameters(usernamePatternOrNull, stringParams);
                var pw = replacePlainTextParameters(passwordPatternOrNull, stringParams);
                var encodedAuth = Base64.encodeBase64String((user + ":" + pw).getBytes(UTF_8));
                urlConnection.setRequestProperty("Authorization", "Basic " + encodedAuth);
            }
            
            if ( ! postParameterPatterns.isEmpty()) {
                Runnable req = () -> {
                    var expanded = new HashMap<String, String>();
                    for (var e : postParameterPatterns.entrySet())
                        expanded.put(e.getKey(), replacePlainTextParameters(e.getValue(), stringParams));
                    var bodyBytes = WebEncodingUtils.encodeGetParameters(expanded).toString().getBytes(StandardCharsets.UTF_8);
                    urlConnection.setDoOutput(true);
                    try (DataOutputStream wr = new DataOutputStream(urlConnection.getOutputStream())) {
                        wr.write(bodyBytes);
                    }
                    catch (IOException e) {
                        throwException(baseUrl, e); // IOException can be URL not found etc.
                        after.accept(null);
                    }
                };
                context.threads.addTaskOffPool(req);
                precursorTasks.add(req);
            }

            if (requestBodyXmlTemplate != null || requestBodyXmlTransformer != null) {
                if ( ! requestHeaderPatterns.keySet().stream().map(x -> x.toLowerCase()).collect(toSet()).contains("content-type"))
                    urlConnection.setRequestProperty("Content-Type", "application/xml; charset=UTF-8");
                
                // Get XML (either fixed in <xml-body>, or result of XSLT) 
                final Document body;
                if (requestBodyXmlTemplate != null) {
                    body = DomVariableExpander.expand(dollarThenBraces, p -> stringParams.get(p).get(), requestBodyXmlTemplate);
                } else if (requestBodyXmlTransformer != null) {
                    var parametersXml = createParametersElement("parameters", context, visibleIntermediateValues);
                    var bodyDocument = new DOMResult();
                    requestBodyXmlTransformer.newTransformer().transform(
                        new DOMSource(parametersXml.getOwnerDocument()), bodyDocument);
                    body = (Document) bodyDocument.getNode();
                    logXmlForDebugging(getClass(), "Result of XSLT, to send to '" + baseUrl + "'", body);
                } else {
                    throw new RuntimeException("Unreachable");
                }
                
                // Add base64 uploaded files if necessary
                var bodyAfterUploadFiles = (replaceXmlElementWithFileUploads)
                    ? replaceXmlElementWithFileUploads(context.request.getUploadedFiles(), body) : body;
                
                // After XSLT results expanded, make request
                Consumer<Document> makeRequest = bodyAfterXsltElementExpansion -> {
                    urlConnection.setDoOutput(true);
                    try (var o = urlConnection.getOutputStream()) {
                        // This does not do any XSLT, it simply sends the DOM to the HTTP server
                        TransformerFactory.newInstance().newTransformer().transform(
                            new DOMSource(bodyAfterXsltElementExpansion), new StreamResult(o));
                    }
                    catch (IOException | TransformerException e) {
                        throwException(baseUrl, e); // IOException can be URL not found etc.
                        after.accept(null);
                    }
                };
                
                // Add base64 XSLT results if necessary (e.g. PDFs)
                // Then do the request (always in a "task" so that we are not blocked by slow HTTP servers) 
                if (replaceXmlElementsWithTransformerResults) {
                    var xmlExpander = new XmlWithBase64TransformationsExpander(context, bodyAfterUploadFiles);
                    precursorTasks.add(xmlExpander.schedule(visibleIntermediateValues, makeRequest));
                } else {
                    Runnable req = () -> makeRequest.accept(bodyAfterUploadFiles);
                    context.threads.addTaskOffPool(req);
                    precursorTasks.add(req);
                }
            }

            if (requestBodyJsonTemplate != null || requestBodyJsonTransformer != null) {
                if ( ! requestHeaderPatterns.keySet().stream().map(x -> x.toLowerCase()).collect(toSet()).contains("content-type"))
                    urlConnection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                urlConnection.setDoOutput(true);
                Runnable sendRequest = () -> { 
                    try (var o = urlConnection.getOutputStream()) {
                        if (requestBodyJsonTemplate != null) {
                            var body = expandJson(stringParams, requestBodyJsonTemplate);
                            new ObjectMapper().writeValue(o, body);
                        } else if (requestBodyJsonTransformer != null) {
                            var parametersXml = createParametersElement("parameters", context, visibleIntermediateValues);
                            StringWriter json = new StringWriter();
                            requestBodyJsonTransformer.newTransformer().transform(
                                new DOMSource(parametersXml.getOwnerDocument()), new StreamResult(json));
                            if (DeploymentParameters.get().xsltDebugLog)
                                log.info("Result of XSLT, to send to '" + baseUrl + "'\n" + json);
                            IOUtils.write(json.toString(), o, UTF_8);
                        } else {
                            throw new RuntimeException("Unreachable");
                        }
                    }
                    catch (IOException | DocumentTemplateInvalidException | TransformerException e) {
                        throwException(baseUrl, e);
                        after.accept(null);
                    }
                };
                context.threads.addTaskOffPool(sendRequest);
                precursorTasks.add(sendRequest);
            }
            
            Runnable executeRequest = () -> {
                try (var ignored2 = new Timer("Execute HTTP request to '" + baseUrl + "'")) {
                    if (urlConnection.getResponseCode() < 200 || urlConnection.getResponseCode() >= 300) {
                        String body = null;
                        var type = urlConnection.getContentType();
                        if (type != null && (type.contains("text") || type.contains("json") || type.contains("xml"))) {
                            try (var errorStream = urlConnection.getErrorStream()) {
                                if (errorStream != null) {
                                    var responseCharset = MediaType.parse(urlConnection.getContentType()).charset().or(UTF_8);
                                    body = IOUtils.toString(errorStream, responseCharset);
                                }
                            }
                        }

                        var bodyMsg = body == null ? "" :
                            " (body was: " +
                                (body.length() > 1_000 ? body.substring(0, 1_000) + "... [truncated]" : body)
                                + ")";

                        var reasonPhrase = urlConnection.getResponseMessage() == null || urlConnection.getResponseMessage().isEmpty()
                            ? "" : " " + urlConnection.getResponseMessage();

                        throw new HttpRequestFailedException(baseUrl, urlConnection.getResponseCode(), "URL '" + baseUrl
                            + "' returned " + urlConnection.getResponseCode()
                            + reasonPhrase + bodyMsg);
                    }

                    after.accept(urlConnection);
                }
                catch (IOException | HttpRequestFailedException e) { 
                    throwException(baseUrl, e);
                    after.accept(null);
                }
            };
            context.threads.addTaskWithDependenciesOffPool(precursorTasks, executeRequest);
        }
        catch (TransformationFailedException | IOException | DocumentTemplateInvalidException | TransformerException e) {
            throwException(baseUrl, e);
            after.accept(null);
        }
    }

    /** @param after null if an error occurred and this request is set to ignore errors. Does not expand variables in response */
    public void scheduleExecutionAndParseResponse(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues,
        @Nonnull Consumer<Element> after
    ) {
        scheduleExecutionAndAssertNoError(context, visibleIntermediateValues, (@CheckForNull var urlConnection) -> {
            if (urlConnection == null) { after.accept(null); return; }

            var url = urlConnection.getURL();
    
            try {
                if (urlConnection.getContentLength() == 0) {
                    var response = DomParser.newDocumentBuilder().newDocument();
                    response.appendChild(response.createElement("empty-response"));
                    after.accept(response.getDocumentElement());
                }
                else if (urlConnection.getContentType().toLowerCase().contains("json")) {
                    try {
                        var xmlFromJson = new JsonXmlConverter().convertJsonToXml(
                            urlConnection.getContentType(), urlConnection.getInputStream(), "response");
                        after.accept(xmlFromJson);
                    }
                    catch (JSONException e) {
                        throw new HttpRequestFailedException(url.toExternalForm(), null, 
                            "Cannot parse JSON response from '" + url + "'", e);
                    }
                }
                else if (urlConnection.getContentType().toLowerCase().contains("xml")) {
                    try (var inputStream = urlConnection.getInputStream()) {
                        var downloadedXml = DomParser.newDocumentBuilder().parse(inputStream);
                        after.accept(downloadedXml.getDocumentElement());
                    }
                    catch (SAXException e) {
                        throw new HttpRequestFailedException(url.toExternalForm(), null, "URL '" + url + "' contained invalid XML", e);
                    }
                }
                else if (urlConnection.getContentType().toLowerCase().contains("html")) {
                    try (var inputStream = urlConnection.getInputStream()) {
                        var document = Jsoup.parse(inputStream, null, "");
                        document.outputSettings().syntax(xml);
                        var xmlString = document.html();
                        var xml = DomParser.from(xmlString);
                        after.accept(xml);
                    }
                    catch (ConfigurationException e) {
                        throw new HttpRequestFailedException(url.toExternalForm(), null, "URL '" + url + "': HTML could not be parsed", e);
                    }
                }
                else throw new HttpRequestFailedException(url.toExternalForm(), null, 
                        "URL '" + url + "' returned an unexpected content type " +
                        "'" + urlConnection.getContentType() + "': Expecting XML, JSON, or empty content");
            }
            catch (IOException | HttpRequestFailedException e) { 
                throwException(url.toExternalForm(), e);
                after.accept(null);
            }
        });
    }
}
