package endpoints;

import endpoints.config.ApplicationName;

import javax.annotation.Nonnull;
import java.io.Serializable;

import static endpoints.generated.jooq.Tables.REQUEST_LOG_IDS;
import static org.apache.commons.lang.RandomStringUtils.random;
import static org.apache.commons.lang.RandomStringUtils.randomNumeric;

public record RandomRequestId(
    long id
) implements Serializable {

    public static @Nonnull RandomRequestId generate(
        @Nonnull ApplicationTransaction tx,
        @Nonnull ApplicationName applicationName, @Nonnull PublishEnvironment environment
    ) {
        tx.acquireLock(environment, applicationName);
        
        for (int attempt = 0; attempt < 100; attempt++) {
            // 10 digits, starting with a non-zero, so it is always 10 characters long
            var candidate = new RandomRequestId(Long.parseLong(random(1, "123456789") + randomNumeric(9)));

            var existingCount = tx.db.jooq()
                .selectCount()
                .from(REQUEST_LOG_IDS)
                .where(REQUEST_LOG_IDS.APPLICATION.eq(applicationName))
                .and(REQUEST_LOG_IDS.ENVIRONMENT.eq(environment))
                .and(REQUEST_LOG_IDS.RANDOM_ID_PER_APPLICATION.eq(candidate))
                .fetchSingle().value1();
            if (existingCount == 0) return candidate;
        }

        throw new RuntimeException("Cannot find new random number");
    }
}
