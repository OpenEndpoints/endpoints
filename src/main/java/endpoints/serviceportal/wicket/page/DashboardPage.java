package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.Timer;
import com.databasesandlife.util.wicket.CachingFutureModel;
import com.databasesandlife.util.wicket.LambdaDisplayValueChoiceRenderer;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.config.ApplicationName;
import endpoints.config.NodeName;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import endpoints.serviceportal.DateRangeOption;
import endpoints.serviceportal.DateRangeOption.LastNDaysOption;
import endpoints.serviceportal.DateRangeOption.ThisMonthOption;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.model.EndpointNamesModel;
import endpoints.serviceportal.wicket.panel.NavigationPanel.NavigationItem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.wicket.ajax.AjaxRequestTarget;
import org.apache.wicket.ajax.markup.html.AjaxFallbackLink;
import org.apache.wicket.behavior.AttributeAppender;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.LambdaModel;
import org.apache.wicket.util.convert.IConverter;
import org.apache.wicket.util.convert.converter.IntegerConverter;
import org.jooq.Condition;
import org.jooq.Result;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static endpoints.PublishEnvironment.live;
import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static endpoints.generated.jooq.Tables.REQUEST_LOG;
import static java.time.LocalDate.now;
import static java.time.ZoneOffset.UTC;
import static java.time.temporal.ChronoUnit.DAYS;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toMap;
import static org.apache.wicket.ajax.AbstractAjaxTimerBehavior.onTimer;
import static org.apache.wicket.util.time.Duration.seconds;
import static org.jooq.impl.DSL.count;
import static org.jooq.impl.DSL.trueCondition;

public class DashboardPage extends AbstractLoggedInPage {

    protected final @Nonnull ApplicationName applicationName = getSession().getLoggedInDataOrThrow().application;
    protected @Getter @Setter @Nonnull PublishEnvironment filterEnvironment = live;
    protected @Getter @Setter @Nonnull DateRangeOption dateRange = DateRangeOption.getValues(now(UTC)).get(0);
    protected @Getter @Setter @CheckForNull NodeName filterEndpoint = null;
    protected @Getter @Setter @CheckForNull Integer filterStatusCode = null;
    protected final @Nonnull Set<RequestLogRecord> expandedRows = new HashSet<>();
    
    protected @Nonnull Condition getCondition() {
        return REQUEST_LOG.APPLICATION.eq(applicationName)
            .and(REQUEST_LOG.ENVIRONMENT.eq(filterEnvironment))
            .and(REQUEST_LOG.DATETIME_UTC.ge(dateRange.getStartDateUtc(now(UTC)).atStartOfDay(UTC).toInstant()))
            .and(REQUEST_LOG.DATETIME_UTC.lt(Optional.ofNullable(dateRange.getEndDateUtc(now(UTC))).orElse(now(UTC))
                .plus(1, DAYS).atStartOfDay(UTC).toInstant()))
            .and(filterEndpoint == null ? trueCondition() : REQUEST_LOG.ENDPOINT.eq(filterEndpoint))
            .and(filterStatusCode == null ? trueCondition() : REQUEST_LOG.STATUS_CODE.eq(filterStatusCode));
    }

    @RequiredArgsConstructor
    class KpiModel extends CachingFutureModel<Integer> {
        protected final @Nonnull DateRangeOption dateOption;
        @Override protected @Nonnull Integer populate() {
            try (var tx = DeploymentParameters.get().newDbTransaction(); var ignored = new Timer("KPI " + dateOption) ) {
                return tx.jooq().selectCount()
                    .from(REQUEST_LOG)
                    .where(REQUEST_LOG.APPLICATION.eq(applicationName))
                    .and(REQUEST_LOG.ENVIRONMENT.eq(live))
                    .and(REQUEST_LOG.DATETIME_UTC.ge(dateOption.getStartDateUtc(now(UTC)).atStartOfDay(UTC).toInstant()))
                    .fetchOne().value1();
            }
        }
    }

    protected class ResultsModel extends CachingFutureModel<Result<RequestLogRecord>> {
        @Override protected @Nonnull Result<RequestLogRecord> populate() {
            try (var tx = DeploymentParameters.get().newDbTransaction()) {
                return tx.jooq().selectFrom(REQUEST_LOG)
                    .where(getCondition())
                    .orderBy(REQUEST_LOG.DATETIME_UTC.desc())
                    .limit(500)
                    .fetch();
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
    
    protected static class LabelWithThousandSeparator extends Label {
        public LabelWithThousandSeparator(String id, IModel<Integer> model) { super(id, model); }

        @SuppressWarnings("unchecked") 
        @Override public <C> IConverter<C> getConverter(Class<C> type) {
            return (IConverter<C>) new IntegerConverter() {
                @Override protected NumberFormat newNumberFormat(Locale locale) {
                    return new DecimalFormat("#,##0");
                }
            };
        }
    }

    public DashboardPage() {
        super(NavigationItem.DashboardPage, null);

        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            var kpiTransactionsTodayModel = new KpiModel(new LastNDaysOption("", 1));
            var kpiTransactionsTodayLabel = new LabelWithThousandSeparator("transactionsToday", kpiTransactionsTodayModel);
            add(kpiTransactionsTodayLabel.setOutputMarkupId(true));

            var kpiTransactionsLast7DaysModel = new KpiModel(new LastNDaysOption("", 7));
            var kpiTransactionsLast7DaysLabel = new LabelWithThousandSeparator("transactionsLast7Days", kpiTransactionsLast7DaysModel);
            add(kpiTransactionsLast7DaysLabel.setOutputMarkupId(true));

            var kpiTransactionsThisMonthModel = new KpiModel(new ThisMonthOption(""));
            var kpiTransactionsThisMonthLabel = new LabelWithThousandSeparator("transactionsThisMonth", kpiTransactionsThisMonthModel);
            add(kpiTransactionsThisMonthLabel.setOutputMarkupId(true));
            
            var includedRequestsPerMonth = tx.jooq().select(APPLICATION_CONFIG.INCLUDED_REQUESTS_PER_MONTH)
                .from(APPLICATION_CONFIG).where(APPLICATION_CONFIG.APPLICATION_NAME.eq(applicationName)).fetchOne().value1();
            add(new LabelWithThousandSeparator("includedRequestsPerMonth", () -> includedRequestsPerMonth)
                .setVisible(includedRequestsPerMonth != null));
            
            var endpointsNamesModel = new EndpointNamesModel(this::getFilterEnvironment);
            var statusCodes = tx.jooq().selectDistinct(REQUEST_LOG.STATUS_CODE).from(REQUEST_LOG)
                .where(REQUEST_LOG.APPLICATION.eq(applicationName))
                .and(REQUEST_LOG.DATETIME_UTC.ge(DateRangeOption.getValues(now(UTC)).stream()
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
            resultsTable.add(new ListView<RequestLogRecord>("row", resultsModel) {
                @Override protected void populateItem(@Nonnull ListItem<RequestLogRecord> item) {
                    var rec = item.getModelObject();

                    // We have to have this as UTC, because:
                    // The top KPI numbers have to be UTC because they are used for billing, all users must see the same data
                    // You want to select the KPI time range in the drop-down for verificaiton, so drop-down must also act on UTC
                    // If you select "today" in the UTC drop-down, you don't want to see yesterday in the table, so table must also be UTC
                    var tableRow = new WebMarkupContainer("tableRow");
                    tableRow.setOutputMarkupId(true);
                    tableRow.add(AttributeAppender.append("class", () -> expandedRows.contains(rec) ? "open-row" : ""));
                    tableRow.add(new Label("dateTimeShortUtc", DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm")
                        .format(rec.getDatetimeUtc().atOffset(UTC))));
                    tableRow.add(new Label("endpoint", rec.getEndpoint().name));
                    tableRow.add(new Label("statusCode", rec.getStatusCode())
                        .add(AttributeAppender.append("class", rec.getStatusCode() >= 300 ? "status-error" : "")));
                    tableRow.add(new Label("incrementalIdPerEndpoint", rec.getIncrementalIdPerEndpoint()));
                    tableRow.add(new Label("randomIdPerApplication", rec.getRandomIdPerApplication()));
                    item.add(tableRow);
                    
                    var details = new WebMarkupContainer("details");
                    details.add(AttributeAppender.append("style", () -> expandedRows.contains(rec) ? "" : "display:none;"));
                    details.setOutputMarkupId(true);
                    details.add(new Label("dateTimeUtc", DateTimeFormatter.ofPattern("d MMMM yyyy, HH:mm:ss.SSS, 'UTC'")
                        .format(rec.getDatetimeUtc().atZone(UTC))));
                    details.add(new Label("userAgent", rec.getUserAgent()));
                    details.add(new Label("exceptionMessage", rec.getExceptionMessage())
                        .setVisible(rec.getExceptionMessage() != null));
                    details.add(new Label("httpRequestFailedUrl", rec.getHttpRequestFailedUrl())
                        .setVisible(rec.getHttpRequestFailedUrl() != null));
                    details.add(new Label("httpRequestFailedStatusCode", rec.getHttpRequestFailedStatusCode())
                        .setVisible(rec.getHttpRequestFailedStatusCode() != null));
                    details.add(new Label("xsltParameterErrorMessage", rec.getXsltParameterErrorMessage())
                        .setVisible(rec.getXsltParameterErrorMessage() != null));
                    item.add(details);

                    // We implement the [+] opening of rows in Wicket, not Javascript
                    // It would be possible to do this in Javascript on the client, that would be faster for the user.
                    // However, it would be more difficult to remember which items were open across AJAX reloads
                    // And would also have lower maintainability, due to more technologies, and generally more complex solution
                    var toggle = new AjaxFallbackLink<Void>("toggle") {
                        @Override public void onClick(Optional<AjaxRequestTarget> redrawTarget) {
                            if (expandedRows.contains(rec)) expandedRows.remove(rec); else expandedRows.add(rec);
                            redrawTarget.ifPresent(t -> t.add(tableRow, details));
                        }
                    };
                    toggle.add(new Label("text", () -> expandedRows.contains(rec) ? "Hide" : "Show"));
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
                kpiTransactionsTodayModel.refresh();
                kpiTransactionsLast7DaysModel.refresh();
                kpiTransactionsThisMonthModel.refresh();
                resultsModel.refresh();
                resultsCountModel.refresh();
                target.add(kpiTransactionsTodayLabel, kpiTransactionsLast7DaysLabel, kpiTransactionsThisMonthLabel, resultsTable);
            }));
        }
    }
}
