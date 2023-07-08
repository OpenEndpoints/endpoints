package endpoints.task;

import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.Timer;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.condition.Condition;
import endpoints.TransformationContext;
import endpoints.config.EmailSendingConfigurationFactory;
import endpoints.config.EndpointExecutionParticipant;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.util.*;

import static com.databasesandlife.util.DomParser.getOptionalAttribute;

public abstract class Task extends EndpointExecutionParticipant {
    
    protected final int taskIndexFromZero;
    protected final @CheckForNull TaskId id;
    
    /** Note the condition cannot reference "intermediate values" */
    public final @Nonnull Condition condition;
    
    // Subclass must implement this constructor, as it is called by reflection
    @SuppressWarnings("unused") 
    public Task(
        @Nonnull XsltCompilationThreads threads, @Nonnull File applicationDir, 
        @Nonnull Map<String, Transformer> transformers, int indexFromZero, @Nonnull Element config
    ) throws ConfigurationException {
        super(config);
        
        this.taskIndexFromZero = indexFromZero;
        this.id = Optional.ofNullable(getOptionalAttribute(config, "id")).map(x -> new TaskId(x)).orElse(null);
        this.condition = new Condition(config);
    }

    public @CheckForNull TaskId getTaskIdOrNull() {
        return id;
    }
    
    protected @Nonnull String ordinal(int n) {
        return switch (n) {
            case 1 -> "1st";
            case 2 -> "2nd";
            case 3 -> "3rd";
            default -> n + "th";
        };
    }

    public @Nonnull String getHumanReadableId() {
        if (id == null) return ordinal(taskIndexFromZero+1) + " <task>";
        else return "<task id='" + id.id() + "'/>";
    }

    public static class TaskExecutionFailedException extends Exception {
        public TaskExecutionFailedException(Throwable e) { super(e); }
        public TaskExecutionFailedException(String prefix, Throwable e) {
            super(ConfigurationException.prefixExceptionMessage(prefix, e), e);
        }
    }

    public void assertParametersSuffice(@Nonnull Set<ParameterName> params)
    throws ConfigurationException { 
        condition.assertParametersSuffice(params, inputIntermediateValues);
    }
    
    public void assertCompatibleWithEmailConfig(
        @CheckForNull EmailSendingConfigurationFactory config, @Nonnull Set<ParameterName> params
    ) throws ConfigurationException {
    }

    public void assertTemplatesValid() throws DocumentTemplateInvalidException { }
    
    /**
     * @implSpec
     *      This method is run within the thread pool.
     *      All intermediate values have been calculated by the time this method is called. 
     */
    protected abstract void executeThenScheduleSynchronizationPoint(
        @Nonnull TransformationContext context,
        @Nonnull SynchronizationPoint workComplete
    ) throws TaskExecutionFailedException;
    
    protected void logAndExecuteThenScheduleSynchronizationPoint(
        @Nonnull TransformationContext context,
        @Nonnull SynchronizationPoint workComplete
    ) throws TaskExecutionFailedException {
        try (var ignored = new Timer("<endpoint name='" + context.endpoint.name.getName() + "'>: " + getHumanReadableId())) {
            executeThenScheduleSynchronizationPoint(context, workComplete);
        }
    }

    /**
     * @return this will be scheduled in the thread pool once the execution is complete and intermediate values are available. 
     */
    public @Nonnull SynchronizationPoint scheduleTaskExecutionIfNecessary(
        @Nonnull List<SynchronizationPoint> dependencies, 
        @Nonnull TransformationContext context
    ) {
        var workComplete = new SynchronizationPoint();
        
        context.threads.addTaskWithDependencies(dependencies, () -> {
            try {
                var stringParams = context.getStringParametersIncludingIntermediateValues(inputIntermediateValues);
                if (condition.evaluate(context.endpoint.getParameterMultipleValueSeparator(), stringParams))
                    logAndExecuteThenScheduleSynchronizationPoint(context, workComplete);
                else
                    context.threads.addTask(workComplete);
            }
            catch (TaskExecutionFailedException e) { throw new RuntimeException(e); }
        });

        return workComplete;
    }
}
