package endpoints.serviceportal;

import lombok.*;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

@EqualsAndHashCode
public abstract class DateRangeOption implements Serializable {
    
    public static @Nonnull List<DateRangeOption> getValues(@Nonnull LocalDate todayUtc) {
        var result = new ArrayList<DateRangeOption>();
        result.add(new LastNDaysOption("Last 30 days", 30));
        result.add(new ThisMonthOption("This Month"));
        result.add(new LastNDaysOption("Last 7 days", 7));
        result.add(new LastNDaysOption("Today", 1));
        for (int daysAgo = 1; daysAgo <= 30; daysAgo++) result.add(new OneDayOption(todayUtc.minus(daysAgo, ChronoUnit.DAYS)));
        return result;
    }

    @EqualsAndHashCode(callSuper = true) @ToString
    @RequiredArgsConstructor
    public static class LastNDaysOption extends DateRangeOption {
        protected final @Nonnull String displayName;
        protected final int dayCountIncludingToday;

        @Override public @Nonnull String getDisplayName() {
            return displayName;
        }

        @Override public @Nonnull LocalDate getStartDateUtc(@Nonnull LocalDate todayUtc) {
            return todayUtc.minus(dayCountIncludingToday - 1, ChronoUnit.DAYS);
        }
    }

    @EqualsAndHashCode(callSuper = true) @ToString
    @RequiredArgsConstructor
    public static class ThisMonthOption extends DateRangeOption {
        protected final @Nonnull String displayName;

        @Override public @Nonnull String getDisplayName() { 
            return displayName; 
        }
        
        @Override public @Nonnull LocalDate getStartDateUtc(@Nonnull LocalDate todayUtc) {
            return todayUtc.withDayOfMonth(1);
        }
    }

    @EqualsAndHashCode(callSuper = true) @ToString
    @RequiredArgsConstructor
    public static class OneDayOption extends DateRangeOption {
        protected final @Nonnull LocalDate dateUtc;

        @Override public @Nonnull String getDisplayName() {
            return DateTimeFormatter.ofPattern("dd MMM yyyy").format(dateUtc);
        }

        @Override public @Nonnull LocalDate getStartDateUtc(@Nonnull LocalDate todayUtc) { 
            return dateUtc;
        }
        
        @Override public @Nonnull LocalDate getEndDateUtc(@Nonnull LocalDate todayUtc) {
            return dateUtc;
        }
    }

    public abstract @Nonnull String getDisplayName();
    public abstract @Nonnull LocalDate getStartDateUtc(@Nonnull LocalDate todayUtc);
    public @CheckForNull LocalDate getEndDateUtc(@Nonnull LocalDate todayUtc) { return null; }
}
