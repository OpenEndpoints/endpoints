package endpoints;

import com.databasesandlife.util.DomParser;
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
import endpoints.datasource.DataSourceCommandResult;
import endpoints.datasource.ParametersCommand;
import endpoints.datasource.TransformationFailedException;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import endpoints.task.Task;
import endpoints.task.Task.TaskExecutionFailedException;
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
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.*;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;
import static com.databasesandlife.util.ThreadPool.unwrapException;
import static com.databasesandlife.util.gwtsafe.ConfigurationException.prefixExceptionMessage;
import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.month;
import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.perpetual;
import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.year;
import static endpoints.OnDemandIncrementingNumber.newLazyNumbers;
import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static endpoints.generated.jooq.Tables.APPLICATION_PUBLISH;
import static endpoints.generated.jooq.Tables.REQUEST_LOG;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang.RandomStringUtils.random;
import static org.jooq.impl.DSL.max;

public class EndpointExecutor {

    public interface Request {
        @CheckForNull InetAddress getClientIpAddress();
        /** Can return empty string */
        @Nonnull String getUserAgent();
        /** Returns content type or "GET" for GET requests */ 
        @Nonnull String getContentType();
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

    @SneakyThrows({DocumentTemplateInvalidException.class, TransformerException.class})
    protected @Nonnull Map<ParameterName, String> transformXmlIntoParameters(
        @Nonnull ApplicationName applicationName, @Nonnull Application application, @Nonnull ApplicationTransaction tx,
        @Nonnull Endpoint endpoint, @Nonnull Request req, boolean debugAllowed, boolean debugRequested, 
        @Nonnull ParameterTransformationLogger parameterTransformationLogger,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, 
        @Nonnull ParameterTransformation parameterTransformation, long autoIncrement, @Nonnull RandomRequestId random,
        @Nonnull Map<ParameterName, String> requestParameters,
        @Nonnull Node... inputFromRequestContents
    ) throws RequestInvalidException, TransformationFailedException, ParameterTransformationHadErrorException, HttpRequestFailedException {
        try {
            // Create input document
            var inputParametersDocument = DomParser.newDocumentBuilder().newDocument();
            inputParametersDocument.appendChild(inputParametersDocument.createElement("parameter-transformation-input"));

            // Add <input-from-request>
            var inputFromRequestElement = inputParametersDocument.createElement("input-from-request");
            inputParametersDocument.getDocumentElement().appendChild(inputFromRequestElement);
            if (debugRequested) inputFromRequestElement.appendChild(inputParametersDocument.createElement("debug-requested"));
            for (var n : inputFromRequestContents) inputFromRequestElement.appendChild(inputParametersDocument.importNode(n, true));
            appendTextElement(inputFromRequestElement, "ip-address",
                req.getClientIpAddress() == null ? null : req.getClientIpAddress().getHostAddress());

            // Add <input-from-application>
            var inputFromApplicationElement = inputParametersDocument.createElement("input-from-application");
            inputParametersDocument.getDocumentElement().appendChild(inputFromApplicationElement);
            var databaseConfig = tx.db.jooq().selectFrom(APPLICATION_CONFIG)
                .where(APPLICATION_CONFIG.APPLICATION_NAME.eq(applicationName)).fetchOne();
            appendTextElement(inputFromApplicationElement, "application", applicationName.name);
            appendTextElement(inputFromApplicationElement, "application-display-name", databaseConfig.getDisplayName());
            if (debugAllowed) inputFromApplicationElement.appendChild(inputParametersDocument.createElement("debug-allowed"));
            appendTextElement(inputFromApplicationElement, "secret-key", application.getSecretKeys()[0]);
            appendTextElement(inputFromApplicationElement, "incremental-id-per-endpoint", Long.toString(autoIncrement));
            appendTextElement(inputFromApplicationElement, "random-id-per-application", Long.toString(random.getId()));
            appendTextElement(inputFromApplicationElement, "http-header-user-agent", req.getUserAgent());

            // Add data sources e.g. <xml-from-application>
            var context = new TransformationContext(application, tx, requestParameters,
                ParameterNotFoundPolicy.emptyString, req.getUploadedFiles(), autoInc);
            var dataSourceResults = new ArrayList<DataSourceCommandResult>();
            for (var c : parameterTransformation.dataSourceCommands)
                dataSourceResults.add(c.scheduleExecution(context, emptySet()));
            context.threads.execute();
            for (var r : dataSourceResults)
                for (var element : r.get())
                    inputParametersDocument.getDocumentElement().appendChild(inputParametersDocument.importNode(element, true));

            // Transform
            boolean debug = debugAllowed && debugRequested;
            if (debug) parameterTransformationLogger.input = inputParametersDocument.getDocumentElement();
            var outputParametersDocument = new DOMResult();
            parameterTransformation.xslt.newTransformer()
                .transform(new DOMSource(inputParametersDocument), outputParametersDocument);

            // Parse and return output
            var outputParametersRoot = ((Document) outputParametersDocument.getNode()).getDocumentElement();
            if (debug) parameterTransformationLogger.output = outputParametersRoot;
            if (outputParametersRoot == null) throw new ConfigurationException("Parameter transformation delivered empty document");
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
                if ( ! endpointsParameters.contains(paramName)) throw new RequestInvalidException("Parameter transformation XSLT " +
                    "produced <parameter name='"+paramName.name+"'../> but this parameter isn't declared in the endpoints.xml");
                result.put(paramName, getMandatoryAttribute(parameterElement, "value"));
            }
            return result;
        }
        catch (ConfigurationException e) { throw new RequestInvalidException("While processing result of parameter transformation", e); }
        catch (RuntimeException e) {
            unwrapException(e, RequestInvalidException.class);
            unwrapException(e, TransformationFailedException.class);
            unwrapException(e, HttpRequestFailedException.class);
            throw e;
        }
    }

    protected @Nonnull Map<ParameterName, String> getParameters(
        @Nonnull ApplicationName applicationName, @Nonnull Application application, @Nonnull ApplicationTransaction tx,
        @Nonnull Endpoint endpoint, @Nonnull Request req, boolean debugAllowed, boolean debugRequested,
        @Nonnull ParameterTransformationLogger parameterTransformationLogger, 
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc,
        long autoIncrement, @Nonnull RandomRequestId random
    ) throws RequestInvalidException, TransformationFailedException, ParameterTransformationHadErrorException,
             EndpointExecutionFailedException, HttpRequestFailedException {
        final Map<ParameterName, String> transformedParameters;

        switch (req.getContentType()) {
            case "GET":
            case "application/x-www-form-urlencoded":
            case "multipart/form-data":
                var inputParameters = req.getParameters().entrySet().stream().collect(toMap(
                    e -> e.getKey(),
                    e -> e.getValue().stream().collect(joining(endpoint.getParameterMultipleValueSeparator()))
                ));
                if (endpoint.parameterTransformation == null) {
                    transformedParameters = inputParameters;
                } else {
                    transformedParameters = transformXmlIntoParameters(applicationName, application, tx, endpoint, req,
                        debugAllowed, debugRequested, parameterTransformationLogger, autoInc,
                        endpoint.parameterTransformation, autoIncrement, random, inputParameters,
                        ParametersCommand.createParametersElements(inputParameters, emptyMap(), req.getUploadedFiles()));
                }
                break;

            case "application/xml":
                try {
                    if (endpoint.parameterTransformation == null) throw new RequestInvalidException("Endpoint does not have " +
                        "<parameter-transformation> defined, therefore cannot accept XML request");
                    var requestDocument = DomParser.from(req.getInputStream());
                    transformedParameters = transformXmlIntoParameters(applicationName, application, tx, endpoint, req,
                        debugAllowed, debugRequested, parameterTransformationLogger, autoInc,
                        endpoint.parameterTransformation, autoIncrement, random, emptyMap(), requestDocument);
                }
                catch (ConfigurationException e) { throw new RequestInvalidException("Request is not valid XML", e); }
                break;

            default:
                throw new RequestInvalidException("Unexpected content type '" + req.getContentType()
                    + "': expected 'application/x-www-form-urlencoded', 'multipart/form-data' or 'application/xml'");
        }

        // Apply <parameter> definition from endpoints.xml
        var checkedParameters = new HashMap<ParameterName, String>();
        for (var paramEntry : endpoint.aggregateParametersOverParents().entrySet()) {
            var param = paramEntry.getKey();
            var defn = paramEntry.getValue();

            if (transformedParameters.containsKey(param)) checkedParameters.put(param, transformedParameters.get(param));
            else if (defn.defaultValueOrNull != null) checkedParameters.put(param, defn.defaultValueOrNull);
            else throw new RequestInvalidException("Parameter '"+param.name+"' did not have a supplied value, nor a default");
        }

        return checkedParameters;
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
    
    protected abstract static class HttpDestination 
    extends BufferedHttpResponseDocumentGenerationDestination 
    implements Runnable { }

    /**
     * Either execute the response (in case of redirect or OK), or write the response to a buffer (in the case of content).
     * <p>The content is written to the buffer because there is much that can go wrong half way through writing the response;
     * in this case we want to write an error to the browser, which we cannot do, if the first half of the successful
     * response has already been streamed to the client. 
     * @return response which must still be delivered to the user, or null if it has already been delivered.
     */
    protected @Nonnull HttpDestination scheduleResponseGeneration(
        @Nonnull List<Runnable> dependencies,
        @Nonnull TransformationContext context,
        @Nonnull ResponseConfiguration config, boolean success
    ) {
        var result = new HttpDestination() {
            @SneakyThrows({IOException.class, RequestInvalidException.class, TransformationFailedException.class})
            @Override public void run() {
                var stringParams = context.getStringParametersIncludingIntermediateValues(config.inputIntermediateValues);

                int statusCode = success ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST;

                if (config instanceof EmptyResponseConfiguration) {
                    setStatusCode(statusCode);
                }
                else if (config instanceof RedirectResponseConfiguration) {
                    var url = replacePlainTextParameters(((RedirectResponseConfiguration)config).urlPattern, stringParams);
                    if (!((RedirectResponseConfiguration)config).whitelist.isUrlInWhiteList(url))
                        throw new RequestInvalidException("Redirect URL '"+url+"' is not in whitelist");
                    setRedirectUrl(new URL(url));
                }
                else if (config instanceof TransformationResponseConfiguration) {
                    var r = (TransformationResponseConfiguration) config;
                    setStatusCode(statusCode);
                    r.transformer.scheduleExecution(context, config.inputIntermediateValues, this);
                    if (r.downloadFilenamePatternOrNull != null)
                        setContentDispositionToDownload(replacePlainTextParameters(r.downloadFilenamePatternOrNull, stringParams));
                }
                else throw new IllegalStateException("Unexpected config: " + config);
            }
        };
        context.threads.addTaskWithDependencies(dependencies, result);
        return result;
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
        r.setDatetimeUtc(now);
        r.setStatusCode(response.getStatusCode());
        r.setUserAgent(req.getUserAgent());
        r.setOnDemandPerpetualIncrementingNumber(autoInc.get(perpetual).getValueOrNull());
        r.setOnDemandYearIncrementingNumber(autoInc.get(year).getValueOrNull());
        r.setOnDemandMonthIncrementingNumber(autoInc.get(month).getValueOrNull());
        r.setParameterTransformationInput(parameterTransformationLogger.input);
        r.setParameterTransformationOutput(parameterTransformationLogger.output);
        return r;
    }
    
    protected @Nonnull HttpDestination scheduleTasksAndSuccess(
        @Nonnull TransformationContext context, @Nonnull Endpoint endpoint
    ) {
        var synchronizationPointForOutputValue = new HashMap<IntermediateValueName, SynchronizationPoint>();
        @SuppressWarnings("Convert2Diamond")  // IntelliJ Windows requires the <Task> here
        var tasksToExecute = new ArrayList<Task>(endpoint.tasks);
        
        int infiniteLoopProtection = 0;
        while ( ! tasksToExecute.isEmpty())
            tasks: for (var taskIter = tasksToExecute.iterator(); taskIter.hasNext(); ) {
                var task = taskIter.next();
                var taskDependencies = new ArrayList<SynchronizationPoint>();
                for (var neededInputValue : task.inputIntermediateValues) {
                    if ( ! synchronizationPointForOutputValue.containsKey(neededInputValue)) continue tasks;
                    taskDependencies.add(synchronizationPointForOutputValue.get(neededInputValue));
                }
                
                var taskRunnable = task.scheduleTaskExecutionIfNecessary(taskDependencies, context);
                taskIter.remove();
                
                for (var outputValue : task.getOutputIntermediateValues()) 
                    synchronizationPointForOutputValue.put(outputValue, taskRunnable);
            }

            infiniteLoopProtection++;
            if (infiniteLoopProtection >= 1_000) {
                throw new RuntimeException("Unreachable: Probably circular dependency of task intermediate variables");
        }

        var successDependencies = new ArrayList<Runnable>();
        for (var neededInputValue : endpoint.success.inputIntermediateValues) {
            successDependencies.add(synchronizationPointForOutputValue.get(neededInputValue));
        }

        return scheduleResponseGeneration(successDependencies, context, endpoint.success, true);
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
                
                var appConfig = DeploymentParameters.get().getApplications(tx.db).fetchApplicationConfig(tx.db, applicationName);
                
                if (appConfig.locked) throw new RequestInvalidException("Application is locked");

                try (var ignored3 = new Timer("Acquire lock on '" + applicationName.name 
                        + "', environment '" + environment.name() + "'")) {
                    tx.db.jooq().select().from(APPLICATION_PUBLISH)
                        .where(APPLICATION_PUBLISH.APPLICATION_NAME.eq(applicationName))
                        .and(APPLICATION_PUBLISH.ENVIRONMENT.eq(environment))
                        .forUpdate().execute();
                }

                long autoIncrement = getNextAutoIncrement(tx.db, applicationName, environment, endpoint.name);
                var autoInc = newLazyNumbers(applicationName, environment, now);
                var random = RandomRequestId.generate(tx.db, applicationName, environment);

                var parameters = getParameters(applicationName, application, tx, endpoint, req, 
                    appConfig.debugAllowed, debugRequested, parameterTransformationLogger, 
                    autoInc, autoIncrement, random);

                if (hashToCheck != null) assertHashCorrect(application, environment, endpoint, parameters, hashToCheck);

                final HttpDestination responseContent;
                try (var ignored3 = new Timer("Execute <task>s and generate response")) {
                    var context = new TransformationContext(application, tx, parameters,
                        ParameterNotFoundPolicy.error, req.getUploadedFiles(), autoInc);

                    responseContent = scheduleTasksAndSuccess(context, endpoint);

                    try { context.threads.execute(); }
                    catch (RuntimeException e) {
                        unwrapException(e, RequestInvalidException.class);
                        unwrapException(e, TransformationFailedException.class);
                        unwrapException(e, TaskExecutionFailedException.class);
                        unwrapException(e, HttpRequestFailedException.class);
                        throw e;
                    }
                }

                var r = newRequestLogRecord(applicationName, environment, endpoint.name, now, req, parameterTransformationLogger,
                    autoInc, responseContent);
                r.setIncrementalIdPerEndpoint(autoIncrement);
                r.setRandomIdPerApplication(random);
                tx.db.insert(r);

                tx.commit();
                responder.respond(responseContent);
            }
            catch (Exception e) {
                try (var tx = new ApplicationTransaction(application);
                     var ignored2 = new Timer("<error> for application='"+applicationName.name+"', endpoint='"+endpoint.name.name+"'")) {
                    Logger.getLogger(getClass()).warn("Delivering error", e);
                    var autoInc = newLazyNumbers(applicationName, environment, now);
                    var context = new TransformationContext(application, tx, new HashMap<>(),
                        ParameterNotFoundPolicy.error, emptyList(), autoInc);
                    var errorResponseContent = scheduleResponseGeneration(emptyList(), context, endpoint.error, false);
                    
                    try { context.threads.execute(); }
                    catch (RuntimeException e2) {
                        unwrapException(e2, RequestInvalidException.class);
                        unwrapException(e2, TransformationFailedException.class);
                        throw e2;
                    }

                    var r = newRequestLogRecord(applicationName, environment, endpoint.name, now, req, parameterTransformationLogger, 
                        autoInc, errorResponseContent);
                    r.setExceptionMessage(e.getMessage());
                    r.setHttpRequestFailedUrl(e instanceof HttpRequestFailedException
                        ? (((HttpRequestFailedException) e).url) : null);
                    r.setHttpRequestFailedStatusCode(e instanceof HttpRequestFailedException
                        ? (((HttpRequestFailedException) e).responseStatusCode) : null);
                    r.setXsltParameterErrorMessage(e instanceof ParameterTransformationHadErrorException
                        ? ((ParameterTransformationHadErrorException) e).error : null);
                    tx.db.insert(r);

                    tx.commit();
                    responder.respond(errorResponseContent);
                }
                catch (RequestInvalidException | TransformationFailedException ee) {
                    throw new EndpointExecutionFailedException(500, "Error occurred; but <error> has an error: "+ee, ee);
                }
            }
        }
        catch (Exception e) { throw new EndpointExecutionFailedException(500, "An internal error occurred", e); }
    }
}
