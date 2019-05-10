package endpoints.task;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import endpoints.TransformationContext;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;

public abstract class Task {
    
    protected @Nonnull TaskCondition condition;
    
    // Subclass must implement this constructor, as it is called by reflection
    @SuppressWarnings("unused") 
    public Task(
        @Nonnull WeaklyCachedXsltTransformer.XsltCompilationThreads threads, @Nonnull File httpXsltDirectory,
        @Nonnull Map<String, Transformer> transformers, @Nonnull File staticDir, Element config
    ) throws ConfigurationException {
        condition = new TaskCondition(config);
    }

    public static class TaskExecutionFailedException extends Exception {
        public TaskExecutionFailedException(Throwable e) { super(e); }
        public TaskExecutionFailedException(String prefix, Throwable e) {
            super(ConfigurationException.prefixExceptionMessage(prefix, e), e);
        }
    }

    public boolean requiresEmailServer() { return false; }
    
    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException { 
        condition.assertParametersSuffice(params);
    }
    
    public void assertTemplatesValid() throws DocumentTemplateInvalidException { }
    
    protected abstract @Nonnull void scheduleTaskExecutionUnconditionally(@Nonnull TransformationContext context) 
    throws TaskExecutionFailedException;
    
    /**
     * Starts execution of this task, and creates a runnable which will complete execution of this task.
     *    <p>
     * To use this method:
     * <ol>
     * <li>Create an {@link ExecutorService}</li>
     * <li>On all tasks, call this method. (This will create "futures" for e.g. XSLT processing, so that can happen
     * concurrently across all tasks at the same time.)</li>
     * <li>On all tasks, run the {@link Runnable}. (This will await the results of the futures, and execute the task.)
     * <li>Shutdown the {@link ExecutorService}
     * </ol>
     */
    public @Nonnull void scheduleTaskExecution(@Nonnull TransformationContext context) throws TaskExecutionFailedException {
        if (condition.evaluate(context.params))
            scheduleTaskExecutionUnconditionally(context);
    }
}
