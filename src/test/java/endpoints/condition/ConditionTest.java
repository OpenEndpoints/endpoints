package endpoints.condition;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.condition.Condition;
import junit.framework.TestCase;

import java.util.Map;

import static com.databasesandlife.util.DomParser.from;
import static endpoints.LazyCachingValue.newFixed;

public class ConditionTest extends TestCase {

    public void testEvaluate_equals_notequals() {
        var equalsBar = new Condition(Condition.Operator.equals, "${foo}", "bar");
        var notEqualsBar = new Condition(Condition.Operator.notequals, "${foo}", "bar");

        // Test single match equals
        assertTrue(equalsBar.evaluate("||", Map.of("foo", newFixed("bar"))));
        assertFalse(equalsBar.evaluate("||", Map.of("foo", newFixed("DIFFERENT"))));
        assertFalse(notEqualsBar.evaluate("||", Map.of("foo", newFixed("bar"))));
        assertTrue(notEqualsBar.evaluate("||", Map.of("foo", newFixed("DIFFERENT"))));

        // Test when variable is formed from an array
        assertTrue(equalsBar.evaluate("||", Map.of("foo", newFixed("bar||baz"))));
        assertFalse(equalsBar.evaluate("||", Map.of("foo", newFixed("DIFFERENT"))));
        assertFalse(notEqualsBar.evaluate("||", Map.of("foo", newFixed("bar||baz"))));
        assertTrue(notEqualsBar.evaluate("||", Map.of("foo", newFixed("DIFFERENT"))));
    }

    public void testEvaluate_isempty() {
        // isEmpty="true"
        var trueCondition = new Condition(Condition.Operator.isempty, "${foo}", "true");
        assertTrue(trueCondition.evaluate("||", Map.of("foo", newFixed(""))));
        assertFalse(trueCondition.evaluate("||", Map.of("foo", newFixed("xyz"))));

        // isEmpty="false"
        var falseCondition = new Condition(Condition.Operator.isempty, "${foo}", "false");
        assertFalse(falseCondition.evaluate("||", Map.of("foo", newFixed(""))));
        assertTrue(falseCondition.evaluate("||", Map.of("foo", newFixed("xyz"))));

        // isEmpty="something-else"
        try {
            new Condition(from("<foo if='foo' isempty='neither-true-nor-false'/>"));
            fail();
        }
        catch (ConfigurationException ignored) { }
    }

    public void testEvaluate_hasmultiple() {
        // isMultiple="true"
        var trueCondition = new Condition(Condition.Operator.hasmultiple, "${foo}", "true");
        assertTrue(trueCondition.evaluate("||", Map.of("foo", newFixed("xyz||bar"))));
        assertFalse(trueCondition.evaluate("||", Map.of("foo", newFixed("xyz"))));

        // isMultiple="false"
        var falseCondition = new Condition(Condition.Operator.hasmultiple, "${foo}", "false");
        assertFalse(falseCondition.evaluate("||", Map.of("foo", newFixed("xyz||bar"))));
        assertTrue(falseCondition.evaluate("||", Map.of("foo", newFixed("xyz"))));

        // isMultiple="something-else"
        try {
            new Condition(from("<foo if='foo' hasmultiple='neither-true-nor-false'/>"));
            fail();
        }
        catch (ConfigurationException ignored) { }
    }

    public void testEvaluate_numeric() {
        var condition = new Condition(Condition.Operator.gt, "${foo}", "12||34");
        assertTrue(condition.evaluate("||", Map.of("foo", newFixed("50||34.1")))); // All are greater
        assertFalse(condition.evaluate("||", Map.of("foo", newFixed("15||51")))); // Some are not greater
        assertFalse(condition.evaluate("||", Map.of("foo", newFixed("||51")))); // Some are empty
        assertFalse(condition.evaluate("||", Map.of("foo", newFixed("foo||51")))); // Some are not numbers
    }
}
