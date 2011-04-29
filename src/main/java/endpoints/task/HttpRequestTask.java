package endpoints.task;

import com.databasesandlife.util.ThreadPool;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.ApplicationTransaction;
import endpoints.EndpointExecutor;
import endpoints.EndpointExecutor.UploadedFile;
import endpoints.OnDemandIncrementingNumber;
import endpoints.OnDemandIncrementingNumber.OnDemandIncrementingNumberType;
import endpoints.config.HttpRequestSpecification;
import endpoints.config.HttpRequestSpecification.HttpRequestFailedException;
import endpoints.config.ParameterName;
import endpoints.config.Transformer;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Performs an HTTP request. See "configuration.md" for more information */
public class HttpRequestTask extends Task {
    
    protected @Nonnull HttpRequestSpecification spec;

    public HttpRequestTask(
        @Nonnull XsltCompilationThreads threads, @Nonnull File httpXsltDirectory, 
        @Nonnull Map<String, Transformer> transformers, @Nonnull File staticDir, @Nonnull Element config
    )
    throws ConfigurationException {
        super(threads, httpXsltDirectory, transformers, staticDir, config);
        spec = new HttpRequestSpecification(threads, httpXsltDirectory, config);
    }

    @Override
    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        super.assertParametersSuffice(params);
        spec.assertParametersSuffice(params);
    }
    
    @Override
    protected @Nonnull void scheduleTaskExecutionUnconditionally(
        @Nonnull ApplicationTransaction tx, @Nonnull ThreadPool threads,
        @Nonnull Map<ParameterName, String> parameters, @Nonnull List<? extends UploadedFile> fileUploads,
        @Nonnull Map<OnDemandIncrementingNumberType, OnDemandIncrementingNumber> autoInc
    ) {
        threads.addTask(() -> {
            try { spec.executeAndAssertNoError(parameters, fileUploads); }
            catch (HttpRequestFailedException e) { throw new RuntimeException(new TaskExecutionFailedException(e)); }
        });
    }
}
