package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.wicket.CachingFutureModel;
import com.databasesandlife.util.wicket.LambdaDisplayValueChoiceRenderer;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.RequestId;
import endpoints.config.ApplicationName;
import endpoints.config.NodeName;
import endpoints.generated.jooq.tables.records.RequestLogExpressionCaptureRecord;
import endpoints.generated.jooq.tables.records.RequestLogIdsRecord;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import endpoints.serviceportal.DateRangeOption;
import endpoints.serviceportal.wicket.model.EndpointNamesModel;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import endpoints.serviceportal.wicket.panel.SubstringHighlightLabel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.form.TextField;
import org.apache.wicket.markup.html.link.ResourceLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.LambdaModel;
import org.apache.wicket.request.resource.ByteArrayResource;
import org.jooq.Condition;
import org.jooq.Field;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static endpoints.PublishEnvironment.live;
import static endpoints.generated.jooq.Tables.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.LocalDate.now;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.*;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static org.apache.wicket.ajax.AbstractAjaxTimerBehavior.onTimer;
import static org.apache.wicket.util.time.Duration.seconds;
import static org.jooq.impl.DSL.*;

public class RequestLogPage extends AbstractLoggedInApplicationPage {

    protected final @Nonnull ApplicationName applicationName = getSession().getLoggedInApplicationDataOrThrow().application();
    protected @Getter @Setter @Nonnull PublishEnvironment filterEnvironment = live;
    protected @Getter @Setter @Nonnull DateRangeOption dateRange = DateRangeOption.getValues(now(UTC)).get(0);
    protected @Getter @Setter @CheckForNull NodeName filterEndpoint = null;
    protected @Getter @Setter @CheckForNull ErrorType filterErrorType = null;
    protected @Getter @Setter @CheckForNull String filterText = null;
    protected final @Nonnull Set<RequestId> expandedRows = new HashSet<>();
    
    protected enum ErrorType {
        success { 
            public @Nonnull Condition getRequestLogCondition() {
                var otherTypes = Arrays.stream(ErrorType.values()).filter(x -> x != this && x != other);
                return not(or(otherTypes.map(type -> type.getRequestLogCondition()).collect(toList())))
                    .and(REQUEST_LOG.STATUS_CODE.between(200, 399)); 
            }
        },
        parameterTransformationError {
            public @Nonnull Condition getRequestLogCondition() { 
                return REQUEST_LOG.XSLT_PARAMETER_ERROR_MESSAGE.isNotNull(); 
            }
        },
        httpRequestFailed {
            public @Nonnull Condition getRequestLogCondition() { 
                return REQUEST_LOG.HTTP_REQUEST_FAILED_STATUS_CODE.isNotNull(); 
            }
        },
        internalError {
            public @Nonnull Condition getRequestLogCondition() {
                return REQUEST_LOG.STATUS_CODE.eq(SC_INTERNAL_SERVER_ERROR);
            }
        },
        /** For example hash wrong */
        other {
            public @Nonnull Condition getRequestLogCondition() {
                var otherTypes = Arrays.stream(ErrorType.values()).filter(x -> x != this);
                return not(or(otherTypes.map(type -> type.getRequestLogCondition()).collect(toList())));
            }
        };
        
        /** @return condition assuming REQUEST_LOG and REQUEST_LOG_IDS are available */
        public abstract @Nonnull Condition getRequestLogCondition();
    }
    
    protected static @Nonnull Condition getTextFilter(@Nonnull String filter) {
        return falseCondition()
            .or(REQUEST_LOG_IDS.ENDPOINT.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG.STATUS_CODE.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG_IDS.INCREMENTAL_ID_PER_ENDPOINT.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG_IDS.RANDOM_ID_PER_APPLICATION.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG.REQUEST_ID.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG.USER_AGENT.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG.EXCEPTION_MESSAGE.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG.HTTP_REQUEST_FAILED_URL.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG.HTTP_REQUEST_FAILED_STATUS_CODE.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG.XSLT_PARAMETER_ERROR_MESSAGE.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT.cast(String.class).containsIgnoreCase(filter))
            .or(REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT.cast(String.class).containsIgnoreCase(filter))
            .or(exists(select()
                .from(REQUEST_LOG_EXPRESSION_CAPTURE)
                .where(REQUEST_LOG_EXPRESSION_CAPTURE.REQUEST_ID.eq(REQUEST_LOG_IDS.REQUEST_ID))
                .and(REQUEST_LOG_EXPRESSION_CAPTURE.VALUE.containsIgnoreCase(filter))
            ));
    }
    
    protected @Nonnull Condition getCondition() {
        return REQUEST_LOG_IDS.APPLICATION.eq(applicationName)
            .and(REQUEST_LOG_IDS.ENVIRONMENT.eq(filterEnvironment))
            .and(REQUEST_LOG.DATETIME.ge(dateRange.getStartDateUtc(now(UTC)).atStartOfDay(UTC).toInstant()))
            .and(REQUEST_LOG.DATETIME.lt(Optional.ofNullable(dateRange.getEndDateUtc(now(UTC))).orElse(now(UTC))
                .plus(1, DAYS).atStartOfDay(UTC).toInstant()))
            .and(filterEndpoint == null ? trueCondition() : REQUEST_LOG_IDS.ENDPOINT.eq(filterEndpoint))
            .and(filterErrorType == null ? trueCondition() : filterErrorType.getRequestLogCondition()) 
            .and(filterText == null ? trueCondition() : getTextFilter(filterText));
    }
    
    @AllArgsConstructor
    protected static class ParameterTransformationXml implements Serializable {
        public boolean xmlIsAvailable, matchesTextFilter;
    }
    
    @AllArgsConstructor
    protected static class RequestLogEntry implements Serializable {
        public @Nonnull RequestLogIdsRecord ids;
        /** Doesn't have the XML and request body fields populated (they might be huge, don't store them in the session) */
        public @Nonnull RequestLogRecord record;
        public @Nonnull ParameterTransformationXml parameterTransformationInput, parameterTransformationOutput;
        public @Nonnull List<RequestLogExpressionCaptureRecord> expressionCaptures;
    }

    protected class ResultsModel extends CachingFutureModel<ArrayList<RequestLogEntry>> {
        @Override protected @Nonnull ArrayList<RequestLogEntry> populate() {
            try (
                var tx = DeploymentParameters.get().newDbTransaction();
                var ignored = new Timer("Fetch RequestLog")
            ) {
                var fields = new ArrayList<Field<?>>();
                fields.add(field(REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT.isNotNull()));
                fields.add(coalesce(field(REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT
                    .cast(String.class).containsIgnoreCase(filterText)), false));
                fields.add(field(REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT.isNotNull()));
                fields.add(coalesce(field(REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT
                    .cast(String.class).containsIgnoreCase(filterText)), false));
                fields.addAll(asList(REQUEST_LOG.fields()));
                fields.addAll(asList(REQUEST_LOG_IDS.fields()));
                
                // Don't fetch fields which are large. Only download them as needed when the user clicks the download button.
                fields.remove(REQUEST_LOG.REQUEST_BODY);
                fields.remove(REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT);
                fields.remove(REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT);

                var requestLogRecords = tx.jooq()
                    .select(fields)
                    .from(REQUEST_LOG_IDS)
                    .join(REQUEST_LOG).on(REQUEST_LOG.REQUEST_ID.eq(REQUEST_LOG_IDS.REQUEST_ID))
                    .where(getCondition())
                    .orderBy(REQUEST_LOG.DATETIME.desc())
                    .limit(500)
                    .fetch();

                var captures = tx.jooq()
                    .selectFrom(REQUEST_LOG_EXPRESSION_CAPTURE)
                    .where(REQUEST_LOG_EXPRESSION_CAPTURE.REQUEST_ID.in(requestLogRecords.getValues(REQUEST_LOG_IDS.REQUEST_ID)))
                    .orderBy(lower(REQUEST_LOG_EXPRESSION_CAPTURE.KEY))
                    .fetch().stream()
                    .collect(groupingBy(r -> r.getRequestId(), toList()));

                return requestLogRecords.stream()
                    .map(r -> new RequestLogEntry(r.into(REQUEST_LOG_IDS), r.into(REQUEST_LOG),
                        new ParameterTransformationXml(r.get(0, Boolean.class), r.get(1, Boolean.class)),
                        new ParameterTransformationXml(r.get(2, Boolean.class), r.get(3, Boolean.class)),
                        captures.getOrDefault(r.into(REQUEST_LOG_IDS).getRequestId(), List.of())))
                    .collect(toCollection(ArrayList::new));
            }
        }
    }

    protected class ResultsCountModel extends CachingFutureModel<Integer> {
        @Override protected @Nonnull Integer populate() {
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                return tx.jooq().selectCount()
                    .from(REQUEST_LOG_IDS)
                    .join(REQUEST_LOG).on(REQUEST_LOG.REQUEST_ID.eq(REQUEST_LOG_IDS.REQUEST_ID))
                    .where(getCondition()).fetchSingle().value1();
            }
        }
    }
    
    protected class DownloadResource<T> extends ByteArrayResource {
        protected final @Nonnull RequestId id;
        protected final @Nonnull Field<T> requestLogField;

        public DownloadResource(
            @Nonnull RequestId id, @Nonnull Field<T> requestLogField,
            @Nonnull String contentType, @Nonnull String filename
        ) {
            super(contentType, null, filename);
            this.id = id;
            this.requestLogField = requestLogField;
        }

        // By default, these are cached, which is bad as they have e.g. link8 and if a new request turns up
        // then all the rows are pushed down and link8 might now refer to something else
        @Override protected void configureResponse(ResourceResponse response, Attributes attributes) {
            super.configureResponse(response, attributes);
            response.disableCaching();
        }

        protected T fetch() {
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                LoggerFactory.getLogger(getClass()).info("Downloading " + requestLogField + " for request_log_id " + id.id() + "...");
                return tx.jooq().select(requestLogField)
                    .from(REQUEST_LOG_IDS)
                    .join(REQUEST_LOG).on(REQUEST_LOG.REQUEST_ID.eq(REQUEST_LOG_IDS.REQUEST_ID))
                    .where(REQUEST_LOG_IDS.APPLICATION.eq(applicationName))
                    .and(REQUEST_LOG_IDS.REQUEST_ID.eq(id)).fetchSingle().value1();
            }
        }
    }

    protected class BinaryDownloadResource extends DownloadResource<byte[]> {
        public BinaryDownloadResource(
            @Nonnull RequestId id, @Nonnull Field<byte[]> requestLogField, @Nonnull String contentType, @Nonnull String filename
        ) {
            super(id, requestLogField, contentType, filename);
        }

        @Override protected @Nonnull byte[] getData(Attributes x) { return fetch(); }
    }

    protected class XmlDownloadResource extends DownloadResource<Element> {
        public XmlDownloadResource(@Nonnull RequestId id, @Nonnull Field<Element> requestLogField, @Nonnull String filename) {
            super(id, requestLogField, "application/xml; charset=utf-8", filename);
        }

        @Override protected @Nonnull byte[] getData(Attributes x) {
            var element = fetch();
            return DomParser.formatXmlPretty(element).getBytes(UTF_8);
        }
    }
    
    public RequestLogPage() {
        super(NavigationItem.RequestLogPage, null);

        var endpointsNamesModel = new EndpointNamesModel(this::getFilterEnvironment);
        var resultsModel = new ResultsModel();
        var resultsCountModel = new ResultsCountModel();

        var form = new Form<Void>("filter") {
            @Override protected void onSubmit() {
                endpointsNamesModel.refresh(); // e.g. environment changed, different set of endpoints available
                resultsCountModel.refresh();   // e.g. filter changed
                resultsModel.refresh();
            }
        };
        add(form);

        form.add(new DropDownChoice<>("environment", LambdaModel.of(this::getFilterEnvironment, this::setFilterEnvironment),
            asList(PublishEnvironment.values()), new EnumChoiceRenderer<>(this)));
        form.add(new DropDownChoice<>("dateRange", LambdaModel.of(this::getDateRange, this::setDateRange),
            () -> DateRangeOption.getValues(now(UTC)), new LambdaDisplayValueChoiceRenderer<>(r -> r.getDisplayName())));
        form.add(new DropDownChoice<>("endpoint", LambdaModel.of(this::getFilterEndpoint, this::setFilterEndpoint),
            endpointsNamesModel, new LambdaDisplayValueChoiceRenderer<>(e -> e.name)).setNullValid(true));
        form.add(new DropDownChoice<>("errorType", LambdaModel.of(this::getFilterErrorType, this::setFilterErrorType),
            asList(ErrorType.values()), new EnumChoiceRenderer<>(this)).setNullValid(true));
        form.add(new TextField<>("text", LambdaModel.of(this::getFilterText, this::setFilterText)));
        
        var resultsTable = new WebMarkupContainer("results");
        resultsTable.add(new ListView<>("row", resultsModel) {
            @Override protected void populateItem(@Nonnull ListItem<RequestLogEntry> item) {
                var entry = item.getModelObject();
                var rec = entry.record;
                var id = rec.getRequestId();

                // We have to have this as UTC, because:
                // The top KPI numbers have to be UTC because they are used for billing, all users must see the same data
                // You want to select the KPI time range in the drop-down for verification, so drop-down must also act on UTC
                // If you select "today" in the UTC drop-down, you don't want to see yesterday in the table, so table must also be UTC
                var tableRow = new WebMarkupContainer("tableRow");
                tableRow.setOutputMarkupId(true);
                tableRow.add(AttributeAppender.append("class", () -> expandedRows.contains(id) ? "open-row" : ""));
                tableRow.add(new Label("dateTimeShortUtc", DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                    .format(rec.getDatetime().atOffset(UTC))));
                tableRow.add(new SubstringHighlightLabel("endpoint", filterText, entry.ids.getEndpoint().name));
                tableRow.add(new SubstringHighlightLabel("statusCode", filterText, Integer.toString(rec.getStatusCode()))
                    .add(AttributeAppender.append("class", rec.getStatusCode() >= 300 ? "status-error" : "")));
                tableRow.add(new SubstringHighlightLabel("incrementalIdPerEndpoint", filterText,
                    Optional.ofNullable(entry.ids.getIncrementalIdPerEndpoint()).map(x -> Long.toString(x)).orElse(null)));
                tableRow.add(new SubstringHighlightLabel("randomIdPerApplication", filterText, 
                    Optional.ofNullable(entry.ids.getRandomIdPerApplication()).map(x -> Long.toString(x.id())).orElse(null)));
                item.add(tableRow);
                
                var detailsHighlightLabels = new ArrayList<SubstringHighlightLabel>();
                detailsHighlightLabels.add(
                    new SubstringHighlightLabel("requestId", filterText, rec.getRequestId().id().toString()));
                detailsHighlightLabels.add(
                    new SubstringHighlightLabel("userAgent", filterText, rec.getUserAgent()));
                detailsHighlightLabels.add((SubstringHighlightLabel) 
                    new SubstringHighlightLabel("exceptionMessage", filterText, rec.getExceptionMessage())
                    .setVisible(rec.getExceptionMessage() != null));
                detailsHighlightLabels.add((SubstringHighlightLabel) 
                    new SubstringHighlightLabel("httpRequestFailedUrl", filterText, rec.getHttpRequestFailedUrl())
                    .setVisible(rec.getHttpRequestFailedUrl() != null));
                detailsHighlightLabels.add((SubstringHighlightLabel) 
                    new SubstringHighlightLabel("httpRequestFailedStatusCode", filterText,
                        Optional.ofNullable(rec.getHttpRequestFailedStatusCode()).map(x -> Integer.toString(x)).orElse(null))
                    .setVisible(rec.getHttpRequestFailedStatusCode() != null));
                detailsHighlightLabels.add((SubstringHighlightLabel) 
                    new SubstringHighlightLabel("xsltParameterErrorMessage", filterText, rec.getXsltParameterErrorMessage())
                    .setVisible(rec.getXsltParameterErrorMessage() != null));
                
                var captures = new WebMarkupContainer("expressionCaptures");
                captures.add(new ListView<>("row", entry.expressionCaptures) {
                    @Override protected void populateItem(ListItem<RequestLogExpressionCaptureRecord> item) {
                        item.add(new Label("key", item.getModelObject().getKey()));
                        item.add(new SubstringHighlightLabel("value", filterText, item.getModelObject().getValue()));
                    }
                });
                captures.setVisible( ! entry.expressionCaptures.isEmpty());

                var details = new WebMarkupContainer("details");
                details.add(AttributeAppender.append("style", () -> expandedRows.contains(id) ? "" : "display:none;"));
                details.setOutputMarkupId(true);
                details.add(new Label("dateTimeUtc", DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm:ss.SSS, 'UTC'")
                    .format(rec.getDatetime().atZone(UTC))));
                details.add(new ResourceLink<>("downloadRequestBody",
                    new BinaryDownloadResource(id, REQUEST_LOG.REQUEST_BODY, rec.getRequestContentType(),
                        "request-body-"+id.id()))
                    .setVisible(rec.getRequestContentType() != null));
                details.add(new Label("requestContentType", rec.getRequestContentType())
                    .setVisible(rec.getRequestContentType() != null));
                details.add(new WebMarkupContainer("noRequestContentType").setVisible(rec.getRequestContentType() == null));
                details.add(new ResourceLink<>("downloadInputXml",
                    new XmlDownloadResource(id, REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT, "input-"+id.id()+".xml"))
                    .add(AttributeAppender.append("class",
                        entry.parameterTransformationInput.matchesTextFilter ? "filter-highlight" : ""))
                    .setVisible(entry.parameterTransformationInput.xmlIsAvailable));
                details.add(new ResourceLink<>("downloadOutputXml",
                    new XmlDownloadResource(id, REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT, "output-"+id.id()+".xml"))
                    .add(AttributeAppender.append("class", 
                        entry.parameterTransformationOutput.matchesTextFilter ? "filter-highlight" : ""))
                    .setVisible(entry.parameterTransformationOutput.xmlIsAvailable));
                details.add(new WebMarkupContainer("outputXmlNotAvailable")
                    .setVisible( ! entry.parameterTransformationOutput.xmlIsAvailable));
                for (var label : detailsHighlightLabels) details.add(label);
                details.add(captures);
                item.add(details);

                var expansionFilterTextMatches = detailsHighlightLabels.stream().anyMatch(label -> label.matches())
                    || entry.parameterTransformationInput.matchesTextFilter
                    || entry.parameterTransformationOutput.matchesTextFilter;
                
                // We implement the [+] opening of rows in Wicket, not Javascript
                // It would be possible to do this in Javascript on the client, that would be faster for the user.
                // However, it would be more difficult to remember which items were open across AJAX reloads
                // And would also have lower maintainability, due to more technologies, and generally more complex solution
                var toggle = new AjaxFallbackLink<Void>("toggle") {
                    @Override public void onClick(Optional<AjaxRequestTarget> redrawTarget) {
                        if (expandedRows.contains(id)) expandedRows.remove(id); else expandedRows.add(id);
                        redrawTarget.ifPresent(t -> t.add(tableRow, details));
                    }
                };
                toggle.add(new Label("text", () -> expandedRows.contains(id) ? "Hide" : "Show"));
                toggle.add(AttributeAppender.append("class", expansionFilterTextMatches ? "filter-highlight" : ""));
                tableRow.add(toggle);
            }
        });
        resultsTable.add(new Label("extraRowCount",  // Lombok stack overflow if LabelWithThousandSeparator, don't know why
            () -> String.format(getLocale(), "%,d", resultsCountModel.getObject() - resultsModel.getObject().size())) {
            @Override public boolean isVisible() {
                return resultsCountModel.getObject() - resultsModel.getObject().size() > 0;
            }
        });
        add(resultsTable.setOutputMarkupId(true));
        
        add(onTimer(seconds(5), (target) -> {
            resultsModel.refresh();
            resultsCountModel.refresh();
            target.add(resultsTable);
        }));
    }
}
