package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.DeploymentParameters;
import software.amazon.awssdk.services.s3.model.GetBucketLocationRequest;

import javax.annotation.Nonnull;
import java.io.File;

import static com.databasesandlife.util.DomParser.*;

public record AwsS3Configuration(
    String bucket
) {
    public static @Nonnull AwsS3Configuration parse(@Nonnull File file)
    throws ConfigurationException {
        try {
            var root = from(file);

            if ( ! root.getNodeName().equals("aws-s3-configuration"))
                throw new ConfigurationException("Root node must be <aws-s3-configuration>");
            assertNoOtherElements(root, "bucket");

            var bucket = getMandatorySingleSubElementTextContent(root, "bucket");

            // Check that we can access the bucket
            try (var awsS3Client = DeploymentParameters.get().newAwsS3Client()) {
                awsS3Client.getBucketLocation(GetBucketLocationRequest.builder().bucket(bucket).build());
            }
            catch (Exception e) { throw new ConfigurationException("Bucket '" + bucket + "'", e); }

            return new AwsS3Configuration(bucket);
        }
        catch (Exception e) { throw new ConfigurationException(file.getAbsolutePath(), e); }
    }
}
