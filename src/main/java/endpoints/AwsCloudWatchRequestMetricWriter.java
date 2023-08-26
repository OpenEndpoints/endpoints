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

    protected static final String metricNamepace = "Endpoints";
    protected static final String metricName = "RequestDuration";
    
    protected final CloudWatchClient client;
    
    public void scheduleWriteMetric(
        @Nonnull ApplicationName application, @Nonnull NodeName endpoint, @Nonnull PublishEnvironment environment,
        @Nonnull Instant now, int statusCode, @Nonnull Duration duration
    ) {
        new Thread(() -> {
            try (var ignored = new Timer("AwsCloudWatchRequestMetricWriter")) {
                MetricDatum datum = MetricDatum.builder()
                    .metricName(metricName)
                    .dimensions(
                        Dimension.builder().name("application").value(application.name()).build(),
                        Dimension.builder().name("endpoint").value(endpoint.getName()).build(),
                        Dimension.builder().name("environment").value(environment.name()).build()
                    )
                    .unit(StandardUnit.MILLISECONDS)
                    .value((double) duration.toMillis())
                    .timestamp(now)
                    .build();
    
                PutMetricDataRequest request = PutMetricDataRequest.builder()
                    .namespace(metricNamepace)
                    .metricData(List.of(datum))
                    .build();
    
                client.putMetricData(request);
            }
        }).start();
    }
}
