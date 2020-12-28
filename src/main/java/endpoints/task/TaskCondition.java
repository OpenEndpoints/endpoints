package endpoints.task;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.PlaintextParameterReplacer;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.databasesandlife.util.PlaintextParameterReplacer.replacePlainTextParameters;
import static java.util.Arrays.stream;

/** Represents the <code>if="${foo}" equals="xyz"</code> conditions that tasks may have */
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
    
    public boolean evaluate(@Nonnull Map<String, String> parameters) {
        var lhs = replacePlainTextParameters(lhsPattern, parameters);
        var rhs = replacePlainTextParameters(rhsPattern, parameters);
        switch (operator) {
            case equals: return lhs.equals(rhs);
            case notequals: return ! lhs.equals(rhs);
            default: throw new RuntimeException("Unexpected operator: " + operator);
        }
    }
}
