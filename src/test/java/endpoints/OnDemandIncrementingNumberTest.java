package endpoints;

import com.databasesandlife.util.jdbc.DbTransaction;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.destination.BufferedHttpResponseDocumentGenerationDestination;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.config.Application;
import endpoints.config.ApplicationName;
import endpoints.config.NodeName;
import endpoints.generated.jooq.tables.records.RequestLogIdsRecord;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import junit.framework.TestCase;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.perpetual;
import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.year;
import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static java.time.temporal.ChronoUnit.HOURS;

public class OnDemandIncrementingNumberTest extends TestCase {
    
    protected void insertRequestLog(@Nonnull DbTransaction tx, @Nonnull ApplicationName app, @Nonnull Instant when) {
        var ids = new RequestLogIdsRecord();
        ids.setRequestId(RequestId.newRandom());
        ids.setApplication(app);
        ids.setEndpoint(new NodeName("endpoint"));
        ids.setEnvironment(PublishEnvironment.live);
        ids.setOnDemandPerpetualIncrementingNumber(1);
        ids.setOnDemandYearIncrementingNumber(10);
        ids.setOnDemandMonthIncrementingNumber(100);
        tx.insert(ids);

        var r = new RequestLogRecord();
        r.setRequestId(ids.getRequestId());
        r.setDatetime(when);
        r.setStatusCode(200);
        r.setUserAgent("user agent");
        tx.insert(r);
    }
    
    protected void performTest(
        int expectedValue, @Nonnull ApplicationTransaction tx, @Nonnull ApplicationName applicationName,
        @Nonnull OnDemandIncrementingNumberType type, @Nonnull Instant now, @Nonnull String timezone
    ) {
        var app = tx.db.jooq().fetchSingle(APPLICATION_CONFIG, APPLICATION_CONFIG.APPLICATION_NAME.eq(applicationName));
        app.setTimezone(ZoneId.of(timezone));
        app.update();
        
        var obj = new OnDemandIncrementingNumber(app.getApplicationName(), PublishEnvironment.live, type, now);
        assertEquals(expectedValue, obj.getOrFetchValue(tx));
    }

    public void testGetOrFetchValue() {
        var app = ApplicationName.newRandomForTesting();

        try (var tx = new ApplicationTransaction(Application.newForTesting())) {
            app.insertToDbForTesting(tx.db, PublishEnvironment.live);

            // Request logged at:                     One hour later (now) is:
            // 2018-12-31 23:30:00 CET                2019-01-01 00:30:00 CET                
            // 2018-12-31 22:30:00 UTC                2018-12-31 23:30:00 UTC
            var requestLog = LocalDateTime.of(2018, 12, 31, 22, 30).atOffset(ZoneOffset.UTC).toInstant();
            var oneHourLaterNow = requestLog.plus(1, HOURS);
            
            performTest(1, tx, app, perpetual, oneHourLaterNow, "UTC");
            insertRequestLog(tx.db, app, requestLog);
            performTest(2, tx, app, perpetual, oneHourLaterNow, "CET");

            performTest(11, tx, app, year, oneHourLaterNow, "UTC");
            performTest(1, tx, app, year, oneHourLaterNow, "CET");
        }
    }

    public void testGetOrFetchValue_parallel() throws Exception {
        var now = LocalDateTime.of(2018, 12, 31, 22, 30).atOffset(ZoneOffset.UTC).toInstant();
        var applicationName = ApplicationName.newRandomForTesting();
        var environment = PublishEnvironment.live;
        var endpoint = new NodeName("endpoint");

        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            applicationName.insertToDbForTesting(tx, environment);
            tx.commit();
        }

        var pool = new WeaklyCachedXsltTransformer.XsltCompilationThreads();
        pool.setThreadCount(10);

        // Map from the auto-increment number to the number of times it was found
        var numbers = new HashMap<Integer, Integer>();

        var taskCount = 20;
        for (int i = 0; i < taskCount; i++) {
            pool.addTask(() -> {
                try (var tx = new ApplicationTransaction(Application.newForTesting())) {
                    // Allocate our number
                    var obj = new OnDemandIncrementingNumber(applicationName, PublishEnvironment.live, perpetual, now);
                    var requestAutoInc = obj.getOrFetchValue(tx);
                    synchronized (numbers) {
                        numbers.putIfAbsent(requestAutoInc, 0);
                        numbers.put(requestAutoInc, numbers.get(requestAutoInc) + 1);
                    }
                    
                    // Record in request_log
                    var inc = new HashMap<>(OnDemandIncrementingNumber.newLazyNumbers(applicationName, environment, Instant.now()));
                    inc.put(perpetual, obj);
                    new EndpointExecutor().insertRequestLog(tx.db, applicationName, environment, endpoint, now, 
                        RequestId.newRandom(), Request.newForTesting(), true, true, true,
                        new EndpointExecutor.ParameterTransformationLogger(), inc, Map.of(),
                        new BufferedHttpResponseDocumentGenerationDestination(), r -> {});
                    
                    tx.commit();
                }
            });
        }
        pool.execute();

        for (int i = 1; i <= taskCount; i++) {
            // Expect each number to be found once
            assertEquals("Number "+i, (Integer)1, numbers.get(i));
        }
    }
}
