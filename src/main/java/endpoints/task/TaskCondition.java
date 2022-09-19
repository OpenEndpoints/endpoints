package endpoints.task;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.PlaintextParameterReplacer;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import lombok.AllArgsConstructor;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;
import static java.util.Arrays.stream;

/** Represents the <code>if="${foo}" equals="xyz"</code> conditions that tasks may have */
@AllArgsConstructor // For testing
public class TaskCondition {
    
    public enum Operator { equals, notequals }
    
    protected final @Nonnull Operator operator;
    protected final @Nonnull String lhsPattern, rhsPattern;
    
    public TaskCondition(@Nonnull Element element) throws ConfigurationException {
        if (element.hasAttribute("if")) {
            this.lhsPattern = element.getAttribute("if");
            for (var op : Operator.values()) {
                if (element.hasAttribute(op.name())) {
                    this.operator = op;
                    this.rhsPattern = element.getAttribute(op.name());
                    return;
                }
            }
            throw new ConfigurationException("'if' attribute found, but second attribute not found, expected one of: " + 
                stream(Operator.values()).map(x -> "'"+x+"'").collect(Collectors.joining(", ")));
        } else {
            this.operator = Operator.equals;
            this.lhsPattern = "";
            this.rhsPattern = "";
        }
    }
    
    public boolean isOptional() {
        return ! (operator == Operator.equals && lhsPattern.isEmpty() && rhsPattern.isEmpty());
    }
    
    public String getDescriptionForDebugging() {
        if (isOptional()) return " if='" + lhsPattern + "' " + operator.name() + "='" + rhsPattern + "'";
        else return "";
    }

    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params,
        @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues, lhsPattern, "'if' attribute");
        PlaintextParameterReplacer.assertParametersSuffice(params, visibleIntermediateValues, rhsPattern, "'"+operator+"' attribute");
    }
    
    public boolean evaluate(@Nonnull String parameterMultipleValueSeparator, @Nonnull Map<String, String> parameters) {
        var lhs = replacePlainTextParameters(lhsPattern, parameters).split(Pattern.quote(parameterMultipleValueSeparator));
        var rhs = replacePlainTextParameters(rhsPattern, parameters).split(Pattern.quote(parameterMultipleValueSeparator));
        switch (operator) {
            case equals: return stream(lhs).anyMatch(x -> Arrays.asList(rhs).contains(x));
            case notequals: return stream(lhs).noneMatch(x -> Arrays.asList(rhs).contains(x));
            default: throw new RuntimeException("Unexpected operator: " + operator);
        }
    }
}
