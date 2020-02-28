package endpoints;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.ThreadPool;
import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.config.*;
import endpoints.config.ApplicationFactory.ApplicationConfig;
import endpoints.task.Task;
import junit.framework.TestCase;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static endpoints.TransformationContext.ParameterNotFoundPolicy.error;

public class EndpointExecutorTest extends TestCase {
    
    @SuppressWarnings("SameParameterValue") 
    protected static void sleep() {
        try { Thread.sleep(100); }
        catch (InterruptedException ignored) { }
    }

    public void testScheduleTasksAndSuccess() throws Exception {
        
        class MyTask extends Task {
            
            final @Nonnull Runnable task;

            MyTask(
                @Nonnull XsltCompilationThreads threads, int idx, 
                @Nonnull String id, @Nonnull String afterXml, @Nonnull Runnable task
            ) throws ConfigurationException  {
                super(threads, new File("/"), Collections.emptyMap(), 
                    new File("/"), idx, DomParser.from("<foo id='"+id+"'>"+afterXml+"</foo>"));
                this.task = task;
            }

            @Override protected void executeThenScheduleSynchronizationPoint(
                @Nonnull TransformationContext context, @Nonnull SynchronizationPoint workComplete
            ) {
                task.run();
                context.threads.addTask(workComplete);
            }
        }
        
        int threadCount = 25;
        
        ThreadPool p = new ThreadPool();
        p.setThreadCount(threadCount);
        
        for (int i = 0; i < threadCount; i++) { // As this thread stuff involves timing, run it a few times to try and provoke an error
            var output = new StringBuffer();

            p.addTask(() -> {
                try (ApplicationTransaction tx = new ApplicationTransaction(Application.newForTesting(Map.of()))) {
                    var threads = new XsltCompilationThreads();
                    threads.setThreadCount(10);
                    
                    var endpoint = new Endpoint();
                    endpoint.tasks.add(new MyTask(threads, 0, "a", "", 
                        () -> { sleep(); output.append("a"); }));
                    endpoint.tasks.add(new MyTask(threads, 1, "b", "<after task-id='a'/>", 
                        () -> output.append("b")));
                    endpoint.success = new EmptyResponseConfiguration(DomParser.from("<foo/>"));
                    
                    var context = new TransformationContext(Application.newForTesting(Map.of()), tx, threads,
                        Map.of(), error, List.of(), Map.of());
                    
                    new EndpointExecutor().scheduleTasksAndSuccess(PublishEnvironment.live, ApplicationName.newRandomForTesting(), 
                        new ApplicationConfig(false, false), context, endpoint, Map.of(), 
                        0, new RandomRequestId(12), r -> {});
                    
                    threads.execute();
                    
                    assertEquals("ab", output.toString());
                }
                catch (Exception e) { throw new RuntimeException(e); }
            });
        }
        
        p.execute();
    }
}