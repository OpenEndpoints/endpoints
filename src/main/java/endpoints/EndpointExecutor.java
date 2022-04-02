package endpoints;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.ThreadPool;
import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.BufferedHttpResponseDocumentGenerationDestination;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import endpoints.HttpRequestSpecification.HttpRequestFailedException;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.TransformationContext.ParameterNotFoundPolicy;
import endpoints.config.*;
import endpoints.config.ApplicationFactory.ApplicationConfig;
import endpoints.config.EndpointHierarchyNode.NodeNotFoundException;
import endpoints.config.response.*;
import endpoints.datasource.DataSourceCommandFetcher;
import endpoints.datasource.ParametersCommand;
import endpoints.datasource.TransformationFailedException;
import endpoints.generated.jooq.tables.records.RequestLogIdsRecord;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import endpoints.task.RequestLogExpressionCaptureTask;
import endpoints.task.Task;
import endpoints.task.Task.TaskExecutionFailedException;
import endpoints.task.TaskId;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.slf4j.LoggerFactory;
import org.json.JSONException;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.activation.MimetypesFileTypeMap;
import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMResult;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.InetAddress;
import java.net.URL;
import java.nio.file.Files;
import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.*;
import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;
import static com.databasesandlife.util.ThreadPool.unwrapException;
import static com.databasesandlife.util.gwtsafe.ConfigurationException.prefixExceptionMessage;
import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.*;
import static endpoints.OnDemandIncrementingNumber.newLazyNumbers;
import static endpoints.generated.jooq.Tables.*;
import static java.util.Arrays.stream;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.jooq.impl.DSL.max;

public class EndpointExecutor {

    @FunctionalInterface
    public interface Responder {
        public void respond(@Nonnull BufferedHttpResponseDocumentGenerationDestination response);
    }

    public static class EndpointExecutionFailedException extends Exception {
        public final String externalMessage;
        public final int statusCode;
        public EndpointExecutionFailedException(int statusCode, String msg, Throwable e) { this(statusCode, msg, msg, e); }
        public EndpointExecutionFailedException(int statusCode, String externalMsg, String internalMsg, Throwable e) {
            super(internalMsg, e);
            this.externalMessage = externalMsg;
            this.statusCode = statusCode;
        }
    }

    public static class RequestInvalidException extends Exception {
        public RequestInvalidException(String msg) { super(msg); }
        public RequestInvalidException(String prefix, Throwable e) { super(prefixExceptionMessage(prefix, e), e); }
    }

    protected static class ParameterTransformationHadErrorException extends Exception {
        public final @Nonnull String error;
        public ParameterTransformationHadErrorException(@Nonnull String prefix, @Nonnull String error) {
            super(prefix + ": " + error);
            this.error = error;
        }
    }
    
    protected class ParameterTransformationLogger {
        public Element input, output;
    }

    protected void appendTextElement(
        @Nonnull Element parent, @Nonnull String name, @CheckForNull String attrName, @CheckForNull String attrValue,
        @CheckForNull String contents
    ) {
        if (contents == null) return;
        var newElement = parent.getOwnerDocument().createElement(name);
        newElement.setTextContent(contents);
        if (attrName != null && attrValue != null) newElement.setAttribute(attrName, attrValue);
        parent.appendChild(newElement);
    }

    protected void appendTextElement(@Nonnull Element parent, @Nonnull String name, @CheckForNull String contents) {
        appendTextElement(parent, name, null, null, contents);
    }

    /**
     * Note: Assumes application is "locked" i.e. only one instance of this process is running on this application.
     */
    protected long getNextAutoIncrement(
        @Nonnull DbTransaction tx,
        @Nonnull ApplicationName application, @Nonnull PublishEnvironment environment, @Nonnull NodeName endpoint
    ) {
        var max = tx.jooq()
            .select(max(REQUEST_LOG_IDS.INCREMENTAL_ID_PER_ENDPOINT))
            .from(REQUEST_LOG_IDS)
            .where(REQUEST_LOG_IDS.APPLICATION.eq(application))
            .and(REQUEST_LOG_IDS.ENVIRONMENT.eq(environment))
            .and(REQUEST_LOG_IDS.ENDPOINT.eq(endpoint))
            .fetchSingle().value1();

        if (max == null) return 1;
        else return max+1;
    }

    @SneakyThrows
    public static void logXmlForDebugging(@Nonnull Class<?> logClass, @Nonnull String msg, @Nonnull Document x) {
        if ( ! DeploymentParameters.get().xsltDebugLog) return;

        StringWriter result = new StringWriter();
        StreamResult xmlOutput = new StreamResult(result);
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer t2 = transformerFactory.newTransformer();
        t2.setOutputProperty(OutputKeys.INDENT, "yes");
        t2.transform(new DOMSource(x), xmlOutput);
        
        var str = result.toString();
        if (str.length() > 20_000) str = str.substring(0, 10_000) + "[...truncated...]" + str.substring(str.length() - 10_000);
        
        LoggerFactory.getLogger(logClass).info(msg + "\n" + str);
    }
    
    @SuppressFBWarnings("SA_LOCAL_SELF_ASSIGNMENT")
    protected @Nonnull Runnable transformXmlIntoParameters(
        @Nonnull PublishEnvironment environment, @Nonnull ApplicationName applicationName,
        @Nonnull Application application, @Nonnull ApplicationTransaction tx,
        @Nonnull ThreadPool threads, @Nonnull Endpoint endpoint, @Nonnull RequestId requestId, 
        @Nonnull Request req, boolean debugAllowed, boolean debugRequested, 
        @Nonnull ParameterTransformationLogger parameterTransformationLogger,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, 
        @Nonnull ParameterTransformation parameterTransformation, long requestAutoInc, @Nonnull RandomRequestId random,
        @Nonnull Map<ParameterName, String> requestParameters,
        @Nonnull Consumer<Map<ParameterName, String>> consumeParameters,
        @Nonnull Node... inputFromRequestContents
    ) throws TransformationFailedException {
        //noinspection ConstantConditions - prevents non-thread-safe transaction from being accidentally used inside thread pool
        tx = tx;
        
        // Create input document
        var inputParametersDocument = DomParser.newDocumentBuilder().newDocument();
        inputParametersDocument.appendChild(inputParametersDocument.createElement("parameter-transformation-input"));

        // Add <input-from-request>
        var inputFromRequestElement = inputParametersDocument.createElement("input-from-request");
        inputParametersDocument.getDocumentElement().appendChild(inputFromRequestElement);
        appendTextElement(inputFromRequestElement, "endpoint", endpoint.name.name);
        if (debugRequested) inputFromRequestElement.appendChild(inputParametersDocument.createElement("debug-requested"));
        for (var n : inputFromRequestContents) inputFromRequestElement.appendChild(inputParametersDocument.importNode(n, true));
        for (var param : req.getLowercaseHttpHeadersWithoutCookies().entrySet())
            for (var value : param.getValue())
                appendTextElement(inputFromRequestElement, "http-header", "name-lowercase", param.getKey(), value);
        for (var cookie : req.getCookies())
            appendTextElement(inputFromRequestElement, "cookie", "name", cookie.getName(), cookie.getValue());
        appendTextElement(inputFromRequestElement, "ip-address",
            req.getClientIpAddress() == null ? null : req.getClientIpAddress().getHostAddress());

        // Add <input-from-application>
        var inputFromApplicationElement = inputParametersDocument.createElement("input-from-application");
        inputParametersDocument.getDocumentElement().appendChild(inputFromApplicationElement);
        @CheckForNull var databaseConfig = tx.db.jooq().selectFrom(APPLICATION_CONFIG)
            .where(APPLICATION_CONFIG.APPLICATION_NAME.eq(applicationName)).fetchOne();
        appendTextElement(inputFromApplicationElement, "application", applicationName.name);
        appendTextElement(inputFromApplicationElement, "application-display-name", 
            databaseConfig == null ? null : databaseConfig.getDisplayName());
        if (debugAllowed) inputFromApplicationElement.appendChild(inputParametersDocument.createElement("debug-allowed"));
        appendTextElement(inputFromApplicationElement, "secret-key", application.getSecretKeys()[0]);
        appendTextElement(inputFromApplicationElement, "incremental-id-per-endpoint", Long.toString(requestAutoInc));
        appendTextElement(inputFromApplicationElement, "random-id-per-application", Long.toString(random.getId()));
        appendTextElement(inputFromApplicationElement, "base-url", DeploymentParameters.get().baseUrl.toExternalForm());

        // Schedule execution of e.g. <xml-from-application>
        var context = new TransformationContext(environment, applicationName, application, tx, threads, requestParameters,
            ParameterNotFoundPolicy.emptyString, requestId, req, autoInc, new HashMap<>());
        var dataSourceResults = new ArrayList<DataSourceCommandFetcher>();
        for (var c : parameterTransformation.dataSourceCommands)
            dataSourceResults.add(c.scheduleExecution(context, Set.of()));

        return threads.addTaskWithDependencies(dataSourceResults, () -> {
            try {
                // Add results of e.g. <xml-from-application>
                for (var r : dataSourceResults)
                    for (var element : r.get())
                        inputParametersDocument.getDocumentElement().appendChild(
                            inputParametersDocument.importNode(element, true));

                // Debug Log
                LoggerFactory.getLogger(getClass()).debug("Parameter Transformation Input:\n" 
                    + formatXmlPretty(inputParametersDocument.getDocumentElement()));

                // Transform
                boolean debug = debugAllowed && debugRequested;
                if (debug) parameterTransformationLogger.input = inputParametersDocument.getDocumentElement();
                var outputParametersDocument = new DOMResult();
                parameterTransformation.xslt.newTransformer()
                    .transform(new DOMSource(inputParametersDocument), outputParametersDocument);

                // Parse and return output
                var outputParametersRoot = ((Document) outputParametersDocument.getNode()).getDocumentElement();
                if (debug) parameterTransformationLogger.output = outputParametersRoot;
                if (outputParametersRoot == null)
                    throw new ConfigurationException("Parameter transformation delivered empty document");
                var error = getOptionalSingleSubElement(outputParametersRoot, "error");
                if (error != null) throw new ParameterTransformationHadErrorException("Parameter transformation " +
                    "contained <error>", error.getTextContent());
                if (getOptionalSingleSubElement(outputParametersRoot, "status") != null)
                    throw new RuntimeException("Please update parameter transformation to new format. " +
                        "Parameter transformation XSLT returned <status> (this is the old format). " +
                        "New format is to return <error> or nothing.");
                assertNoOtherElements(outputParametersRoot, "error", "parameter");

                var result = new HashMap<ParameterName, String>();
                var endpointsParameters = new HashSet<>(endpoint.aggregateParametersOverParents().keySet());
                TransformationContext.getSystemParameterNames().forEach(x -> endpointsParameters.add(new ParameterName(x)));
                for (var parameterElement : getSubElements(outputParametersRoot, "parameter")) {
                    var paramName = new ParameterName(getMandatoryAttribute(parameterElement, "name"));
                    if ( ! endpointsParameters.contains(paramName))
                        throw new RequestInvalidException("Parameter transformation XSLT " +
                            "produced <parameter name='"+paramName.name+"'../> but this parameter " +
                            "isn't declared in 'endpoints.xml'");
                    result.put(paramName, getMandatoryAttribute(parameterElement, "value"));
                }

                consumeParameters.accept(result);
            }
            catch (ConfigurationException e) { throw new RuntimeException(new RequestInvalidException(
                "While processing result of parameter transformation", e)); }
            catch (DocumentTemplateInvalidException | TransformerException |
                ParameterTransformationHadErrorException | RequestInvalidException e) { throw new RuntimeException(e); }
        });
    }
    
    @SneakyThrows(IOException.class)
    protected @Nonnull Node[] convertJsonToXml(@Nonnull String contentType, @Nonnull InputStream jsonInputStream) 
    throws RequestInvalidException {
        try {
            var rootNode = new JsonToXmlConverter().convert(contentType, jsonInputStream, "json-request");
            var nodeList = rootNode.getChildNodes();
            
            var result = new Node[nodeList.getLength()];
            for (int i = 0; i < nodeList.getLength(); i++) result[i] = nodeList.item(i);

            LoggerFactory.getLogger(getClass()).debug("POST request JSON converted to XML, output is:\n" + Arrays.stream(result)
                .map(x -> x instanceof Element ? formatXmlPretty((Element) x) : "(not an element)")
                .collect(joining("\n")));

            return result;
        }
        catch (JSONException e) { throw new RequestInvalidException("Cannot parse JSON from POST request", e); }
    }
    
    /** Places all the children into a new container element */
    protected @Nonnull Element encloseElement(@Nonnull String surroundingTag, @Nonnull Node... children) {
        var doc = DomParser.newDocumentBuilder().newDocument();
        var result = doc.createElement(surroundingTag);
        for (var c : children) result.appendChild(doc.importNode(c, true));
        return result;
    }

    @SuppressWarnings("UnusedReturnValue") 
    protected @Nonnull Runnable getParameters(
        @Nonnull PublishEnvironment environment, @Nonnull ApplicationName applicationName,
        @Nonnull Application application, @Nonnull ApplicationTransaction tx,
        @Nonnull ThreadPool threads, @Nonnull Endpoint endpoint, @Nonnull RequestId requestId,
        @Nonnull Request req, boolean debugAllowed, boolean debugRequested,
        @Nonnull ParameterTransformationLogger parameterTransformationLogger, 
        long requestAutoInc, @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc,
        @Nonnull RandomRequestId random, @Nonnull Consumer<Map<ParameterName, String>> consumeParameters
    ) throws RequestInvalidException, TransformationFailedException, EndpointExecutionFailedException {
        Consumer<Map<ParameterName, String>> validateThenConsumeParameters = (transformedParameters) -> {
            try {
                // Apply <parameter> definition from endpoints.xml
                var checkedParameters = new HashMap<ParameterName, String>();
                for (var paramEntry : endpoint.aggregateParametersOverParents().entrySet()) {
                    var param = paramEntry.getKey();
                    var defn = paramEntry.getValue();
    
                    if (transformedParameters.containsKey(param)) checkedParameters.put(param, transformedParameters.get(param));
                    else if (defn.defaultValueOrNull != null) checkedParameters.put(param, defn.defaultValueOrNull);
                    else throw new RequestInvalidException("Endpoint '" + endpoint.name.name + "': " +
                        "Parameter '"+param.name+"' did not have a supplied value, nor a default");
                }
                consumeParameters.accept(checkedParameters);
            }
            catch (RequestInvalidException e) { throw new RuntimeException(e); }
        };

        var contentType = req.getContentTypeIfPost();
        if (contentType == null 
                || contentType.equals("application/x-www-form-urlencoded") 
                || contentType.equals("multipart/form-data")) {
            var inputParameters = req.getParameters().entrySet().stream().collect(toMap(
                e -> e.getKey(),
                e -> e.getValue().stream().collect(joining(endpoint.getParameterMultipleValueSeparator()))
            ));
            if (endpoint.parameterTransformation == null) {
                return threads.addTask(() -> validateThenConsumeParameters.accept(inputParameters));
            } else {
                return transformXmlIntoParameters(environment, applicationName, application, tx, threads, endpoint, requestId, req,
                    debugAllowed, debugRequested, parameterTransformationLogger, autoInc,
                    endpoint.parameterTransformation, requestAutoInc, random, inputParameters, validateThenConsumeParameters,
                    ParametersCommand.createParametersElements(inputParameters, Map.of(), req.getUploadedFiles()));
            }
        }
        else if (contentType.contains("xml") || contentType.contains("json")) {
            try {
                if (endpoint.parameterTransformation == null) throw new RequestInvalidException("Endpoint does not have " +
                    "<parameter-transformation> defined, therefore cannot accept XML or JSON request " +
                    "with Content-Type '" + req.getContentTypeIfPost() + "'");
                final @Nonnull Element requestDocument;
                if (contentType.contains("xml"))
                    requestDocument = encloseElement("xml", DomParser.from(req.getInputStream()));
                else if (contentType.contains("json")) 
                    requestDocument = encloseElement("json", convertJsonToXml(contentType, req.getInputStream()));
                else throw new RuntimeException("Unreachable; contentType='" + contentType + "'");
                return transformXmlIntoParameters(environment, applicationName, application, tx, threads, endpoint, requestId, req,
                    debugAllowed, debugRequested, parameterTransformationLogger, autoInc,
                    endpoint.parameterTransformation, requestAutoInc, random, Map.of(),
                    validateThenConsumeParameters, requestDocument);
            }
            catch (ConfigurationException e) { throw new RequestInvalidException("Request is not valid XML", e); }
        }
        else throw new RequestInvalidException("Unexpected Content Type '" + req.getContentTypeIfPost()
            + "': Must either be a GET request, or a POST request from a <form>, or POST request with XML or JSON body");
    }

    protected void assertHashCorrect(
        @Nonnull Application application, @Nonnull PublishEnvironment environment, @Nonnull Endpoint endpoint,
        @Nonnull Map<ParameterName, String> parameters, @Nonnull String suppliedHash
    ) throws RequestInvalidException {
        var expectedHashes = stream(application.getSecretKeys())
            .map(secretKey -> endpoint.parametersForHash.calculateHash(secretKey, environment, endpoint.name, parameters))
            .collect(Collectors.toSet());
        if (DeploymentParameters.get().checkHash && ! expectedHashes.contains(suppliedHash.toLowerCase())) {
            if (DeploymentParameters.get().displayExpectedHash)
                throw new RequestInvalidException("Expected hash '"+expectedHashes.iterator().next()
                    +"' (won't be displayed on live system)");
            else throw new RequestInvalidException("Hash wrong");
        }
    }
    
    @RequiredArgsConstructor
    protected class Response implements Runnable {
        protected final @Nonnull TransformationContext context;
        protected final @Nonnull ResponseConfiguration config;
        protected final boolean success;
        protected final @Nonnull Consumer<BufferedHttpResponseDocumentGenerationDestination> responseConsumer;
        
        @SneakyThrows({IOException.class, RequestInvalidException.class, TransformationFailedException.class})
        public void runUnconditionally() {
            var destination = new BufferedHttpResponseDocumentGenerationDestination();
            int contentStatusCode = success ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST;
            var stringParams = context.getStringParametersIncludingIntermediateValues(config.inputIntermediateValues);

            if (config instanceof EmptyResponseConfiguration) {
                destination.setStatusCode(contentStatusCode);
            }
            else if (config instanceof StaticResponseConfiguration) {
                var r = (StaticResponseConfiguration) config;
                destination.setStatusCode(contentStatusCode);
                destination.setContentType(new MimetypesFileTypeMap().getContentType(r.file));
                try (var o = destination.getOutputStream()) { Files.copy(r.file.toPath(), o); }
                if (r.downloadFilenamePatternOrNull != null)
                    destination.setContentDispositionToDownload(
                        replacePlainTextParameters(r.downloadFilenamePatternOrNull, stringParams));
            }
            else if (config instanceof UrlResponseConfiguration) {
                var r = (UrlResponseConfiguration) config;
                destination.setStatusCode(contentStatusCode);
                r.spec.scheduleExecutionAndAssertNoError(context, config.inputIntermediateValues, (@CheckForNull var result) -> {
                    if (result != null) {
                        destination.setContentType(result.getContentType());
                        try (var i = result.getInputStream(); var o = destination.getOutputStream()) { IOUtils.copy(i, o); }
                        catch (IOException e) { throw new RuntimeException(e); }
                    }
                });
                if (r.downloadFilenamePatternOrNull != null)
                    destination.setContentDispositionToDownload(
                        replacePlainTextParameters(r.downloadFilenamePatternOrNull, stringParams));
            }
            else if (config instanceof RedirectResponseConfiguration) {
                var url = replacePlainTextParameters(((RedirectResponseConfiguration)config).urlPattern, stringParams);
                if (!((RedirectResponseConfiguration)config).whitelist.isUrlInWhiteList(url))
                    throw new RequestInvalidException("Redirect URL '"+url+"' is not in whitelist");
                destination.setRedirectUrl(new URL(url));
            }
            else if (config instanceof TransformationResponseConfiguration) {
                var r = (TransformationResponseConfiguration) config;
                destination.setStatusCode(contentStatusCode);
                r.transformer.scheduleExecution(context, config.inputIntermediateValues, destination);
                if (r.downloadFilenamePatternOrNull != null)
                    destination.setContentDispositionToDownload(
                        replacePlainTextParameters(r.downloadFilenamePatternOrNull, stringParams));
            }
            else throw new IllegalStateException("Unexpected config: " + config);
            
            responseConsumer.accept(destination);
        }

        @Override 
        public void run() {
            var stringParams = context.getStringParametersIncludingIntermediateValues(config.inputIntermediateValues);

            boolean satisfiesCondition = config.satisfiesCondition(stringParams);
            synchronized (context) {
                if (context.alreadyDeliveredResponse || !satisfiesCondition) return;
                context.alreadyDeliveredResponse = true;
            }

            runUnconditionally();
        }
    }

    protected class ResponseIncludingForward extends Response {
        protected final @Nonnull PublishEnvironment environment;
        protected final @Nonnull ApplicationName applicationName;
        protected final @Nonnull Application application;
        protected final @Nonnull ApplicationConfig appConfig;
        protected final long requestAutoInc;
        protected final @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc;
        protected final @Nonnull RandomRequestId random;
        
        public ResponseIncludingForward(
            @Nonnull PublishEnvironment environment, @Nonnull ApplicationName applicationName,
            @Nonnull Application application, @Nonnull TransformationContext context,
            @Nonnull ResponseConfiguration config, boolean success, @Nonnull ApplicationConfig appConfig, long requestAutoInc,
            @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, @Nonnull RandomRequestId random,
            @Nonnull Consumer<BufferedHttpResponseDocumentGenerationDestination> responseConsumer
        ) {
            super(context, config, success, responseConsumer);
            this.environment = environment;
            this.applicationName = applicationName;
            this.application = application;
            this.appConfig = appConfig;
            this.requestAutoInc = requestAutoInc;
            this.autoInc = autoInc;
            this.random = random;
        }

        @SneakyThrows({RequestInvalidException.class, TransformationFailedException.class, NodeNotFoundException.class,
            EndpointExecutionFailedException.class})
        @Override public void runUnconditionally() {
            var stringParams = context.getStringParametersIncludingIntermediateValues(config.inputIntermediateValues);

            if (config instanceof ForwardToEndpointResponseConfiguration) {
                var request = new Request() {
                    @Override public @CheckForNull InetAddress getClientIpAddress() { return context.request.getClientIpAddress(); }
                    @Override public @Nonnull Map<String, List<String>> getLowercaseHttpHeadersWithoutCookies() {
                        return context.request.getLowercaseHttpHeadersWithoutCookies(); } 
                    @Override public @Nonnull List<Cookie> getCookies() { return context.request.getCookies(); }
                    @Override public @Nonnull String getUserAgent() { return context.request.getUserAgent(); }
                    @Override public @CheckForNull String getContentTypeIfPost() { return null; }
                    @Override public @Nonnull List<? extends UploadedFile> getUploadedFiles() { 
                        return context.request.getUploadedFiles(); }
                    @Override public @Nonnull InputStream getInputStream() { throw new IllegalStateException(); }
                    @Override public @Nonnull Map<ParameterName, List<String>> getParameters() {
                        var patterns = ((ForwardToEndpointResponseConfiguration) config).inputParameterPatterns;
                        return patterns == null
                            ? stringParams.entrySet().stream().collect(
                                toMap(e -> new ParameterName(e.getKey()), e -> List.of(e.getValue()))) 
                            : patterns.entrySet().stream().collect(
                                toMap(e -> e.getKey(), e -> List.of(replacePlainTextParameters(e.getValue(), stringParams))));
                    }
                };

                attemptSuccess(environment, applicationName, application, appConfig,
                    application.getEndpoints().findEndpointOrThrow(((ForwardToEndpointResponseConfiguration) config).endpoint),
                    context.tx, context.threads, false, new ParameterTransformationLogger(), requestAutoInc, autoInc,
                    context.requestLogExpressionCaptures, random, null, context.requestId, request, responseConsumer);
            }
            else super.runUnconditionally();
        }
    }

    protected void insertRequestLog(
        @Nonnull DbTransaction tx,
        @Nonnull ApplicationName applicationName, @Nonnull PublishEnvironment environment, @Nonnull NodeName endpointName,
        @Nonnull Instant now, @Nonnull RequestId requestId, 
        @Nonnull Request req, @Nonnull ParameterTransformationLogger parameterTransformationLogger, 
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, 
        @Nonnull Map<String, String> requestLogExpressionCaptures,
        @Nonnull BufferedHttpResponseDocumentGenerationDestination response,
        @Nonnull Consumer<RequestLogIdsRecord> alterRequestLogIds, @Nonnull Consumer<RequestLogRecord> alterRequestLog
    ) {
        var ids = new RequestLogIdsRecord();
        ids.setRequestId(requestId);
        ids.setApplication(applicationName);
        ids.setEnvironment(environment);
        ids.setEndpoint(endpointName);
        ids.setOnDemandPerpetualIncrementingNumber(autoInc.get(perpetual).getValueOrNull());
        ids.setOnDemandYearIncrementingNumber(autoInc.get(year).getValueOrNull());
        ids.setOnDemandMonthIncrementingNumber(autoInc.get(month).getValueOrNull());
        alterRequestLogIds.accept(ids);
        tx.insert(ids);
        
        var r = new RequestLogRecord();
        r.setRequestId(requestId);
        r.setDatetime(now);
        r.setStatusCode(response.getStatusCode());
        r.setUserAgent(req.getUserAgent());
        r.setParameterTransformationInput(parameterTransformationLogger.input);
        r.setParameterTransformationOutput(parameterTransformationLogger.output);
        alterRequestLog.accept(r);
        tx.insert(r);
        
        RequestLogExpressionCaptureTask.writeToDb(tx, requestId, requestLogExpressionCaptures);
    }
    
    protected @Nonnull Response scheduleTasksAndSuccess(
        @Nonnull PublishEnvironment environment, @Nonnull ApplicationName applicationName, @Nonnull ApplicationConfig appConfig,
        @Nonnull TransformationContext context, @Nonnull Endpoint endpoint,
        long requestAutoInc, @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc,
        @Nonnull RandomRequestId random, @Nonnull Consumer<BufferedHttpResponseDocumentGenerationDestination> responseConsumer
    ) {
        var synchronizationPointForTaskId = new HashMap<TaskId, SynchronizationPoint>();
        var synchronizationPointForOutputValue = new HashMap<IntermediateValueName, SynchronizationPoint>();
        @SuppressWarnings("Convert2Diamond")  // IntelliJ Windows requires the <Task> here
        var tasksToSchedule = new ArrayList<Task>(endpoint.tasks);
        
        var infiniteLoopProtection = 0;
        while ( ! tasksToSchedule.isEmpty())
            tasks: for (var tasksToScheduleIter = tasksToSchedule.iterator(); tasksToScheduleIter.hasNext(); ) {
                var task = tasksToScheduleIter.next();
                var dependencies = new ArrayList<SynchronizationPoint>();
                
                // What tasks do we depend on? Ignore the task for now in case our dependencies haven't been scheduled yet
                for (var predecessor : task.predecessors) {
                    if ( ! synchronizationPointForTaskId.containsKey(predecessor)) continue tasks;
                    dependencies.add(synchronizationPointForTaskId.get(predecessor));
                }
                for (var neededInputValue : task.inputIntermediateValues) {
                    if ( ! synchronizationPointForOutputValue.containsKey(neededInputValue)) continue tasks;
                    dependencies.add(synchronizationPointForOutputValue.get(neededInputValue));
                }
                
                // Schedule the task (after our dependencies)
                var taskRunnable = task.scheduleTaskExecutionIfNecessary(dependencies, context);
                
                // Remove the task from the list of tasks still to schedule
                tasksToScheduleIter.remove();
                
                // Record our runnable for this task, in case future tasks need to depend on us
                if (task.getTaskIdOrNull() != null)
                    synchronizationPointForTaskId.put(task.getTaskIdOrNull(), taskRunnable);
                for (var outputValue : task.getOutputIntermediateValues()) 
                    synchronizationPointForOutputValue.put(outputValue, taskRunnable);
            }

            infiniteLoopProtection++;
            if (infiniteLoopProtection >= 1_000) {
                throw new RuntimeException("Unreachable: Probably circular dependency of task intermediate variables");
        }

        ResponseIncludingForward previousResponse = null;
        for (var success : endpoint.success) {
            var dependencies = new ArrayList<Runnable>();
            if (previousResponse != null)
                dependencies.add(previousResponse);
            for (var predecessor : success.predecessors) 
                dependencies.add(synchronizationPointForTaskId.get(predecessor));
            for (var neededInputValue : success.inputIntermediateValues) 
                dependencies.add(synchronizationPointForOutputValue.get(neededInputValue));

            var thisResponse = new ResponseIncludingForward(environment, applicationName, context.application,
                context, success, true, appConfig, requestAutoInc, autoInc, random, responseConsumer);
            context.threads.addTaskWithDependencies(dependencies, thisResponse);
            
            previousResponse = thisResponse;
        }

        if (previousResponse == null) throw new RuntimeException("Unreachable: no <success> responses scheduled");
        return previousResponse;
    }
    
    protected void attemptSuccess(
        @Nonnull PublishEnvironment environment, @Nonnull ApplicationName applicationName,
        @Nonnull Application application, @Nonnull ApplicationConfig appConfig, @Nonnull Endpoint endpoint,
        @Nonnull ApplicationTransaction tx, @Nonnull ThreadPool threads,
        boolean debugRequested, @Nonnull ParameterTransformationLogger parameterTransformationLogger,
        long requestAutoInc, @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc,
        @Nonnull Map<String, String> requestLogExpressionCaptures, @Nonnull RandomRequestId random,
        @CheckForNull String hashToCheck, @Nonnull RequestId requestId, @Nonnull Request req,
        @Nonnull Consumer<BufferedHttpResponseDocumentGenerationDestination> responseConsumer
    ) throws EndpointExecutionFailedException, RequestInvalidException, TransformationFailedException {
        getParameters(environment, applicationName, application, tx, threads, endpoint, requestId, req,
            appConfig.debugAllowed, debugRequested, parameterTransformationLogger,
            requestAutoInc, autoInc, random, parameters -> {
                try {
                    if (hashToCheck != null) assertHashCorrect(application, environment, endpoint, parameters, hashToCheck);
                    
                    var context = new TransformationContext(environment, applicationName, application, tx, threads, parameters,
                        ParameterNotFoundPolicy.error, requestId, req, autoInc, requestLogExpressionCaptures);
                    scheduleTasksAndSuccess(environment, applicationName, appConfig,
                        context, endpoint, requestAutoInc, autoInc, random, responseConsumer);
                }
                catch (RequestInvalidException e) { throw new RuntimeException(e); }
            }
        );
    }

    /**
     * @param hashToCheck null if not to check hash (e.g. service portal is calling this, so no hash check)
     */
    public void execute(
        @Nonnull PublishEnvironment environment, @Nonnull ApplicationName applicationName,
        @Nonnull Application application, @Nonnull Endpoint endpoint, boolean debugRequested,
        @CheckForNull String hashToCheck, @Nonnull Request req, @Nonnull Responder responder
    ) throws EndpointExecutionFailedException {
        try (var ignored = new Timer(getClass().getSimpleName())) {
            var now = Instant.now();
            var requestId = RequestId.newRandom();
            
            var parameterTransformationLogger = new ParameterTransformationLogger();
            
            try (var tx = new ApplicationTransaction(application);
                 var ignored2 = new Timer("<success> for application='"+applicationName.name+"', endpoint='"+endpoint.name.name+"'")) {
                
                var threads = new ThreadPool();
                threads.setThreadNamePrefix("<success>");
                
                var appConfig = DeploymentParameters.get().getApplications(tx.db).fetchApplicationConfig(tx.db, applicationName);
                
                if (appConfig.locked) throw new RequestInvalidException("Application is locked");

                try (var ignored3 = new Timer("Acquire lock on '" + applicationName.name 
                        + "', environment '" + environment.name() + "'")) {
                    tx.db.jooq().select().from(APPLICATION_PUBLISH)
                        .where(APPLICATION_PUBLISH.APPLICATION_NAME.eq(applicationName))
                        .and(APPLICATION_PUBLISH.ENVIRONMENT.eq(environment))
                        .forUpdate().execute();
                }

                var requestAutoInc = getNextAutoIncrement(tx.db, applicationName, environment, endpoint.name);
                var autoInc = newLazyNumbers(applicationName, environment, now);
                var requestLogExpressionCaptures = new HashMap<String, String>();
                var random = RandomRequestId.generate(tx.db, applicationName, environment);

                var successResponse = new Consumer<BufferedHttpResponseDocumentGenerationDestination>() {
                    public BufferedHttpResponseDocumentGenerationDestination destination;
                    @Override public void accept(BufferedHttpResponseDocumentGenerationDestination d) { destination = d; } 
                };
                
                attemptSuccess(environment, applicationName, application, appConfig, 
                    endpoint, tx, threads, debugRequested, parameterTransformationLogger, requestAutoInc, autoInc, 
                    requestLogExpressionCaptures, random, hashToCheck, requestId, req, successResponse);

                try { threads.execute(); }
                catch (RuntimeException e) {
                    unwrapException(e, RequestInvalidException.class);
                    unwrapException(e, TransformationFailedException.class);
                    unwrapException(e, EndpointExecutionFailedException.class);
                    unwrapException(e, HttpRequestFailedException.class);
                    unwrapException(e, TaskExecutionFailedException.class);
                    unwrapException(e, ParameterTransformationHadErrorException.class);
                    throw e;
                }

                insertRequestLog(tx.db, applicationName, environment, endpoint.name, now, requestId, req,
                    parameterTransformationLogger, autoInc, requestLogExpressionCaptures, successResponse.destination, r -> {
                        r.setIncrementalIdPerEndpoint(requestAutoInc);
                        r.setRandomIdPerApplication(random);
                    }, r -> {});

                tx.commit();
                responder.respond(successResponse.destination);
            }
            catch (Exception e) {
                try (var tx = new ApplicationTransaction(application);
                     var ignored2 = new Timer("<error> for application='"+applicationName.name+"', endpoint='"+endpoint.name.name+"'")) {
                    LoggerFactory.getLogger(getClass()).warn("Delivering error", e);

                    var errorResponse = new Consumer<BufferedHttpResponseDocumentGenerationDestination>() {
                        public BufferedHttpResponseDocumentGenerationDestination destination;
                        @Override public void accept(BufferedHttpResponseDocumentGenerationDestination d) { destination = d; }
                    };

                    var errorExpansionValues = new HashMap<ParameterName, String>();
                    errorExpansionValues.put(new ParameterName("internal-error-text"), 
                        Optional.ofNullable(e.getMessage()).orElse(""));
                    errorExpansionValues.put(new ParameterName("parameter-transformation-error-text"),
                        e instanceof ParameterTransformationHadErrorException
                            ? ((ParameterTransformationHadErrorException) e).error : "");

                    var threads = new ThreadPool();
                    threads.setThreadNamePrefix("<error>");
                    var autoInc = newLazyNumbers(applicationName, environment, now);
                    var context = new TransformationContext(environment, applicationName, application, tx, 
                        threads, errorExpansionValues, ParameterNotFoundPolicy.error, requestId, req, autoInc, new HashMap<>());
                    threads.addTask(new Response(context, endpoint.error, false, errorResponse));
                    
                    try { threads.execute(); }
                    catch (RuntimeException e2) {
                        unwrapException(e2, RequestInvalidException.class);
                        unwrapException(e2, TransformationFailedException.class);
                        throw e2;
                    }

                    insertRequestLog(tx.db, applicationName, environment, endpoint.name, now, requestId, req,
                        parameterTransformationLogger, autoInc, context.requestLogExpressionCaptures, 
                        errorResponse.destination, r -> {}, r -> {
                            r.setExceptionMessage(e.getMessage());
                            r.setHttpRequestFailedUrl(e instanceof HttpRequestFailedException
                                ? (((HttpRequestFailedException) e).url) : null);
                            r.setHttpRequestFailedStatusCode(e instanceof HttpRequestFailedException
                                ? (((HttpRequestFailedException) e).responseStatusCode) : null);
                            r.setXsltParameterErrorMessage(e instanceof ParameterTransformationHadErrorException
                                ? ((ParameterTransformationHadErrorException) e).error : null);
                        });

                    tx.commit();
                    responder.respond(errorResponse.destination);
                }
                catch (RequestInvalidException | TransformationFailedException ee) {
                    throw new EndpointExecutionFailedException(500, "Error occurred; but <error> has an error: "+ee, ee);
                }
            }
        }
        catch (Exception e) { throw new EndpointExecutionFailedException(500, "An internal error occurred", e); }
    }
}
