package endpoints.condition;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.LazyCachingValue;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import lombok.RequiredArgsConstructor;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A set of condition folders joined by OR */
@RequiredArgsConstructor
public class ConditionFolderList {
    
    protected final @Nonnull List<ConditionFolder> conditions;
    
    public ConditionFolderList(@Nonnull Element element) throws ConfigurationException {
        this.conditions = DomParser.parseList(element, "condition-folder", ConditionFolder::new);
    }

    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params, @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        for (var c : conditions) c.assertParametersSuffice(params, visibleIntermediateValues);
    }

    public boolean evaluate(@Nonnull String parameterMultipleValueSeparator, @Nonnull Map<String, LazyCachingValue> parameters) {
        if (conditions.isEmpty()) return true; // No conditions = OK
        return conditions.stream().anyMatch(c -> c.evaluate(parameterMultipleValueSeparator, parameters));
    }
}
