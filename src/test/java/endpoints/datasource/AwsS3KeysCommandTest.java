package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.offerready.xslt.WeaklyCachedXsltTransformer;
import endpoints.DeploymentParameters;
import endpoints.config.AwsS3Configuration;
import junit.framework.TestCase;
import org.w3c.dom.Element;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

import javax.annotation.Nonnull;
import java.io.File;
import java.util.UUID;

public class AwsS3KeysCommandTest extends TestCase {
    
    @Override protected void setUp() throws Exception {
        super.setUp();
        
        if (DeploymentParameters.get().awsS3EndpointOverride == null)
            throw new RuntimeException("AWS env var should be configured to talk to local S3 emulation");
    }

    protected void putObject(String bucketName, String filename, String tag) {
        try (var client = DeploymentParameters.get().newAwsS3Client()) {
            client.putObject(req -> req
                    .bucket(bucketName)
                    .key(filename)
                    .tagging(Tagging.builder().tagSet(Tag.builder().key("tag").value(tag).build()).build()),
                RequestBody.fromString("contents"));
        }
    }
    
    protected boolean checkResultContains(@Nonnull Element element, @Nonnull String filenamePattern) {
        for (var e : DomParser.toElementList(element.getChildNodes())) {
            if (e.getAttribute("key").matches(filenamePattern)) return true;
        }
        return false;
    }

    public void testExecute() throws Exception {
        var bucketName = UUID.randomUUID().toString();
        try (var client = DeploymentParameters.get().newAwsS3Client()) {
            client.createBucket(b -> b.bucket(bucketName));
        }

        // Upload objects
        //   Folder/Object A  [tag=right]   (most recent)
        //   Wrong Folder/Foo
        //   Folder/Object B  [tag=right]
        //   Folder/Object C  [tag=wrong]
        //   Folder/Object D
        //    -- cut off --
        //   Folder/Object E                  (oldest)

        putObject(bucketName, "Folder/ObjectE", "right-tag");
        putObject(bucketName, "Folder/ObjectD", "right-tag");
        putObject(bucketName, "Folder/ObjectC", "wrong-tag");
        putObject(bucketName, "Folder/ObjectB", "right-tag");
        putObject(bucketName, "Wrong Folder/Foo", "right-tag");
        putObject(bucketName, "Folder/ObjectA", "right-tag");

        var config = """
            <aws-s3-keys limit="3">
              <folder>Folder</folder>
              <match-tag name="tag">right-tag</match-tag>
            </aws-s3-keys>
            """;
        var command = new AwsS3KeysCommand(new WeaklyCachedXsltTransformer.XsltCompilationThreads(), new File("/"),
            DomParser.from(config));
        var result = command.execute(new AwsS3Configuration(bucketName));

        assertTrue(checkResultContains(result, "ObjectA"));
        assertFalse(checkResultContains(result, ".*Wrong.*"));
        assertTrue(checkResultContains(result, "ObjectB"));
        assertFalse(checkResultContains(result, ".*ObjectC.*"));
        assertTrue(checkResultContains(result, "ObjectD"));
        assertFalse(checkResultContains(result, ".*ObjectE.*"));
    }
}
