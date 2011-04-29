package endpoints.task;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.PlaintextParameterReplacer;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

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
    
    public void assertParametersSuffice(@Nonnull Set<ParameterName> params) throws ConfigurationException {
        PlaintextParameterReplacer.assertParametersSuffice(params, lhsPattern, "'if' attribute");
        PlaintextParameterReplacer.assertParametersSuffice(params, rhsPattern, "'"+operator+"' attribute");
    }
    
    public boolean evaluate(@Nonnull Map<ParameterName, String> parameters) {
        var lhs = PlaintextParameterReplacer.replacePlainTextParameters(lhsPattern, parameters);
        var rhs = PlaintextParameterReplacer.replacePlainTextParameters(rhsPattern, parameters);
        switch (operator) {
            case equals: return lhs.equals(rhs);
            case notequals: return ! lhs.equals(rhs);
            default: throw new RuntimeException("Unexpected operator: " + operator);
        }
    }
}
