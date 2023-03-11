package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.ThreadPool;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.RequestId;
import junit.framework.TestCase;

import java.util.Map;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;

public class WriteTransformationDataToAwsS3CommandTest extends TestCase {

    @Override protected void setUp() throws Exception {
        super.setUp();

        if (DeploymentParameters.get().awsS3EndpointOverride == null)
            throw new RuntimeException("AWS env var should be configured to talk to local S3 emulation");
    }

    public void testScheduleWriting() throws Exception {
        var bucketName = UUID.randomUUID().toString();
        try (var client = DeploymentParameters.get().newAwsS3Client()) {
            client.createBucket(b -> b.bucket(bucketName));
        }

        var config = """
            <write-input-to-aws-s3>
               <folder>my-folder</folder>
               <tag name="my-tag">my-tag-value</tag>
            </write-input-to-aws-s3>
            """;
        var command = new WriteTransformationDataToAwsS3Command(DomParser.from(config));
        var requestId = RequestId.newRandom();
        var threads = new ThreadPool();
        command.scheduleWriting(threads, PublishEnvironment.live, requestId, new AwsS3Configuration(bucketName), 
            "my-data-source", "application/text", "My text".getBytes(UTF_8));
        threads.execute();
        
        try (var s3 = DeploymentParameters.get().newAwsS3Client()) {
            var objectList = s3.listObjectsV2(req -> req.bucket(bucketName));
            assertEquals(1, (int) objectList.keyCount());
            // <request-id>-<data-source-name>-transformer-input.xml
            assertEquals("my-folder/"+requestId.id()+"-my-data-source-transformer-input", objectList.contents().get(0).key());
            
            var object = s3.getObject(req -> req
                .bucket(bucketName)
                .key(objectList.contents().get(0).key()));
            assertEquals("application/text", object.response().contentType());
            assertEquals("My text", new String(object.readAllBytes(), UTF_8));
            
            var tagList = s3.getObjectTagging(req -> req
                .bucket(bucketName)
                .key(objectList.contents().get(0).key()));
            var tags = tagList.tagSet().stream().collect(toMap(t -> t.key(), t -> t.value()));
            assertEquals(Map.of("environment", "live", "my-tag", "my-tag-value"), tags);
        }
    }
}
