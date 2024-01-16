package endpoints.datadrivencms;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.LazyCachingValue;
import endpoints.condition.ConditionFolder;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.*;
import java.util.function.Function;

import static java.util.stream.Collectors.*;

public class DataDrivenCmsFile {
    
    protected final ConditionFolder globalConditions;
    protected final List<DataDrivenCmsContentInstance> contents;
    protected final List<DataDrivenCmsPropertyInstance> properties;
    
    protected interface InstanceFactory<T extends DataDrivenCmsInstance> {
        @Nonnull T newInstance(@Nonnull String id, @Nonnull Element element) throws ConfigurationException;
    }
    
    protected <T extends DataDrivenCmsInstance> List<T> parseInstances(
        @Nonnull Element config, @Nonnull String elementName, @Nonnull InstanceFactory<T> instanceFactory
    ) throws ConfigurationException {
        var result = new ArrayList<T>();
        for (var element : DomParser.getSubElements(config, elementName)) {
            DomParser.assertNoOtherElements(element, "instance");
            var id = DomParser.getMandatoryAttribute(element, "id");
            try { result.addAll(DomParser.parseList(element, "instance", e -> instanceFactory.newInstance(id, e))); }
            catch (ConfigurationException e) { throw new ConfigurationException("<content id='" + id + "'>", e); }
        }
        return result;
    }
    
    public DataDrivenCmsFile(@Nonnull Element config) throws ConfigurationException {
        DomParser.assertNoOtherElements(config, "condition", "content", "property");
        this.globalConditions = new ConditionFolder(config);
        this.contents = parseInstances(config, "content", DataDrivenCmsContentInstance::new);
        this.properties = parseInstances(config, "property", DataDrivenCmsPropertyInstance::new);
    }

    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params, @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        globalConditions.assertParametersSuffice(params, visibleIntermediateValues);
        for (var c : contents) c.assertParametersSuffice(params, visibleIntermediateValues);
        for (var p : properties) p.assertParametersSuffice(params, visibleIntermediateValues);
    }

    protected static <T extends DataDrivenCmsInstance>
    @Nonnull Map<String, TreeMap<Integer, List<T>>> newIdToPriorityToContent(
        @Nonnull String parameterMultipleValueSeparator, @Nonnull Map<String, LazyCachingValue> params,
        @Nonnull List<DataDrivenCmsFile> files, @Nonnull Function<DataDrivenCmsFile, List<T>> itemsExtractor
    ) {
        return files.stream()
            .filter(file -> file.globalConditions.evaluate(parameterMultipleValueSeparator, params))
            .flatMap(file -> itemsExtractor.apply(file).stream()) // stream of instances
            .collect(groupingBy(c -> c.contentId)) // Map<content id, List<instance>>
            .entrySet().stream()
            .collect(toMap(e -> e.getKey(), e -> e.getValue().stream()
                .filter(c -> c.conditionFolders.evaluate(parameterMultipleValueSeparator, params))
                .collect(groupingBy(c -> c.priority, TreeMap::new, toList())),
                (a,b) -> a, // cannot happen due to previous groupingBy
                TreeMap::new // this makes the output order predictable which is useful for unit tests
            ));
    }
    
    public static @Nonnull Element createDataSourceOutput(
        @Nonnull String parameterMultipleValueSeparator, @Nonnull Map<String, LazyCachingValue> params,
        @Nonnull List<DataDrivenCmsFile> files
    ) {
        var document = DomParser.newDocumentBuilder().newDocument();
        var root = document.createElement("data-driven-cms");

        var idToPriorityToContent = newIdToPriorityToContent(parameterMultipleValueSeparator, params, files, f -> f.contents);
        for (var e : idToPriorityToContent.entrySet()) {
            var contentElement = document.createElement("content");
            contentElement.setAttribute("id", e.getKey());
            root.appendChild(contentElement);

            for (var contentList : e.getValue().descendingMap().entrySet()) {
                for (var content : contentList.getValue()) {
                    var instanceElement = document.createElement("instance");
                    contentElement.appendChild(instanceElement);

                    for (var copyElement : DomParser.getSubElements(content.copy, "*"))
                        instanceElement.appendChild(document.importNode(copyElement, true));
                }
            }
        }
        
        var idToPriorityToProperty = newIdToPriorityToContent(parameterMultipleValueSeparator, params, files, f -> f.properties);
        for (var e : idToPriorityToProperty.entrySet()) {
            if (e.getValue().isEmpty()) continue; // e.g. no conditions match, so no items
            var property = e.getValue().descendingMap().firstEntry(); // from priority to object, ordered by priority desc

            var propertyElement = document.createElement("property");
            propertyElement.setAttribute("id", e.getKey());
            propertyElement.setAttribute("value", property.getValue().get(0).value);
            root.appendChild(propertyElement);
        }
        
        return root;
    }
}
