package endpoints;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.ThreadPool;
import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.BufferedHttpResponseDocumentGenerationDestination;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import endpoints.HttpRequestSpecification.HttpRequestFailedException;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.TransformationContext.ParameterNotFoundPolicy;
import endpoints.config.*;
import endpoints.config.ApplicationFactory.ApplicationConfig;
import endpoints.config.EndpointHierarchyNode.NodeNotFoundException;
import endpoints.datasource.DataSourceCommandFetcher;
import endpoints.datasource.ParametersCommand;
import endpoints.datasource.TransformationFailedException;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import endpoints.task.Task;
import endpoints.task.Task.TaskExecutionFailedException;
import endpoints.task.TaskId;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.apache.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
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
import static java.util.Collections.*;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.jooq.impl.DSL.max;

public class EndpointExecutor {

    public interface Request {
        @CheckForNull InetAddress getClientIpAddress();
        /** Can return empty string */
        @Nonnull String getUserAgent();
        @CheckForNull String getContentTypeIfPost();
        @Nonnull Map<ParameterName, List<String>> getParameters();
        @Nonnull List<? extends UploadedFile> getUploadedFiles();
        @Nonnull InputStream getInputStream() throws EndpointExecutionFailedException;
    }
    
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

    protected void appendTextElement(@Nonnull Element parent, @Nonnull String name, @CheckForNull String contents) {
        if (contents == null) return;
        var newElement = parent.getOwnerDocument().createElement(name);
        newElement.setTextContent(contents);
        parent.appendChild(newElement);
    }

    protected void appendTextElement(@Nonnull Element parent, @Nonnull String name, @CheckForNull Integer contents) {
        if (contents == null) return;
        appendTextElement(parent, name, Integer.toString(contents));
    }

    protected long getNextAutoIncrement(
        @Nonnull DbTransaction tx,
        @Nonnull ApplicationName app, @Nonnull PublishEnvironment env, @Nonnull NodeName endp
    ) {
        var max = tx.jooq()
            .select(max(REQUEST_LOG.INCREMENTAL_ID_PER_ENDPOINT))
            .from(REQUEST_LOG)
            .where(REQUEST_LOG.APPLICATION.eq(app))
            .and(REQUEST_LOG.ENVIRONMENT.eq(env))
            .and(REQUEST_LOG.ENDPOINT.eq(endp))
            .fetchOne().value1();

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
        
        Logger.getLogger(logClass).info(msg + "\n" + str);
    }
    
    protected @Nonnull Runnable transformXmlIntoParameters(
        @Nonnull ApplicationName applicationName, @Nonnull Application application, @Nonnull ApplicationTransaction tx,
        @Nonnull ThreadPool threads,
        @Nonnull Endpoint endpoint, @Nonnull Request req, boolean debugAllowed, boolean debugRequested, 
        @Nonnull ParameterTransformationLogger parameterTransformationLogger,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, 
        @Nonnull ParameterTransformation parameterTransformation, long autoIncrement, @Nonnull RandomRequestId random,
        @Nonnull Map<ParameterName, String> requestParameters,
        @Nonnull Consumer<Map<ParameterName, String>> consumeParameters,
        @Nonnull Node... inputFromRequestContents
    ) throws TransformationFailedException {
        // Create input document
        var inputParametersDocument = DomParser.newDocumentBuilder().newDocument();
        inputParametersDocument.appendChild(inputParametersDocument.createElement("parameter-transformation-input"));

        // Add <input-from-request>
        var inputFromRequestElement = inputParametersDocument.createElement("input-from-request");
        inputParametersDocument.getDocumentElement().appendChild(inputFromRequestElement);
        appendTextElement(inputFromRequestElement, "endpoint", endpoint.name.name);
        if (debugRequested) inputFromRequestElement.appendChild(inputParametersDocument.createElement("debug-requested"));
        for (var n : inputFromRequestContents) inputFromRequestElement.appendChild(inputParametersDocument.importNode(n, true));
        appendTextElement(inputFromRequestElement, "http-header-user-agent", req.getUserAgent());
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
        appendTextElement(inputFromApplicationElement, "incremental-id-per-endpoint", Long.toString(autoIncrement));
        appendTextElement(inputFromApplicationElement, "random-id-per-application", Long.toString(random.getId()));

        // Schedule execution of e.g. <xml-from-application>
        var context = new TransformationContext(application, tx, threads, requestParameters,
            ParameterNotFoundPolicy.emptyString, req.getUploadedFiles(), autoInc);
        var dataSourceResults = new ArrayList<DataSourceCommandFetcher>();
        for (var c : parameterTransformation.dataSourceCommands)
            dataSourceResults.add(c.scheduleExecution(context, emptySet()));

        return threads.addTaskWithDependencies(dataSourceResults, () -> {
            try {
                // Add results of e.g. <xml-from-application>
                for (var r : dataSourceResults)
                    for (var element : r.get())
                        inputParametersDocument.getDocumentElement().appendChild(
                            inputParametersDocument.importNode(element, true));

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
                var endpointsParameters = endpoint.aggregateParametersOverParents().keySet();
                for (var parameterElement : getSubElements(outputParametersRoot, "parameter")) {
                    var paramName = new ParameterName(getMandatoryAttribute(parameterElement, "name"));
                    if ( ! endpointsParameters.contains(paramName))
                        throw new RequestInvalidException("Parameter transformation XSLT " +
                            "produced <parameter name='"+paramName.name+"'../> but this parameter " +
                            "isn't declared in the endpoints.xml");
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

    @SuppressWarnings("UnusedReturnValue") 
    protected @Nonnull Runnable getParameters(
        @Nonnull ApplicationName applicationName, @Nonnull Application application, @Nonnull ApplicationTransaction tx,
        @Nonnull ThreadPool threads, @Nonnull Endpoint endpoint, @Nonnull Request req, boolean debugAllowed, boolean debugRequested,
        @Nonnull ParameterTransformationLogger parameterTransformationLogger, 
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc,
        long autoIncrement, @Nonnull RandomRequestId random, @Nonnull Consumer<Map<ParameterName, String>> consumeParameters
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

        switch (Optional.ofNullable(req.getContentTypeIfPost()).orElse("null")) {
            case "null":
            case "application/x-www-form-urlencoded":
            case "multipart/form-data":
                var inputParameters = req.getParameters().entrySet().stream().collect(toMap(
                    e -> e.getKey(),
                    e -> e.getValue().stream().collect(joining(endpoint.getParameterMultipleValueSeparator()))
                ));
                if (endpoint.parameterTransformation == null) {
                    return threads.addTask(() -> validateThenConsumeParameters.accept(inputParameters));
                } else {
                    return transformXmlIntoParameters(applicationName, application, tx, threads, endpoint, req,
                        debugAllowed, debugRequested, parameterTransformationLogger, autoInc,
                        endpoint.parameterTransformation, autoIncrement, random, inputParameters, validateThenConsumeParameters, 
                        ParametersCommand.createParametersElements(inputParameters, emptyMap(), req.getUploadedFiles()));
                }

            case "application/xml":
                try {
                    if (endpoint.parameterTransformation == null) throw new RequestInvalidException("Endpoint does not have " +
                        "<parameter-transformation> defined, therefore cannot accept XML request");
                    var requestDocument = DomParser.from(req.getInputStream());
                    return transformXmlIntoParameters(applicationName, application, tx, threads, endpoint, req,
                        debugAllowed, debugRequested, parameterTransformationLogger, autoInc,
                        endpoint.parameterTransformation, autoIncrement, random, emptyMap(),
                        validateThenConsumeParameters, requestDocument);
                }
                catch (ConfigurationException e) { throw new RequestInvalidException("Request is not valid XML", e); }

            default:
                throw new RequestInvalidException("Unexpected content type '" + req.getContentTypeIfPost()
                    + "': expected 'application/x-www-form-urlencoded', 'multipart/form-data' or 'application/xml'");
        }
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

            if (config instanceof EmptyResponseConfiguration) {
                int statusCode = success ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST;
                destination.setStatusCode(statusCode);
            }
            else if (config instanceof RedirectResponseConfiguration) {
                var stringParams = context.getStringParametersIncludingIntermediateValues(config.inputIntermediateValues);
                var url = replacePlainTextParameters(((RedirectResponseConfiguration)config).urlPattern, stringParams);
                if (!((RedirectResponseConfiguration)config).whitelist.isUrlInWhiteList(url))
                    throw new RequestInvalidException("Redirect URL '"+url+"' is not in whitelist");
                destination.setRedirectUrl(new URL(url));
            }
            else if (config instanceof TransformationResponseConfiguration) {
                int statusCode = success ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST;
                var stringParams = context.getStringParametersIncludingIntermediateValues(config.inputIntermediateValues);
                var r = (TransformationResponseConfiguration) config;
                destination.setStatusCode(statusCode);
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
        protected final @Nonnull Long autoIncrement;
        protected final @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc;
        protected final @Nonnull RandomRequestId random;
        
        public ResponseIncludingForward(
            @Nonnull PublishEnvironment environment, @Nonnull ApplicationName applicationName,
            @Nonnull Application application, @Nonnull TransformationContext context,
            @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, @Nonnull ResponseConfiguration config,
            boolean success, @Nonnull ApplicationConfig appConfig, @Nonnull Long autoIncrement, @Nonnull RandomRequestId random,
            @Nonnull Consumer<BufferedHttpResponseDocumentGenerationDestination> responseConsumer
        ) {
            super(context, config, success, responseConsumer);
            this.environment = environment;
            this.applicationName = applicationName;
            this.application = application;
            this.appConfig = appConfig;
            this.autoIncrement = autoIncrement;
            this.autoInc = autoInc;
            this.random = random;
        }

        @SneakyThrows({RequestInvalidException.class, TransformationFailedException.class, NodeNotFoundException.class,
            EndpointExecutionFailedException.class})
        @Override public void runUnconditionally() {
            var stringParams = context.getStringParametersIncludingIntermediateValues(config.inputIntermediateValues);

            if (config instanceof ForwardToEndpointResponseConfiguration) {
                var request = new Request() {
                    @Override public @CheckForNull InetAddress getClientIpAddress() { return null; }
                    @Override public @Nonnull String getUserAgent() { return "<forward-to-endpoint>"; }
                    @Override public @CheckForNull String getContentTypeIfPost() { return null; }
                    @Override public @Nonnull List<? extends UploadedFile> getUploadedFiles() { return List.of(); }
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
                    context.tx, context.threads, false, new ParameterTransformationLogger(), autoInc, autoIncrement,
                    random, null, request, responseConsumer);
            }
            else super.runUnconditionally();
        }
    }

    protected @Nonnull RequestLogRecord newRequestLogRecord(
        @Nonnull ApplicationName applicationName, @Nonnull PublishEnvironment environment, @Nonnull NodeName endpointName,
        @Nonnull Instant now, @Nonnull Request req, @Nonnull ParameterTransformationLogger parameterTransformationLogger, 
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, 
        @Nonnull BufferedHttpResponseDocumentGenerationDestination response
    ) {
        var r = new RequestLogRecord();
        r.setApplication(applicationName);
        r.setEnvironment(environment);
        r.setEndpoint(endpointName);
        r.setDatetime(now);
        r.setStatusCode(response.getStatusCode());
        r.setUserAgent(req.getUserAgent());
        r.setOnDemandPerpetualIncrementingNumber(autoInc.get(perpetual).getValueOrNull());
        r.setOnDemandYearIncrementingNumber(autoInc.get(year).getValueOrNull());
        r.setOnDemandMonthIncrementingNumber(autoInc.get(month).getValueOrNull());
        r.setParameterTransformationInput(parameterTransformationLogger.input);
        r.setParameterTransformationOutput(parameterTransformationLogger.output);
        return r;
    }
    
    protected @Nonnull Response scheduleTasksAndSuccess(
        @Nonnull PublishEnvironment environment, @Nonnull ApplicationName applicationName, @Nonnull ApplicationConfig appConfig,
        @Nonnull TransformationContext context, @Nonnull Endpoint endpoint,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, long autoIncrement,
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
                context, autoInc, success, true, appConfig, autoIncrement, random, responseConsumer);
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
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, long autoIncrement,
        @Nonnull RandomRequestId random,
        @CheckForNull String hashToCheck, @Nonnull Request req,
        @Nonnull Consumer<BufferedHttpResponseDocumentGenerationDestination> responseConsumer
    ) throws EndpointExecutionFailedException, RequestInvalidException, TransformationFailedException {
        getParameters(applicationName, application, tx, threads, endpoint, req,
            appConfig.debugAllowed, debugRequested, parameterTransformationLogger,
            autoInc, autoIncrement, random, 
            parameters -> {
                try {
                    if (hashToCheck != null) assertHashCorrect(application, environment, endpoint, parameters, hashToCheck);
                    
                    try (var ignored3 = new Timer("Execute <task>s and generate response")) {
                        var context = new TransformationContext(application, tx, threads, parameters,
                            ParameterNotFoundPolicy.error, req.getUploadedFiles(), autoInc);
                        scheduleTasksAndSuccess(environment, applicationName, appConfig,
                            context, endpoint, autoInc, autoIncrement, random, responseConsumer);
                    }
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
            
            var parameterTransformationLogger = new ParameterTransformationLogger();
            
            try (var tx = new ApplicationTransaction(application);
                 var ignored2 = new Timer("<success> for application='"+applicationName.name+"', endpoint='"+endpoint.name.name+"'")) {
                
                var threads = new ThreadPool();
                threads.setThreadNamePrefix(getClass().getName() + " <success>");
                
                var appConfig = DeploymentParameters.get().getApplications(tx.db).fetchApplicationConfig(tx.db, applicationName);
                
                if (appConfig.locked) throw new RequestInvalidException("Application is locked");

                try (var ignored3 = new Timer("Acquire lock on '" + applicationName.name 
                        + "', environment '" + environment.name() + "'")) {
                    tx.db.jooq().select().from(APPLICATION_PUBLISH)
                        .where(APPLICATION_PUBLISH.APPLICATION_NAME.eq(applicationName))
                        .and(APPLICATION_PUBLISH.ENVIRONMENT.eq(environment))
                        .forUpdate().execute();
                }

                var autoIncrement = getNextAutoIncrement(tx.db, applicationName, environment, endpoint.name);
                var autoInc = newLazyNumbers(applicationName, environment, now);
                var random = RandomRequestId.generate(tx.db, applicationName, environment);

                var successResponse = new Consumer<BufferedHttpResponseDocumentGenerationDestination>() {
                    public BufferedHttpResponseDocumentGenerationDestination destination;
                    @Override public void accept(BufferedHttpResponseDocumentGenerationDestination d) { destination = d; } 
                };
                
                attemptSuccess(environment, applicationName, application, appConfig, 
                    endpoint, tx, threads, debugRequested, parameterTransformationLogger, autoInc, autoIncrement, random, 
                    hashToCheck, req, successResponse);

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

                var r = newRequestLogRecord(applicationName, environment, endpoint.name, now, req, parameterTransformationLogger,
                    autoInc, successResponse.destination);
                r.setIncrementalIdPerEndpoint(autoIncrement);
                r.setRandomIdPerApplication(random);
                tx.db.insert(r);

                tx.commit();
                responder.respond(successResponse.destination);
            }
            catch (Exception e) {
                try (var tx = new ApplicationTransaction(application);
                     var ignored2 = new Timer("<error> for application='"+applicationName.name+"', endpoint='"+endpoint.name.name+"'")) {
                    Logger.getLogger(getClass()).warn("Delivering error", e);

                    var errorResponse = new Consumer<BufferedHttpResponseDocumentGenerationDestination>() {
                        public BufferedHttpResponseDocumentGenerationDestination destination;
                        @Override public void accept(BufferedHttpResponseDocumentGenerationDestination d) { destination = d; }
                    };

                    var errorExpansionValues = new HashMap<ParameterName, String>();
                    errorExpansionValues.put(new ParameterName("internal-error-text"), e.getMessage());
                    errorExpansionValues.put(new ParameterName("parameter-transformation-error-text"),
                        e instanceof ParameterTransformationHadErrorException
                            ? ((ParameterTransformationHadErrorException) e).error : "");

                    var threads = new ThreadPool();
                    threads.setThreadNamePrefix(getClass().getName() + " <error>");
                    var autoInc = newLazyNumbers(applicationName, environment, now);
                    var context = new TransformationContext(application, tx, threads, errorExpansionValues,
                        ParameterNotFoundPolicy.error, emptyList(), autoInc);
                    threads.addTask(new Response(context, endpoint.error, false, errorResponse));
                    
                    try { threads.execute(); }
                    catch (RuntimeException e2) {
                        unwrapException(e2, RequestInvalidException.class);
                        unwrapException(e2, TransformationFailedException.class);
                        throw e2;
                    }

                    var r = newRequestLogRecord(applicationName, environment, endpoint.name, now, req, parameterTransformationLogger, 
                        autoInc, errorResponse.destination);
                    r.setExceptionMessage(e.getMessage());
                    r.setHttpRequestFailedUrl(e instanceof HttpRequestFailedException
                        ? (((HttpRequestFailedException) e).url) : null);
                    r.setHttpRequestFailedStatusCode(e instanceof HttpRequestFailedException
                        ? (((HttpRequestFailedException) e).responseStatusCode) : null);
                    r.setXsltParameterErrorMessage(e instanceof ParameterTransformationHadErrorException
                        ? ((ParameterTransformationHadErrorException) e).error : null);
                    tx.db.insert(r);

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
