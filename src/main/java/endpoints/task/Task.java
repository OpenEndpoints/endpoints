package endpoints.task;

import com.databasesandlife.util.ThreadPool.SynchronizationPoint;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.DocumentTemplateInvalidException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
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
    public final @Nonnull TaskCondition condition;
    
    // Subclass must implement this constructor, as it is called by reflection
    @SuppressWarnings("unused") 
    public Task(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory,
        @Nonnull Map<String, Transformer> transformers, @Nonnull File staticDir, int indexFromZero, @Nonnull Element config
    ) throws ConfigurationException {
        super(config);
        taskIndexFromZero = indexFromZero;
        id = Optional.ofNullable(getOptionalAttribute(config, "id")).map(x -> new TaskId(x)).orElse(null);
        condition = new TaskCondition(config);
    }

    public @CheckForNull TaskId getTaskIdOrNull() {
        return id;
    }
    
    protected @Nonnull String ordinal(int n) {
        switch (n) {
            case 1: return "1st";
            case 2: return "2nd";
            case 3: return "3rd";
            default: return n + "th";
        }
    }

    protected @Nonnull String getHumanReadableId() {
        if (id == null) return ordinal(taskIndexFromZero+1) + " <task>";
        else return "<task id='" + id.id + "'/>";
    }

    public static class TaskExecutionFailedException extends Exception {
        public TaskExecutionFailedException(Throwable e) { super(e); }
        public TaskExecutionFailedException(String prefix, Throwable e) {
            super(ConfigurationException.prefixExceptionMessage(prefix, e), e);
        }
    }

    public boolean requiresEmailServer() { return false; }
    
    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException { 
        condition.assertParametersSuffice(params, inputIntermediateValues);
    }
    
    public void assertTemplatesValid() throws DocumentTemplateInvalidException { }
    
    /** Note: All intermediate values have been calculated by the time this method is called. */
    protected abstract void executeThenScheduleSynchronizationPoint(
        @Nonnull TransformationContext context,
        @Nonnull SynchronizationPoint workComplete
    );
    
    /**
     * @return this will be scheduled in the thread pool once the execution is complete and intermediate values are available. 
     */
    public @Nonnull SynchronizationPoint scheduleTaskExecutionIfNecessary(
        @Nonnull List<SynchronizationPoint> dependencies, 
        @Nonnull TransformationContext context
    ) {
        var workComplete = new SynchronizationPoint();
        
        context.threads.addTaskWithDependencies(dependencies, () -> {
            var stringParams = context.getStringParametersIncludingIntermediateValues(inputIntermediateValues);
            if (condition.evaluate(stringParams))
                executeThenScheduleSynchronizationPoint(context, workComplete);
            else
                context.threads.addTask(workComplete);
        });

        return workComplete;
    }
}
