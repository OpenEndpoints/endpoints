package endpoints;

import endpoints.config.ApplicationName;
import lombok.RequiredArgsConstructor;
import org.jooq.Condition;
import org.jooq.Field;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.Month;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static endpoints.generated.jooq.Tables.*;
import static org.jooq.impl.DSL.max;
import static org.jooq.impl.DSL.trueCondition;

/**
 * Values which auto-increment as part of an endpoint execution, but which only auto-increment if they're used.
 */
@RequiredArgsConstructor
public class OnDemandIncrementingNumber {
    
    public enum OnDemandIncrementingNumberType {
        perpetual {
            @Override public @Nonnull Field<Integer> getRequestLogIdsField() { return REQUEST_LOG_IDS.ON_DEMAND_PERPETUAL_INCREMENTING_NUMBER; }
            @Override public @Nonnull Condition getRequestLogCondition(Instant now, ZoneId timezone) { return trueCondition(); }
        },
        year {
            @Override public @Nonnull Field<Integer> getRequestLogIdsField() { return REQUEST_LOG_IDS.ON_DEMAND_YEAR_INCREMENTING_NUMBER; }
            @Override public @Nonnull Condition getRequestLogCondition(@Nonnull Instant now, @Nonnull ZoneId timezone) { 
                var nowLocal = now.atZone(timezone).toLocalDateTime();
                var startLocal = LocalDateTime.of(nowLocal.getYear(), Month.JANUARY, 1, 0, 0);
                return REQUEST_LOG.DATETIME.ge(startLocal.atZone(timezone).toInstant())
                    .and(REQUEST_LOG.DATETIME.lt(startLocal.plus(1, ChronoUnit.YEARS).atZone(timezone).toInstant())); 
            }
        },
        month {
            @Override public @Nonnull Field<Integer> getRequestLogIdsField() { return REQUEST_LOG_IDS.ON_DEMAND_MONTH_INCREMENTING_NUMBER; }
            @Override public @Nonnull Condition getRequestLogCondition(@Nonnull Instant now, @Nonnull ZoneId timezone) {
                var nowLocal = now.atZone(timezone).toLocalDateTime();
                var startLocal = LocalDateTime.of(nowLocal.getYear(), nowLocal.getMonth(), 1, 0, 0);
                return REQUEST_LOG.DATETIME.ge(startLocal.atZone(timezone).toInstant())
                    .and(REQUEST_LOG.DATETIME.lt(startLocal.plus(1, ChronoUnit.MONTHS).atZone(timezone).toInstant()));
            }
        };

        public abstract @Nonnull Field<Integer> getRequestLogIdsField();
        public abstract @Nonnull Condition getRequestLogCondition(@Nonnull Instant now, @Nonnull ZoneId timezone);
    }

    protected final @Nonnull ApplicationName application;
    protected final @Nonnull PublishEnvironment environment;
    protected final @Nonnull OnDemandIncrementingNumberType type;
    protected final @Nonnull Instant now;
    
    protected @CheckForNull Integer value;
    
    public synchronized @CheckForNull Integer getValueOrNull() { return value; }
    
    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter") 
    public int getOrFetchValue(@Nonnull ApplicationTransaction tx) {
        synchronized (this) {
            if (value != null) return value;
        }

        int newValue;
        synchronized (tx) {
            tx.acquireLock(environment, application);

            var timezone = tx.db.jooq()
                .select(APPLICATION_CONFIG.TIMEZONE)
                .from(APPLICATION_CONFIG)
                .where(APPLICATION_CONFIG.APPLICATION_NAME.eq(application))
                .fetchOptional().map(r -> r.value1()).orElse(DeploymentParameters.get().singleApplicationModeTimezoneId);
            if (timezone == null) throw new RuntimeException("Unreachable: " +
                "Neither 'application_config' row is present, nor is environment variable set");

            var max = tx.db.jooq()
                .select(max(type.getRequestLogIdsField()))
                .from(REQUEST_LOG_IDS)
                .join(REQUEST_LOG).on(REQUEST_LOG.REQUEST_ID.eq(REQUEST_LOG_IDS.REQUEST_ID))
                .where(REQUEST_LOG_IDS.APPLICATION.eq(application))
                .and(REQUEST_LOG_IDS.ENVIRONMENT.eq(environment))
                .and(type.getRequestLogCondition(now, timezone))
                .fetchSingle().value1();

            if (max == null) newValue = 1;
            else newValue = max + 1;
        }
        
        synchronized (this) {
            return value = newValue;
        }
    }
    
    public static @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> newLazyNumbers(
        @Nonnull ApplicationName application, @Nonnull PublishEnvironment environment, @Nonnull Instant now
    ) {
        return Arrays.stream(OnDemandIncrementingNumberType.values()).collect(Collectors.toMap(t -> t, 
            t ->new OnDemandIncrementingNumber(application, environment, t, now)));
    }
}
