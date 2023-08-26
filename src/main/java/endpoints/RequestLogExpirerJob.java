package endpoints;

import com.databasesandlife.util.DailyJob;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

import static endpoints.generated.jooq.Tables.REQUEST_LOG;
import static endpoints.generated.jooq.Tables.REQUEST_LOG_EXPRESSION_CAPTURE;
import static java.time.temporal.ChronoUnit.DAYS;
import static org.jooq.impl.DSL.select;

@Slf4j
public class RequestLogExpirerJob extends DailyJob {

    static final int days = 32; // User can only go back 30 days, or within same month (31 days).
    
    @Override protected void performJob() {
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
