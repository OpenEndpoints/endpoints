package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.ThreadPool;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.DeploymentParameters;
import endpoints.PublishEnvironment;
import endpoints.RequestId;
import org.w3c.dom.Element;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.Tag;
import software.amazon.awssdk.services.s3.model.Tagging;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;

import static com.databasesandlife.util.DomParser.getOptionalSingleSubElementTextContent;
import static com.databasesandlife.util.DomParser.parseMap;

/**
 * Writes either the output or the input of a transformation to AWS S3.
 */
public class WriteTransformationDataToAwsS3Command {

    protected enum InputOrOutput { input, output }
    
    protected final @Nonnull InputOrOutput inputOrOutput;
    protected final @Nonnull String folder;
    protected final @Nonnull Map<String, String> tags;
    
    public WriteTransformationDataToAwsS3Command(@Nonnull Element config) throws ConfigurationException {
        inputOrOutput = switch (config.getNodeName()) {
            case "write-input-to-aws-s3" -> InputOrOutput.input;
            case "write-output-to-aws-s3" -> InputOrOutput.output;
            default -> throw new ConfigurationException("Unexpected element name <" + config.getNodeName() + ">");
        };
        
        DomParser.assertNoOtherElements(config, "folder", "tag");
        folder = Optional.ofNullable(getOptionalSingleSubElementTextContent(config, "folder"))
            .map(f -> f.endsWith("/") ? f : f+"/")
            .orElse("");
        tags = parseMap(config, "tag", "name");
    }
    
    public void scheduleWriting(
        @Nonnull ThreadPool threads, @Nonnull PublishEnvironment environment, @Nonnull RequestId requestId,
        @Nonnull AwsS3Configuration config, @Nonnull String dataSourceName, 
        @Nonnull String contentType, @Nonnull byte[] contents
    ) {
        threads.addTaskOffPool(() -> {
            try (var s3 = DeploymentParameters.get().newAwsS3Client()) {
                var extension = contentType.contains("xml") ? ".xml" : "";

                var tagList = new ArrayList<Tag>();
                tagList.add(Tag.builder().key("environment").value(environment.name()).build());
                tags.forEach((key, value) -> tagList.add(Tag.builder().key(key).value(value).build()));

                s3.putObject(req -> req
                    .bucket(config.bucket())
                    .key(String.format("%s%s-%s-transformer-%s%s", 
                        folder, requestId.id(), dataSourceName, inputOrOutput, extension))
                    .contentType(contentType)
                    .tagging(Tagging.builder().tagSet(tagList).build()),
                    RequestBody.fromBytes(contents));
            }
        });
    }
}
