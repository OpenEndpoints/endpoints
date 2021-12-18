package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.wicket.CachingFutureModel;
import com.databasesandlife.util.wicket.LambdaDisplayValueChoiceRenderer;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.RequestId;
import endpoints.config.ApplicationName;
import endpoints.config.NodeName;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import endpoints.serviceportal.DateRangeOption;
import endpoints.serviceportal.wicket.model.EndpointNamesModel;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.LoggerFactory;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.link.ResourceLink;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.LambdaModel;
import org.apache.wicket.request.Response;
import org.apache.wicket.request.resource.BaseDataResource;
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
import static java.time.LocalDate.now;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toCollection;
import static org.apache.wicket.ajax.AbstractAjaxTimerBehavior.onTimer;
import static org.apache.wicket.util.time.Duration.seconds;
import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.trueCondition;

public class RequestLogPage extends AbstractLoggedInApplicationPage {

    protected final @Nonnull ApplicationName applicationName = getSession().getLoggedInApplicationDataOrThrow().application;
    protected @Getter @Setter @Nonnull PublishEnvironment filterEnvironment = live;
    protected @Getter @Setter @Nonnull DateRangeOption dateRange = DateRangeOption.getValues(now(UTC)).get(0);
    protected @Getter @Setter @CheckForNull NodeName filterEndpoint = null;
    protected @Getter @Setter @CheckForNull Integer filterStatusCode = null;
    protected final @Nonnull Set<RequestId> expandedRows = new HashSet<>();
    
    protected @Nonnull Condition getCondition() {
        return REQUEST_LOG.APPLICATION.eq(applicationName)
            .and(REQUEST_LOG.ENVIRONMENT.eq(filterEnvironment))
            .and(REQUEST_LOG.DATETIME.ge(dateRange.getStartDateUtc(now(UTC)).atStartOfDay(UTC).toInstant()))
            .and(REQUEST_LOG.DATETIME.lt(Optional.ofNullable(dateRange.getEndDateUtc(now(UTC))).orElse(now(UTC))
                .plus(1, DAYS).atStartOfDay(UTC).toInstant()))
            .and(filterEndpoint == null ? trueCondition() : REQUEST_LOG.ENDPOINT.eq(filterEndpoint))
            .and(filterStatusCode == null ? trueCondition() : REQUEST_LOG.STATUS_CODE.eq(filterStatusCode));
    }

    @AllArgsConstructor
    protected class RequestLogEntry implements Serializable {
        /** Doesn't have the XML fields populated (they might be huge, don't store them in the session) */
        public RequestLogRecord record;
        public boolean hasParameterTransformationInputXml, hasParameterTransformationOutputXml;
    }

    protected class ResultsModel extends CachingFutureModel<ArrayList<RequestLogEntry>> {
        @SuppressWarnings("SimplifyStreamApiCallChains")
        @Override protected @Nonnull ArrayList<RequestLogEntry> populate() {
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                var fields = new ArrayList<Field<?>>();
                fields.add(field(REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT.isNotNull()));
                fields.add(field(REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT.isNotNull()));
                fields.addAll(asList(REQUEST_LOG.fields()));
                fields.remove(REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT);
                fields.remove(REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT);

                return tx.jooq().select(fields)
                    .from(REQUEST_LOG)
                    .where(getCondition())
                    .orderBy(REQUEST_LOG.DATETIME.desc())
                    .limit(500)
                    .fetch(r -> new RequestLogEntry(r.into(REQUEST_LOG), r.get(0, Boolean.class), r.get(1, Boolean.class)))
                    .stream().collect(toCollection(ArrayList::new));
            }
        }
    }

    protected class ResultsCountModel extends CachingFutureModel<Integer> {
        @Override protected @Nonnull Integer populate() {
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                return tx.jooq().selectCount().from(REQUEST_LOG).where(getCondition()).fetchOne().value1();
            }
        }
    }
    
    protected class XmlDownloadResource extends BaseDataResource<String> {
        protected final @Nonnull RequestId id;
        protected final @Nonnull Field<Element> field;

        public XmlDownloadResource(@Nonnull RequestId id, @Nonnull Field<Element> field, @Nonnull String filename) {
            super("application/xml; charset=utf-8", null, filename);
            this.id = id;
            this.field = field;
        }

        // By default these are cached, which is bad as they have e.g. link8 and if a new request turns up
        // then all the rows are pushed down and link8 might now refer to something else
        @Override protected void configureResponse(ResourceResponse response, Attributes attributes) {
            super.configureResponse(response, attributes);
            response.disableCaching();
        }

        @Override protected Long getLength(String data) { return null; }
        @Override protected void writeData(Response response, String data) { response.write(data); }

        @Override protected @Nonnull String getData(Attributes x) {
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                LoggerFactory.getLogger(getClass()).info("Downloading " + field + " for request_log_id " + id.getId() + "...");
                var element = tx.jooq().select(field).from(REQUEST_LOG).where(REQUEST_LOG.APPLICATION.eq(applicationName))
                    .and(REQUEST_LOG.REQUEST_ID.eq(id)).fetchOne().value1();
                return DomParser.formatXmlPretty(element);
            }
        }
    }
    
    public RequestLogPage() {
        super(NavigationItem.RequestLogPage, null);

        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var endpointsNamesModel = new EndpointNamesModel(this::getFilterEnvironment);
            var statusCodes = tx.jooq().selectDistinct(REQUEST_LOG.STATUS_CODE).from(REQUEST_LOG)
                .where(REQUEST_LOG.APPLICATION.eq(applicationName))
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
            form.add(new DropDownChoice<NodeName>("endpoint", LambdaModel.of(this::getFilterEndpoint, this::setFilterEndpoint),
                endpointsNamesModel, new LambdaDisplayValueChoiceRenderer<>(e -> e.name)).setNullValid(true));
            form.add(new DropDownChoice<>("statusCode", LambdaModel.of(this::getFilterStatusCode, this::setFilterStatusCode),
                statusCodes).setNullValid(true));
            
            var resultsTable = new WebMarkupContainer("results");
            resultsTable.add(new ListView<RequestLogEntry>("row", resultsModel) {
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
                    tableRow.add(new Label("endpoint", rec.getEndpoint().name));
                    tableRow.add(new Label("statusCode", rec.getStatusCode())
                        .add(AttributeAppender.append("class", rec.getStatusCode() >= 300 ? "status-error" : "")));
                    tableRow.add(new Label("incrementalIdPerEndpoint", rec.getIncrementalIdPerEndpoint()));
                    tableRow.add(new Label("randomIdPerApplication", Optional.ofNullable(rec.getRandomIdPerApplication())
                        .map(x -> x.getId()).orElse(null)));
                    item.add(tableRow);
                    
                    var details = new WebMarkupContainer("details");
                    details.add(AttributeAppender.append("style", () -> expandedRows.contains(id) ? "" : "display:none;"));
                    details.setOutputMarkupId(true);
                    details.add(new Label("dateTimeUtc", DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm:ss.SSS, 'UTC'")
                        .format(rec.getDatetime().atZone(UTC))));
                    details.add(new Label("requestId", rec.getRequestId().getId().toString()));
                    details.add(new Label("userAgent", rec.getUserAgent()));
                    details.add(new Label("exceptionMessage", rec.getExceptionMessage())
                        .setVisible(rec.getExceptionMessage() != null));
                    details.add(new Label("httpRequestFailedUrl", rec.getHttpRequestFailedUrl())
                        .setVisible(rec.getHttpRequestFailedUrl() != null));
                    details.add(new Label("httpRequestFailedStatusCode", rec.getHttpRequestFailedStatusCode())
                        .setVisible(rec.getHttpRequestFailedStatusCode() != null));
                    details.add(new Label("xsltParameterErrorMessage", rec.getXsltParameterErrorMessage())
                        .setVisible(rec.getXsltParameterErrorMessage() != null));
                    details.add(new ResourceLink<Element>("downloadInputXml",
                        new XmlDownloadResource(id, REQUEST_LOG.PARAMETER_TRANSFORMATION_INPUT, "input.xml"))
                        .setVisible(entry.hasParameterTransformationInputXml));
                    details.add(new ResourceLink<Element>("downloadOutputXml",
                        new XmlDownloadResource(id, REQUEST_LOG.PARAMETER_TRANSFORMATION_OUTPUT, "output.xml"))
                        .setVisible(entry.hasParameterTransformationOutputXml));
                    details.add(new WebMarkupContainer("outputXmlNotAvailable")
                        .setVisible(!entry.hasParameterTransformationOutputXml));
                    item.add(details);

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
