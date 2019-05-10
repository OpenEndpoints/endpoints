package endpoints;

import com.databasesandlife.util.ThreadPool;
import endpoints.config.ParameterName;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;

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

}
