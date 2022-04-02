package endpoints;

import com.databasesandlife.util.Timer;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Random;

import static java.time.ZoneOffset.UTC;

/**
 * A daily job is something that runs once a day, for example cleanup operation.
 * See the LyX doc for more information.
 */
public abstract class DailyJob {
    
    protected final static Random random = new Random();
    
    /** When to run the daily job, in UTC */
    protected final LocalTime scheduleUtc;
    
    public DailyJob() {
        scheduleUtc = LocalTime.of(random.nextInt(24), random.nextInt(60), 0);
    }
    
    public void start() {
        var thread = new Thread(this::runThread, getClass().getSimpleName());
        thread.start();
    }
    
    protected abstract void performJob();
    
    @SuppressWarnings("InfiniteLoopStatement") 
    protected void runThread() {
        LoggerFactory.getLogger(getClass()).info("Scheduling " + getClass().getSimpleName()
            + " daily at " + scheduleUtc.format(DateTimeFormatter.ofPattern("HH:mm")) + " UTC");
        
        while (true) {
            var currentTimeUtc = LocalTime.now(UTC);
            var wait = Duration.between(currentTimeUtc, scheduleUtc);
            if (wait.isNegative()) wait = wait.plusDays(1);
            if (wait.isNegative()) throw new RuntimeException("Unreachable");
            LoggerFactory.getLogger(getClass()).info(String.format(
                "Waiting %.1f hours until next %s", (double) wait.getSeconds() / 60 / 60, getClass().getSimpleName()));
            
            try { Thread.sleep(wait.toMillis()); }
            catch (InterruptedException ignored) { }
            
            try (var ignored = new Timer("Execute '"+getClass().getSimpleName() + "'")) {
                performJob(); 
            }
            catch (Throwable e) {
                LoggerFactory.getLogger(getClass()).error(getClass().getSimpleName() + " threw excpetion", e);
            }
        }
    }
}
