package endpoints;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.ThreadPool;
import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.BufferedHttpResponseDocumentGenerationDestination;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.config.*;
import endpoints.config.ApplicationFactory.ApplicationConfig;
import endpoints.config.response.EmptyResponseConfiguration;
import endpoints.config.response.RedirectResponseConfiguration;
import endpoints.task.Task;
import junit.framework.TestCase;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static endpoints.TransformationContext.ParameterNotFoundPolicy.error;
import static java.net.HttpURLConnection.HTTP_MOVED_PERM;
import static java.net.HttpURLConnection.HTTP_OK;

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
                super(threads, new File("/"), Map.of(), 
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
        
        int threadCount = 10;
        
        ThreadPool p = new ThreadPool();
        p.setThreadCount(threadCount);
        
        for (int i = 0; i < threadCount; i++) { // As this thread stuff involves timing, run it a few times to try and provoke an error
            p.addTask(() -> {
                for (var param : new String[] { "special", "normal" }) {
                    try (var tx = new ApplicationTransaction(Application.newForTesting(Map.of()))) {
                        var output = new StringBuffer();
                        
                        var threads = new XsltCompilationThreads();
                        threads.setThreadCount(10);
                        
                        var endpoint = new Endpoint();
                        endpoint.tasks.add(new MyTask(threads, 0, "a", "", 
                            () -> { sleep(); output.append("a"); }));
                        endpoint.tasks.add(new MyTask(threads, 1, "b", "<after task-id='a'/>", 
                            () -> output.append("b")));
                        endpoint.success = List.of(
                            RedirectResponseConfiguration.newForTesting("if='${param}' equals='special'", "https://foo.com/"),
                            new EmptyResponseConfiguration(DomParser.from("<foo/>"))
                        );
                        
                        var context = new TransformationContext(PublishEnvironment.live, ApplicationName.newRandomForTesting(), 
                            Application.newForTesting(Map.of()), tx, threads,
                            Map.of(new ParameterName("param"), param), error, RequestId.newRandom(), 
                            Request.newForTesting(), Map.of());
                        
                        var consumer = new Consumer<BufferedHttpResponseDocumentGenerationDestination>() {
                            BufferedHttpResponseDocumentGenerationDestination x;
                            @Override public void accept(BufferedHttpResponseDocumentGenerationDestination x) { this.x=x; }
                        };
                        new EndpointExecutor().scheduleTasksAndSuccess(PublishEnvironment.live, ApplicationName.newRandomForTesting(), 
                            new ApplicationConfig(false, false), context, endpoint, 0, Map.of(),
                            new RandomRequestId(12), consumer);
                        
                        threads.execute();
                        
                        assertTrue(context.alreadyDeliveredResponse);
                        assertEquals("ab", output.toString());
                        assertEquals(param.equals("special") ? HTTP_MOVED_PERM : HTTP_OK, consumer.x.getStatusCode());
                    }
                    catch (Exception e) { throw new RuntimeException(e); }
                }
            });
        }
        
        p.execute();
    }
}