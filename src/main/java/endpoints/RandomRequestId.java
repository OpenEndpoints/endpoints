package endpoints;

import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.config.ApplicationName;
import lombok.Value;

import javax.annotation.Nonnull;

import java.io.Serializable;

import static endpoints.generated.jooq.Tables.REQUEST_LOG;
import static org.apache.commons.lang.RandomStringUtils.random;
import static org.apache.commons.lang.RandomStringUtils.randomNumeric;

@Value
public class RandomRequestId implements Serializable {
    
    long id;

    protected static @Nonnull RandomRequestId generate(
        @Nonnull DbTransaction tx,
        @Nonnull ApplicationName applicationName, @Nonnull PublishEnvironment environment
    ) {
        for (int attempt = 0; attempt < 100; attempt++) {
            // 10 digits, starting with a non-zero so it is always 10 characters long
            var candidate = new RandomRequestId(Long.parseLong(random(1, "123456789") + randomNumeric(9)));

            var existingCount = tx.jooq()
                .selectCount()
                .from(REQUEST_LOG)
                .where(REQUEST_LOG.RANDOM_ID_PER_APPLICATION.eq(candidate))
                .and(REQUEST_LOG.APPLICATION.eq(applicationName))
                .and(REQUEST_LOG.ENVIRONMENT.eq(environment))
                .fetchOne().value1();
            if (existingCount == 0) return candidate;
        }

        throw new RuntimeException("Cannot find new random number");
    }
}
