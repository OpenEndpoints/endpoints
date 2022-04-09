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
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.request.resource.CharSequenceResource;
import org.slf4j.LoggerFactory;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.ResourceLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.LambdaModel;
import org.jooq.Condition;
import org.jooq.Field;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static endpoints.PublishEnvironment.live;
import static endpoints.generated.jooq.Tables.REQUEST_LOG;
import static endpoints.generated.jooq.Tables.REQUEST_LOG_EXPRESSION_CAPTURE;
import static endpoints.generated.jooq.Tables.REQUEST_LOG_IDS;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.time.LocalDate.now;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.*;
import static org.apache.wicket.ajax.AbstractAjaxTimerBehavior.onTimer;
import static org.apache.wicket.util.time.Duration.seconds;
import static org.jooq.impl.DSL.*;
import static org.jooq.impl.DSL.coalesce;

public class RequestLogPage extends AbstractLoggedInApplicationPage {

    protected final @Nonnull ApplicationName applicationName = getSession().getLoggedInApplicationDataOrThrow().application;
    protected @Getter @Setter @Nonnull PublishEnvironment filterEnvironment = live;
    protected @Getter @Setter @Nonnull DateRangeOption dateRange = DateRangeOption.getValues(now(UTC)).get(0);
    protected @Getter @Setter @CheckForNull NodeName filterEndpoint = null;
    protected @Getter @Setter @CheckForNull Integer filterStatusCode = null;
    protected @Getter @Setter @CheckForNull String filterText = null;
    protected final @Nonnull Set<RequestId> expandedRows = new HashSet<>();
    
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
            .and(filterStatusCode == null ? trueCondition() : REQUEST_LOG.STATUS_CODE.eq(filterStatusCode))
            .and(filterText == null ? trueCondition() : getTextFilter(filterText));
    }
    
    @AllArgsConstructor
    protected static class ParameterTransformationXml implements Serializable {
        public boolean xmlIsAvailable, matchesTextFilter;
    }
    
    @AllArgsConstructor
    protected class RequestLogEntry implements Serializable {
        public @Nonnull RequestLogIdsRecord ids;
        /** Doesn't have the XML fields populated (they might be huge, don't store them in the session) */
        public @Nonnull RequestLogRecord record;
        public @Nonnull ParameterTransformationXml parameterTransformationInput, parameterTransformationOutput;
        public @Nonnull List<RequestLogExpressionCaptureRecord> expressionCaptures;
    }

    protected class ResultsModel extends CachingFutureModel<ArrayList<RequestLogEntry>> {
        @SuppressWarnings("SimplifyStreamApiCallChains")
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
    
    protected class XmlDownloadResource extends CharSequenceResource {
        protected final @Nonnull RequestId id;
        protected final @Nonnull Field<Element> requestLogField;

        public XmlDownloadResource(@Nonnull RequestId id, @Nonnull Field<Element> requestLogField, @Nonnull String filename) {
            super("application/xml; charset=utf-8", null, filename);
            this.id = id;
            this.requestLogField = requestLogField;
            setCharset(UTF_8);
        }

        // By default these are cached, which is bad as they have e.g. link8 and if a new request turns up
        // then all the rows are pushed down and link8 might now refer to something else
        @Override protected void configureResponse(ResourceResponse response, Attributes attributes) {
            super.configureResponse(response, attributes);
            response.disableCaching();
        }

        @Override protected @Nonnull String getData(Attributes x) {
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                LoggerFactory.getLogger(getClass()).info("Downloading " + requestLogField + " for request_log_id " + id.getId() + "...");
                var element = tx.jooq().select(requestLogField)
                    .from(REQUEST_LOG_IDS)
                    .join(REQUEST_LOG).on(REQUEST_LOG.REQUEST_ID.eq(REQUEST_LOG_IDS.REQUEST_ID))
                    .where(REQUEST_LOG_IDS.APPLICATION.eq(applicationName))
                    .and(REQUEST_LOG_IDS.REQUEST_ID.eq(id)).fetchSingle().value1();
                return DomParser.formatXmlPretty(element);
            }
        }
    }
    
    public RequestLogPage() {
        super(NavigationItem.RequestLogPage, null);

        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var endpointsNamesModel = new EndpointNamesModel(this::getFilterEnvironment);
            var statusCodes = tx.jooq().selectDistinct(REQUEST_LOG.STATUS_CODE)
                .from(REQUEST_LOG_IDS)
                .join(REQUEST_LOG).on(REQUEST_LOG.REQUEST_ID.eq(REQUEST_LOG_IDS.REQUEST_ID))
                .where(REQUEST_LOG_IDS.APPLICATION.eq(applicationName))
                .and(REQUEST_LOG.DATETIME.ge(DateRangeOption.getValues(now(UTC)).stream()
                    .map(d -> d.getStartDateUtc(now(UTC)))
                    .min(Comparator.naturalOrder())
                    .map(d -> d.atStartOfDay().toInstant(UTC)).orElse(null)))
                .orderBy(REQUEST_LOG.STATUS_CODE).fetch(REQUEST_LOG.STATUS_CODE);
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
            form.add(new DropDownChoice<>("statusCode", LambdaModel.of(this::getFilterStatusCode, this::setFilterStatusCode),
                statusCodes).setNullValid(true));
            form.add(new TextField<>("text", LambdaModel.of(this::getFilterText, this::setFilterText)));
            
            var resultsTable = new WebMarkupContainer("results");
            resultsTable.add(new ListView<>("row", resultsModel) {
                @Override protected void populateItem(@Nonnull ListItem<RequestLogEntry> item) {
                    var entry = item.getModelObject();
                    var rec = entry.record;
                    var id = rec.getRequestId();

                    // We have to have this as UTC, because:
                    // The top KPI numbers have to be UTC because they are used for billing, all users must see the same data
                    // You want to select the KPI time range in the drop-down for verificaiton, so drop-down must also act on UTC
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
                        Optional.ofNullable(entry.ids.getRandomIdPerApplication()).map(x -> Long.toString(x.getId())).orElse(null)));
                    item.add(tableRow);
                    
                    var detailsHighlightLabels = new ArrayList<SubstringHighlightLabel>();
                    detailsHighlightLabels.add(
                        new SubstringHighlightLabel("requestId", filterText, rec.getRequestId().getId().toString()));
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
                    details.add(new ResourceLink<Element>("downloadInputXml",
                        new XmlDownloadResource(id, REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT, "input-"+id.id+".xml"))
                        .add(AttributeAppender.append("class",
                            entry.parameterTransformationInput.matchesTextFilter ? "filter-highlight" : ""))
                        .setVisible(entry.parameterTransformationInput.xmlIsAvailable));
                    details.add(new ResourceLink<Element>("downloadOutputXml",
                        new XmlDownloadResource(id, REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT, "output-"+id.id+".xml"))
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
}
