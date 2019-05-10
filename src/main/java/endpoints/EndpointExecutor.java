package endpoints;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.ThreadPool;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.BufferedHttpResponseDocumentGenerationDestination;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.config.*;
import endpoints.datasource.ParametersCommand;
import endpoints.datasource.TransformationFailedException;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
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
import java.net.URL;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static com.databasesandlife.util.DomParser.*;
import static com.databasesandlife.util.DomParser.getMandatoryAttribute;
import static com.databasesandlife.util.gwtsafe.ConfigurationException.prefixExceptionMessage;
import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.month;
import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.perpetual;
import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.year;
import static endpoints.OnDemandIncrementingNumber.newLazyNumbers;
import static endpoints.PlaintextParameterReplacer.replacePlainTextParameters;
import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static endpoints.generated.jooq.Tables.APPLICATION_PUBLISH;
import static endpoints.generated.jooq.Tables.REQUEST_LOG;
import static java.util.Arrays.stream;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static org.apache.commons.lang.RandomStringUtils.random;
import static org.apache.commons.lang.RandomStringUtils.randomNumeric;
import static org.jooq.impl.DSL.max;

public class EndpointExecutor {

    public interface UploadedFile {
        @Nonnull String getFieldName();
        @Nonnull String getContentType();

        /**
         * This is safe to call multiple times.
         * @implNote Both Wicket and Servlet are based on Jetty, which reads and stores the entire content when preparing the request.
         */
        @Nonnull InputStream getInputStream();
        
        /** null means that no filename was submitted. Will not be empty. */
        @CheckForNull String getSubmittedFileName();
    }

    public interface Request {
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

    protected long generateRandom(
        @Nonnull DbTransaction tx,
        @Nonnull ApplicationName applicationName, @Nonnull PublishEnvironment environment
    ) {
        for (int attempt = 0; attempt < 100; attempt++) {
            // 10 digits, starting with a non-zero so it is always 10 characters long
            long candidate = Long.parseLong(random(1, "123456789") + randomNumeric(9));

            int existingCount = tx.jooq()
                .selectCount()
                .from(REQUEST_LOG)
                .where(REQUEST_LOG.RANDOM_ID_PER_APPLICATION.eq(candidate))
                .and(REQUEST_LOG.APPLICATION.eq(applicationName))
                .and(REQUEST_LOG.ENVIRONMENT.eq(environment))
                .fetchOne().value1();
            if (existingCount == 0) return candidate;
        }

        throw new RuntimeException("Cannot find new random number");
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
        @Nonnull Endpoint endpoint, @Nonnull Request req,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, 
        @Nonnull ParameterTransformation parameterTransformation, long autoIncrement, long random,
        @Nonnull Node... inputFromRequestContents
    ) throws RequestInvalidException, TransformationFailedException, ParameterTransformationHadErrorException {
        try {
            // Create input document
            var inputParametersDocument = DomParser.newDocumentBuilder().newDocument();
            inputParametersDocument.appendChild(inputParametersDocument.createElement("parameter-transformation-input"));

            // Add <input-from-request>
            var inputFromRequestElement = inputParametersDocument.createElement("input-from-request");
            inputParametersDocument.getDocumentElement().appendChild(inputFromRequestElement);
            for (var n : inputFromRequestContents) inputFromRequestElement.appendChild(inputParametersDocument.importNode(n, true));

            // Add <input-from-application>
            var inputFromApplicationElement = inputParametersDocument.createElement("input-from-application");
            inputParametersDocument.getDocumentElement().appendChild(inputFromApplicationElement);
            var databaseConfig = tx.db.jooq().selectFrom(APPLICATION_CONFIG)
                .where(APPLICATION_CONFIG.APPLICATION_NAME.eq(applicationName)).fetchOne();
            appendTextElement(inputFromApplicationElement, "application", applicationName.name);
            appendTextElement(inputFromApplicationElement, "application-display-name", databaseConfig.getDisplayName());
            appendTextElement(inputFromApplicationElement, "included-requests-per-month", databaseConfig.getIncludedRequestsPerMonth());
            appendTextElement(inputFromApplicationElement, "secret-key", application.getSecretKeys()[0]);
            appendTextElement(inputFromApplicationElement, "incremental-id-per-endpoint", Long.toString(autoIncrement));
            appendTextElement(inputFromApplicationElement, "random-id-per-application", Long.toString(random));
            appendTextElement(inputFromApplicationElement, "http-header-user-agent", req.getUserAgent());

            // Add data sources e.g. <xml-from-application>
            var futures = parameterTransformation.dataSourceCommands.stream()
                .map(c -> c.execute(tx, emptyMap(), req.getUploadedFiles(), autoInc)).collect(toList());
            for (var r : futures)
                for (var element : r.getOrThrow())
                    inputParametersDocument.getDocumentElement().appendChild(inputParametersDocument.importNode(element, true));

            // Transform
            logXmlForDebugging(getClass(), "Input to parameter transformation", inputParametersDocument);
            var outputParametersDocument = new DOMResult();
            parameterTransformation.xslt.newTransformer()
                .transform(new DOMSource(inputParametersDocument), outputParametersDocument);
            logXmlForDebugging(getClass(), "Output from parameter transformation", (Document) outputParametersDocument.getNode());

            // Parse and return output
            var outputParametersRoot = ((Document) outputParametersDocument.getNode()).getDocumentElement();
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
    }

    protected @Nonnull Map<ParameterName, String> getParameters(
        @Nonnull ApplicationName applicationName, @Nonnull Application application, @Nonnull ApplicationTransaction tx,
        @Nonnull Endpoint endpoint, @Nonnull Request req,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc,
        long autoIncrement, long random
    ) throws RequestInvalidException, TransformationFailedException, ParameterTransformationHadErrorException,
             EndpointExecutionFailedException {
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
                    transformedParameters = transformXmlIntoParameters(applicationName, application, tx, endpoint, req, autoInc,
                        endpoint.parameterTransformation, autoIncrement, random,
                        ParametersCommand.createParametersElements(inputParameters));
                }
                break;

            case "application/xml":
                try {
                    if (endpoint.parameterTransformation == null) throw new RequestInvalidException("Endpoint does not have " +
                        "<parameter-transformation> defined, therefore cannot accept XML request");
                    var requestDocument = DomParser.from(req.getInputStream());
                    transformedParameters = transformXmlIntoParameters(applicationName, application, tx, endpoint, req, autoInc,
                        endpoint.parameterTransformation, autoIncrement, random, requestDocument);
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

    /**
     * Either execute the response (in case of redirect or OK), or write the response to a buffer (in the case of content).
     * <p>The content is written to the buffer because there is much that can go wrong half way through writing the response;
     * in this case we want to write an error to the browser, which we cannot do, if the first half of the successful
     * response has already been streamed to the client. 
     * @return response which must still be delivered to the user, or null if it has already been delivered.
     */
    @SneakyThrows({IOException.class, DocumentTemplateInvalidException.class})
    protected @Nonnull BufferedHttpResponseDocumentGenerationDestination generateResponse(
        @Nonnull ApplicationTransaction tx, @Nonnull ResponseConfiguration config,
        @Nonnull Map<ParameterName, String> params, @Nonnull List<? extends UploadedFile> fileUploads,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc, boolean success, boolean transform
    ) throws RequestInvalidException, TransformationFailedException {
        var dest = new BufferedHttpResponseDocumentGenerationDestination();

        int statusCode = success ? HttpServletResponse.SC_OK : HttpServletResponse.SC_BAD_REQUEST;

        if (config instanceof EmptyResponseConfiguration) {
            dest.setStatusCode(statusCode);
        }
        else if (config instanceof RedirectResponseConfiguration) {
            var url = replacePlainTextParameters(((RedirectResponseConfiguration)config).url, params);
            if (!((RedirectResponseConfiguration)config).whitelist.isUrlInWhiteList(url))
                throw new RequestInvalidException("Redirect URL '"+url+"' is not in whitelist");
            dest.setRedirectUrl(new URL(url));
        }
        else if (config instanceof TransformationResponseConfiguration) {
            var r = (TransformationResponseConfiguration) config;
            dest.setStatusCode(statusCode);
            r.transformer.execute(tx, dest, params, fileUploads, autoInc, transform);
            if (r.downloadFilenamePatternOrNull != null)
                dest.setContentDispositionToDownload(replacePlainTextParameters(r.downloadFilenamePatternOrNull, params));
        }
        else throw new IllegalStateException("Unexpected config: " + config);

        return dest;
    }

    protected @Nonnull RequestLogRecord newRequestLogRecord(
        @Nonnull ApplicationName applicationName, @Nonnull PublishEnvironment environment, @Nonnull NodeName endpointName,
        @Nonnull Instant now, @Nonnull Request req, 
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
        return r;
    }

    /**
     * @param hashToCheck null if not to check hash (e.g. service portal is calling this, so no hash check)
     */
    public void execute(
        @Nonnull PublishEnvironment environment, @Nonnull ApplicationName applicationName,
        @Nonnull Application application, @Nonnull Endpoint endpoint, boolean transform,
        @CheckForNull String hashToCheck, @Nonnull Request req, @Nonnull Responder responder
    ) throws EndpointExecutionFailedException {
        try (var ignored = new Timer(getClass().getSimpleName())) {
            var now = Instant.now();
            
            try (var tx = new ApplicationTransaction(application);
                 var ignored2 = new Timer("<success> for application='"+applicationName.name+"', endpoint='"+endpoint.name.name+"'")) {

                var locked = tx.db.jooq().select(APPLICATION_CONFIG.LOCKED).from(APPLICATION_CONFIG)
                    .where(APPLICATION_CONFIG.APPLICATION_NAME.eq(applicationName)).fetchOne(APPLICATION_CONFIG.LOCKED);
                if (locked) throw new RequestInvalidException("Application is locked");

                tx.db.jooq().select().from(APPLICATION_PUBLISH)
                    .where(APPLICATION_PUBLISH.APPLICATION_NAME.eq(applicationName))
                    .and(APPLICATION_PUBLISH.ENVIRONMENT.eq(environment))
                    .forUpdate().execute();

                long autoIncrement = getNextAutoIncrement(tx.db, applicationName, environment, endpoint.name);
                var autoInc = newLazyNumbers(applicationName, environment, now);
                long random = generateRandom(tx.db, applicationName, environment);

                var parameters = getParameters(applicationName, application, tx, endpoint, req, autoInc, autoIncrement, random);

                if (hashToCheck != null) assertHashCorrect(application, environment, endpoint, parameters, hashToCheck);

                final BufferedHttpResponseDocumentGenerationDestination responseContent;
                try (var ignored3 = new Timer("Execute <task>s and generate response")) {
                    var taskThreads = new ThreadPool();
                    taskThreads.setThreadNamePrefix("Execute <task>s and generate response");

                    class ResponseCreation implements Runnable {
                        BufferedHttpResponseDocumentGenerationDestination responseContent;

                        @Override public void run() {
                            try { 
                                responseContent = generateResponse(tx, endpoint.success, 
                                    parameters, req.getUploadedFiles(), autoInc, true, transform);
                            }
                            catch (RequestInvalidException | TransformationFailedException e) { throw new RuntimeException(e); }
                        }
                    }
                    var responseCreationTask = new ResponseCreation();
                    taskThreads.addTask(responseCreationTask);

                    for (var task : endpoint.tasks)
                        task.scheduleTaskExecution(tx, taskThreads, parameters, req.getUploadedFiles(), autoInc);

                    try { taskThreads.execute(); }
                    catch (RuntimeException e) {
                        if (e.getCause() instanceof RequestInvalidException)
                            throw (RequestInvalidException) e.getCause();
                        else if (e.getCause() instanceof TransformationFailedException)
                            throw (TransformationFailedException) e.getCause();
                        else if (e.getCause() instanceof TaskExecutionFailedException)
                            throw (TaskExecutionFailedException) e.getCause();
                        else
                            throw e;
                    }

                    responseContent = responseCreationTask.responseContent;
                }

                var r = newRequestLogRecord(applicationName, environment, endpoint.name, now, req, autoInc, responseContent);
                r.setIncrementalIdPerEndpoint(autoIncrement);
                r.setRandomIdPerApplication(random);
                tx.db.insert(r);

                tx.commit();
                responder.respond(responseContent);
            }
            catch (RequestInvalidException | ParameterTransformationHadErrorException
                    | TransformationFailedException | TaskExecutionFailedException e) {
                try (var tx = new ApplicationTransaction(application);
                     var ignored2 = new Timer("<error> for application='"+applicationName.name+"', endpoint='"+endpoint.name.name+"'")) {
                    Logger.getLogger(getClass()).warn("Delivering error", e);
                    var autoInc = newLazyNumbers(applicationName, environment, now);
                    var errorResponseContent = generateResponse(tx, endpoint.error, new HashMap<>(), emptyList(), autoInc,
                        false, transform);

                    var r = newRequestLogRecord(applicationName, environment, endpoint.name, now, req, autoInc, errorResponseContent);
                    r.setExceptionMessage(e.getMessage());
                    r.setHttpRequestFailedUrl(e.getCause() instanceof HttpRequestSpecification.HttpRequestFailedException
                        ? (((HttpRequestSpecification.HttpRequestFailedException) e.getCause()).url) : null);
                    r.setHttpRequestFailedStatusCode(e.getCause() instanceof HttpRequestSpecification.HttpRequestFailedException
                        ? (((HttpRequestSpecification.HttpRequestFailedException) e.getCause()).responseStatusCode) : null);
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
            catch (Exception e) {
                throw new EndpointExecutionFailedException(500, "An internal error occurred, " +
                    "application='"+applicationName.name+"', endpoint='"+endpoint.name.name+"'", "An internal error occurred", e);
            }
        }
        catch (Exception e) { throw new EndpointExecutionFailedException(500, "An internal error occurred", e); }
    }
}
