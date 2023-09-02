package endpoints;

import com.databasesandlife.util.Timer;
import endpoints.config.ApplicationName;
import endpoints.config.NodeName;
import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Dimension;
import software.amazon.awssdk.services.cloudwatch.model.MetricDatum;
import software.amazon.awssdk.services.cloudwatch.model.PutMetricDataRequest;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/** Writes metrics to AWS CloudWatch */
@RequiredArgsConstructor
public class AwsCloudWatchRequestMetricWriter {

    protected final @Nonnull CloudWatchClient client;
    protected final @Nonnull String instance;
    
    public void scheduleWriteMetric(
        @Nonnull ApplicationName application, @Nonnull NodeName endpoint, @Nonnull PublishEnvironment environment,
        @Nonnull Instant now, int statusCode, @Nonnull Duration duration
    ) {
        new Thread(() -> {
            try (var ignored = new Timer("AwsCloudWatchRequestMetricWriter")) {
                MetricDatum datum = MetricDatum.builder()
                    .metricName("RequestDuration")
                    .dimensions(
                        Dimension.builder().name("Instance").value(instance).build(),
                        Dimension.builder().name("Application").value(application.name()).build(),
                        Dimension.builder().name("Endpoint").value(endpoint.getName()).build(),
                        Dimension.builder().name("Environment").value(environment.name()).build(),
                        Dimension.builder().name("StatusCode").value(String.valueOf(statusCode)).build()
                    )
                    .unit(StandardUnit.MILLISECONDS)
                    .value((double) duration.toMillis())
                    .timestamp(now)
                    .build();
    
                PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace("Endpoints")
                    .metricData(List.of(datum))
                    .build();
    
                client.putMetricData(request);
            }
        }).start();
    }
}
