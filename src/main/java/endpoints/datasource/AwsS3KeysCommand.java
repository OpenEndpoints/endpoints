package endpoints.datasource;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.DeploymentParameters;
import endpoints.TransformationContext;
import endpoints.config.AwsS3Configuration;
import endpoints.config.IntermediateValueName;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.File;
import java.time.Instant;
import java.util.*;

import static com.databasesandlife.util.DomParser.*;
import static java.util.stream.Collectors.toMap;

public class AwsS3KeysCommand extends DataSourceCommand {
    
    protected final @CheckForNull String folderOrNull;
    protected final @Nonnull Map<String, String> matchTag;
    protected final int limit;
    
    public AwsS3KeysCommand(
        @Nonnull XsltCompilationThreads threads,
        @Nonnull File applicationDir, @Nonnull File httpXsltDirectory, @Nonnull File xmlFromApplicationDir,
        @Nonnull File dataSourcePostProcessingXsltDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, applicationDir, httpXsltDirectory, xmlFromApplicationDir, dataSourcePostProcessingXsltDir, config);
        
        assertNoOtherElements(config, "folder", "match-tag");
        folderOrNull = getOptionalSingleSubElementTextContent(config, "folder");
        matchTag = parseMap(config, "match-tag", "name");
        limit = parseMandatoryIntAttribute(config, "limit");
    }

    @Override
    public boolean requiresAwsS3Configuration() { 
        return true; 
    }
    
    protected @Nonnull Element newObjectElement(@Nonnull Document resultDocument, @Nonnull String key) {
        var element = resultDocument.createElement("object");
        element.setAttribute("key", key);
        return element;
    }
    
    protected @Nonnull Element execute(@Nonnull AwsS3Configuration s3) {
        var resultDocument = DomParser.newDocumentBuilder().newDocument();
        var rootElement = resultDocument.createElement("aws-s3-keys");
        resultDocument.appendChild(rootElement);

        var result = new TreeMap<Instant, List<Element>>();

        try (var client = DeploymentParameters.get().newAwsS3Client()) {
            var folder = Optional.ofNullable(folderOrNull).map(f -> f+"/");
            var pages = client.listObjectsV2Paginator(request -> request
                .bucket(s3.bucket())
                .prefix(folder.orElse(null)));
            for (var page : pages) {
                objects: for (var s3Object : page.contents()) {
                    var tagList = client.getObjectTagging(request -> request
                        .bucket(s3.bucket())
                        .key(s3Object.key()));
                    var tags = tagList.tagSet().stream().collect(toMap(t -> t.key(), t -> t.value()));
                    for (var requestedTag : matchTag.entrySet())
                        if ( ! tags.getOrDefault(requestedTag.getKey(), "").equals(requestedTag.getValue()))
                            continue objects;

                    result
                        .computeIfAbsent(s3Object.lastModified(), key -> new ArrayList<>())
                        .add(newObjectElement(resultDocument, s3Object.key().replace(folder.orElse(""), "")));
                }
            }
        }
        
        objects: for (var time : result.descendingMap().entrySet()) {
            for (var object : time.getValue()) {
                if (rootElement.getChildNodes().getLength() >= limit) 
                    break objects;
                rootElement.appendChild(object);
            }
        }
        
        return rootElement;
    }
  
    @Override public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context, @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws TransformationFailedException {
        var result = new DataSourceCommandFetcher() {
            @Override protected Element[] populateOrThrow() {
                AwsS3Configuration s3 = context.application.getAwsS3ConfigurationOrNull();
                assert s3 != null; // checked on application load
                return new Element[] { execute(s3) };
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
