package endpoints;

import com.databasesandlife.util.ThreadPool;
import com.offerready.xslt.BufferedDocumentGenerationDestination;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import endpoints.datasource.TransformationFailedException;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import static java.util.Collections.singletonList;

/**
 * Represents certain things that the execution of transformations and data sources need.
 *   <p>
 * There are (at the time of writing) three times in the endpoints's execution where one is created:
 * During parameter transformation, during success flow, and during error flow.
 */
@RequiredArgsConstructor
public class TransformationContext {

    public final @Nonnull ApplicationTransaction tx;
    public final @Nonnull ThreadPool threads = new ThreadPool();
    public final @Nonnull Map<ParameterName, String> params;
    public final @Nonnull List<? extends EndpointExecutor.UploadedFile> fileUploads;
    public final @Nonnull Map<OnDemandIncrementingNumber.OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc;
    
    public static class TransformerExecutor implements Runnable {
        public final @Nonnull BufferedDocumentGenerationDestination result = new BufferedDocumentGenerationDestination();
        @Override public void run() { }
    }
    
    public synchronized @Nonnull TransformerExecutor getOrScheduleTransformation(@Nonnull Transformer transformer) 
    throws TransformationFailedException {
        var result = new TransformerExecutor();
        var process = transformer.scheduleExecution(this, result.result);
        threads.addTaskWithDependencies(singletonList(process), result);
        return result;
    }

}
