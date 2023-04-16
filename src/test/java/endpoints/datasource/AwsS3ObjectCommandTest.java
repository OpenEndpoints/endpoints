package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import endpoints.DeploymentParameters;
import endpoints.config.AwsS3Configuration;
import junit.framework.TestCase;
import software.amazon.awssdk.core.sync.RequestBody;

import java.io.File;
import java.util.UUID;

public class AwsS3ObjectCommandTest extends TestCase {
    
    @Override protected void setUp() throws Exception {
        super.setUp();
        
        if (DeploymentParameters.get().awsS3EndpointOverride == null)
            throw new RuntimeException("AWS env var should be configured to talk to local S3 emulation");
    }

    public void testExecute() throws Exception {
        var bucketName = UUID.randomUUID().toString();
        
        try (var client = DeploymentParameters.get().newAwsS3Client()) {
            client.createBucket(b -> b.bucket(bucketName));
            
            client.putObject(req -> req
                    .bucket(bucketName)
                    .key("file.xml"),
                RequestBody.fromString("<foo/>"));
        }
        
        var config = "<aws-s3-object key='file.xml'/>";
        var command = new AwsS3ObjectCommand(new WeaklyCachedXsltTransformer.XsltCompilationThreads(), new File("/"),
            DomParser.from(config));
        var result = command.execute(new AwsS3Configuration(bucketName));
        var formatted = DomParser.formatXmlPretty(result);
        var expected = """
            <aws-s3-object key="file.xml">
               <foo/>
            </aws-s3-object>""";
        assertEquals(expected, formatted);
    }
}
