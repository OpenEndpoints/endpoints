package endpoints;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.config.ApplicationName;
import endpoints.config.NodeName;
import endpoints.generated.jooq.tables.records.ApplicationConfigRecord;
import endpoints.generated.jooq.tables.records.RequestLogRecord;
import junit.framework.TestCase;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.perpetual;
import static endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType.year;
import static java.time.temporal.ChronoUnit.HOURS;

public class OnDemandIncrementingNumberTest extends TestCase {
    
    protected void insertRequestLog(@Nonnull DbTransaction tx, @Nonnull ApplicationName app, @Nonnull Instant when) {
        var r = new RequestLogRecord();
        r.setApplication(app);
        r.setEndpoint(new NodeName("endpoint"));
        r.setDatetimeUtc(when);
        r.setStatusCode(200);
        r.setUserAgent("user agent");
        r.setEnvironment(PublishEnvironment.live);
        r.setOnDemandPerpetualIncrementingNumber(1);
        r.setOnDemandYearIncrementingNumber(10);
        r.setOnDemandMonthIncrementingNumber(100);
        tx.insert(r);
    }
    
    protected void performTest(
        int expectedValue, @Nonnull DbTransaction tx, @Nonnull ApplicationConfigRecord app,
        @Nonnull OnDemandIncrementingNumberType type, @Nonnull Instant now, @Nonnull String timezone
    ) {
        app.setTimezone(ZoneId.of(timezone));
        app.update();
        
        var obj = new OnDemandIncrementingNumber(app.getApplicationName(), PublishEnvironment.live, type, now);
        assertEquals(expectedValue, obj.getOrFetchValue(tx));
    }

    public void testGetOrFetchValue() {
        var app = ApplicationName.newRandomForTesting();

        try (var tx = DeploymentParameters.get().newDbTransaction()) {

            // Request logged at:                     One hour later (now) is:
            // 2018-12-31 23:30:00 CET                2019-01-01 00:30:00 CET                
            // 2018-12-31 22:30:00 UTC                2018-12-31 23:30:00 UTC
            var requestLog = LocalDateTime.of(2018, 12, 31, 22, 30).atOffset(ZoneOffset.UTC).toInstant();
            var oneHourLaterNow = requestLog.plus(1, HOURS);
            
            var appRow = new ApplicationConfigRecord();
            appRow.setApplicationName(app);
            appRow.setDisplayName("unit test");
            tx.insert(appRow);
            
            performTest(1, tx, appRow, perpetual, oneHourLaterNow, "UTC");
            insertRequestLog(tx, app, requestLog);
            performTest(2, tx, appRow, perpetual, oneHourLaterNow, "CET");

            performTest(11, tx, appRow, year, oneHourLaterNow, "UTC");
            performTest(1, tx, appRow, year, oneHourLaterNow, "CET");
        }
    }
}