package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.generated.jooq.tables.records.RequestLogExpressionCaptureRecord;
import endpoints.generated.jooq.tables.records.RequestLogIdsRecord;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import static com.databasesandlife.util.DomParser.assertNoOtherElements;
import static endpoints.generated.jooq.Tables.*;
import static java.time.ZoneOffset.UTC;
import static java.util.stream.Collectors.groupingBy;
import static org.jooq.impl.DSL.lower;
import static org.jooq.impl.DSL.select;

public class RequestLogCommand extends DataSourceCommand {

    protected static final DateTimeFormatter dateTimeFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    public RequestLogCommand(
        @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element command
    ) throws ConfigurationException {
        super(threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, dataSourcePostProcessingXsltDir, command);
        assertNoOtherElements(command);
    }

    protected void appendElementWithText(
        @Nonnull Element container, @Nonnull Consumer<Element> after, @Nonnull String elementName, @CheckForNull Object contents
    ) {
        if (contents == null) return;

        var result = container.getOwnerDocument().createElement(elementName);
        result.setTextContent(contents.toString());
        after.accept(result);
        container.appendChild(result);
    }

    protected void appendElementWithXmlContents(@Nonnull Element container, @CheckForNull Element contents) {
        if (contents == null) return;
        container.appendChild(container.getOwnerDocument().importNode(contents, true));
    }

    protected @Nonnull Element createRequestLogEntryXml(
        @Nonnull Document doc, 
        @Nonnull RequestLogIdsRecord ids, @Nonnull RequestLogRecord r, 
        @Nonnull List<RequestLogExpressionCaptureRecord> expressionCaptures
    ) {
        var element = doc.createElement("request-log-entry");

        appendElementWithText(element, e->{}, "request-id", r.getRequestId().id);
        appendElementWithText(element, e->{}, "endpoint", ids.getEndpoint().name);
        appendElementWithText(element, e->{}, "datetime-utc", r.getDatetime().atZone(UTC).format(dateTimeFormat));
        appendElementWithText(element, e->{}, "status-code", r.getStatusCode());
        appendElementWithText(element, e->{}, "exception-message", r.getExceptionMessage());
        appendElementWithText(element, e->{}, "incremental-id-per-endpoint", ids.getIncrementalIdPerEndpoint());
        appendElementWithText(element, e->{}, "random-id-per-application",
            Optional.ofNullable(ids.getRandomIdPerApplication()).map(x -> x.getId()).orElse(null));
        appendElementWithText(element, e->{}, "user-agent", r.getUserAgent());
        appendElementWithText(element, e->{}, "environment", ids.getEnvironment());
        appendElementWithText(element, e->{}, "http-request-failed-url", r.getHttpRequestFailedUrl());
        appendElementWithText(element, e->{}, "http-request-failed-status-code", r.getHttpRequestFailedStatusCode());
        appendElementWithText(element, e->{}, "xslt-parameter-error-message", r.getXsltParameterErrorMessage());
        appendElementWithText(element, e->{}, "on-demand-perpetual-incrementing-number", ids.getOnDemandPerpetualIncrementingNumber());
        appendElementWithText(element, e->{}, "on-demand-perpetual-month-number", ids.getOnDemandMonthIncrementingNumber());
        appendElementWithText(element, e->{}, "on-demand-perpetual-year-number", ids.getOnDemandYearIncrementingNumber());
        appendElementWithXmlContents(element, r.getParameterTransformationInput());
        appendElementWithXmlContents(element, r.getParameterTransformationOutput());
        
        for (var c : expressionCaptures)
            appendElementWithText(element, e -> e.setAttribute("key", c.getKey()), "expression-capture", c.getValue());

        return element;
    }
    
    @Override public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context, @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws TransformationFailedException {
        var result = new DataSourceCommandFetcher() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                synchronized (context.tx.db) {
                    var doc = DomParser.newDocumentBuilder().newDocument();

                    var root = doc.createElement("request-log");

                    var requestLogRows = context.tx.db.jooq()
                        .select()
                        .from(REQUEST_LOG_IDS)
                        .join(REQUEST_LOG).on(REQUEST_LOG.REQUEST_ID.eq(REQUEST_LOG_IDS.REQUEST_ID))
                        .where(REQUEST_LOG_IDS.APPLICATION.eq(context.applicationName))
                        .orderBy(REQUEST_LOG.DATETIME.asc())
                        .fetch();

                    var expressionCaptures = context.tx.db.jooq()
                        .selectFrom(REQUEST_LOG_EXPRESSION_CAPTURE)
                        .where(REQUEST_LOG_EXPRESSION_CAPTURE.REQUEST_ID.in(
                            select(REQUEST_LOG_IDS.REQUEST_ID)
                                .from(REQUEST_LOG_IDS)
                                .where(REQUEST_LOG_IDS.APPLICATION.eq(context.applicationName))
                        ))
                        .orderBy(lower(REQUEST_LOG_EXPRESSION_CAPTURE.KEY))
                        .fetch().stream().collect(groupingBy(r -> r.getRequestId()));
                    
                    for (var r : requestLogRows) 
                        root.appendChild(createRequestLogEntryXml(doc, r.into(REQUEST_LOG_IDS), r.into(REQUEST_LOG), 
                            expressionCaptures.getOrDefault(r.get(REQUEST_LOG_IDS.REQUEST_ID), List.of())));

                    return new Element[] { root };
                }
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
