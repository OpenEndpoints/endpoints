package endpoints;

import com.databasesandlife.util.DailyJob;
import org.slf4j.LoggerFactory;

import java.time.Instant;

import static endpoints.generated.jooq.Tables.REQUEST_LOG;
import static endpoints.generated.jooq.Tables.REQUEST_LOG_EXPRESSION_CAPTURE;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.jooq.impl.DSL.select;

public class RequestLogExpirerJob extends DailyJob {
    
    @Override protected void performJob() {
        var days = DeploymentParameters.get().requestLogExpiryDays;
        if (days == null) {
            LoggerFactory.getLogger(getClass()).info("Request log expiry env var is not set, will not expire request log");
            return;
        }
        
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            tx.jooq()
                .deleteFrom(REQUEST_LOG_EXPRESSION_CAPTURE)
                .where(REQUEST_LOG_EXPRESSION_CAPTURE.REQUEST_ID.in(
                    select(REQUEST_LOG.REQUEST_ID)
                    .from(REQUEST_LOG)
                    .where(REQUEST_LOG.DATETIME.lt(Instant.now().minus(days, DAYS)))
                ))
                .execute();
            tx.jooq()
                .deleteFrom(REQUEST_LOG)
                .where(REQUEST_LOG.DATETIME.lt(Instant.now().minus(days, DAYS)))
                .execute();
            tx.commit();
        }
    }
}
