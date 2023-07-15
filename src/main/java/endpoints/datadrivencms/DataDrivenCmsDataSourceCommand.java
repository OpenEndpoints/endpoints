package endpoints.datadrivencms;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import com.offerready.xslt.WeaklyCachedXsltTransformer.XsltCompilationThreads;
import endpoints.TransformationContext;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import endpoints.datasource.DataSourceCommand;
import endpoints.datasource.DataSourceCommandFetcher;
import lombok.SneakyThrows;
import org.apache.commons.collections4.iterators.IteratorIterable;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static endpoints.config.ApplicationFactory.dataDrivenCmsDir;

public class DataDrivenCmsDataSourceCommand extends DataSourceCommand {
    
    protected final List<DataDrivenCmsFile> files;

    @SneakyThrows(IOException.class)
    public DataDrivenCmsDataSourceCommand(
        @Nonnull XsltCompilationThreads threads, @Nonnull File applicationDir, @Nonnull Element config
    ) throws ConfigurationException {
        super(threads, applicationDir, config);
        
        this.files = new ArrayList<>();
        try (var stream = Files.walk(new File(applicationDir, dataDrivenCmsDir).toPath())) {
            for (var file : new IteratorIterable<>(stream.iterator())) {
                if ( ! file.toString().toLowerCase().endsWith(".xml")) continue;
                try { this.files.add(new DataDrivenCmsFile(DomParser.from(file.toFile()))); }
                catch (ConfigurationException e) { throw new ConfigurationException(file.toString(), e); }
            }
        }
    }

    @Override public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params, @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        for (var f : files)
            f.assertParametersSuffice(params, visibleIntermediateValues);
    }

    @Override
    public @Nonnull DataSourceCommandFetcher scheduleFetch(
        @Nonnull TransformationContext context,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        var result = new DataSourceCommandFetcher() {
            @Override protected @Nonnull Element[] populateOrThrow() {
                var stringParams = context.getParametersAndIntermediateValuesAndSecrets(visibleIntermediateValues);
                var sep = context.endpoint.getParameterMultipleValueSeparator();
                return new Element[] { DataDrivenCmsFile.createDataSourceOutput(sep, stringParams, files) };
            }
        };
        context.threads.addTask(result);
        return result;
    }
}
