package endpoints.condition;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.LazyCachingValue;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import lombok.RequiredArgsConstructor;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A set of conditions joined by AND */
@RequiredArgsConstructor
public class ConditionFolder {
    
    protected final @Nonnull List<Condition> conditions;
    
    public ConditionFolder(@Nonnull Element element) throws ConfigurationException {
        conditions = new ArrayList<>();
        var conditionElements = DomParser.getSubElements(element, "condition");
        for (int i = 0; i < conditionElements.size(); i++) {
            var el = conditionElements.get(i);
            try { conditions.add(new Condition(el)); }
            catch (ConfigurationException e) { throw new ConfigurationException("<condition> (0-based) index " + i, e); }
        }
    }

    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params, @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        for (var c : conditions) c.assertParametersSuffice(params, visibleIntermediateValues);
    }

    public boolean evaluate(@Nonnull String parameterMultipleValueSeparator, @Nonnull Map<String, LazyCachingValue> parameters) {
        return conditions.stream().allMatch(c -> c.evaluate(parameterMultipleValueSeparator, parameters));
    }
}
