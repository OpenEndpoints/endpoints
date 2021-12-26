package endpoints;

import org.slf4j.LoggerFactory;

import java.time.Instant;

import static endpoints.generated.jooq.Tables.REQUEST_LOG;
import static java.time.temporal.ChronoUnit.DAYS;

public class ExpireRequestLogDailyJob extends DailyJob {
    
    @Override protected void performJob() {
        var days = DeploymentParameters.get().requestLogExpiryDays;
        if (days == null) {
            LoggerFactory.getLogger(getClass()).info("Request log expiry env var is not set, will not expire request log");
            return;
        }
        
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            tx.jooq()
                .deleteFrom(REQUEST_LOG)
                .where(REQUEST_LOG.DATETIME.lt(Instant.now().minus(days, DAYS)))
                .execute();
            tx.commit();
        }
    }
}
