package endpoints.condition;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.LazyCachingValue;
import endpoints.PlaintextParameterReplacer;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import lombok.AllArgsConstructor;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static endpoints.PlaintextParameterReplacer.replacePlainTextParameters;
import static java.util.Arrays.stream;

/** Represents the <code>if="${foo}" equals="xyz"</code> conditions that tasks may have */
@AllArgsConstructor // For testing
public class Condition {
    
    public enum Operator { equals, notequals, isempty, hasmultiple, gt, ge, lt, le }
    
    protected final @Nonnull Operator operator;
    protected final @Nonnull String lhsPattern, rhsPattern;
    
    public Condition(@Nonnull Element element) throws ConfigurationException {
        if (element.hasAttribute("if")) {
            this.lhsPattern = element.getAttribute("if");
            for (var op : Operator.values()) {
                if (element.hasAttribute(op.name())) {
                    this.operator = op;
                    this.rhsPattern = element.getAttribute(op.name());
                    if (op == Operator.isempty || op == Operator.hasmultiple) 
                        if ( ! Set.of("true", "false").contains(this.rhsPattern))
                            throw new ConfigurationException("Condition "+op+" must be "+op+"='true' or "+op+"='false', " +
                                "not "+op+"='"+this.rhsPattern+"'");
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
    
    protected boolean evaluateNumerical(
        @Nonnull String[] lhs, @Nonnull String[] rhs,
        @Nonnull BiFunction<BigDecimal, BigDecimal, Boolean> matches
    ) {
        for (var lString : lhs) {
            for (var rString : rhs) {
                try {
                    var lNumber = new BigDecimal(lString);
                    var rNumber = new BigDecimal(rString);
                    if ( ! matches.apply(lNumber, rNumber)) return false;
                }
                catch (NumberFormatException ignored) { 
                    return false;
                }
            }
        }
        return true;
    }
    
    public boolean evaluate(@Nonnull String parameterMultipleValueSeparator, @Nonnull Map<String, LazyCachingValue> parameters) {
        var lhs = replacePlainTextParameters(lhsPattern, parameters).split(Pattern.quote(parameterMultipleValueSeparator));
        var rhs = replacePlainTextParameters(rhsPattern, parameters).split(Pattern.quote(parameterMultipleValueSeparator));
        return switch (operator) {
            case equals -> stream(lhs).anyMatch(x -> Arrays.asList(rhs).contains(x));
            case notequals -> stream(lhs).noneMatch(x -> Arrays.asList(rhs).contains(x));
            case isempty -> rhsPattern.equals("true") == stream(lhs).allMatch(x -> x.isEmpty());
            case hasmultiple -> rhsPattern.equals("true") == lhs.length > 1;
            case gt -> evaluateNumerical(lhs, rhs, (a,b) -> a.compareTo(b) >  0);
            case ge -> evaluateNumerical(lhs, rhs, (a,b) -> a.compareTo(b) >= 0);
            case lt -> evaluateNumerical(lhs, rhs, (a,b) -> a.compareTo(b) <  0);
            case le -> evaluateNumerical(lhs, rhs, (a,b) -> a.compareTo(b) <= 0);
        };
    }
}
