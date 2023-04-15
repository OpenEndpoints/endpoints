package endpoints.task;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import junit.framework.TestCase;

import java.util.Map;

import static com.databasesandlife.util.DomParser.from;

public class TaskConditionTest extends TestCase {

    public void testEvaluate_equals_notequals() {
        var equalsBar = new TaskCondition(TaskCondition.Operator.equals, "${foo}", "bar");
        var notEqualsBar = new TaskCondition(TaskCondition.Operator.notequals, "${foo}", "bar");

        // Test single match equals
        assertTrue(equalsBar.evaluate("||", Map.of("foo", "bar")));
        assertFalse(equalsBar.evaluate("||", Map.of("foo", "DIFFERENT")));
        assertFalse(notEqualsBar.evaluate("||", Map.of("foo", "bar")));
        assertTrue(notEqualsBar.evaluate("||", Map.of("foo", "DIFFERENT")));

        // Test when variable is formed from an array
        assertTrue(equalsBar.evaluate("||", Map.of("foo", "bar||baz")));
        assertFalse(equalsBar.evaluate("||", Map.of("foo", "DIFFERENT")));
        assertFalse(notEqualsBar.evaluate("||", Map.of("foo", "bar||baz")));
        assertTrue(notEqualsBar.evaluate("||", Map.of("foo", "DIFFERENT")));
    }

    public void testEvaluate_isempty() {
        // isEmpty="true"
        var trueCondition = new TaskCondition(TaskCondition.Operator.isempty, "${foo}", "true");
        assertTrue(trueCondition.evaluate("||", Map.of("foo", "")));
        assertFalse(trueCondition.evaluate("||", Map.of("foo", "xyz")));

        // isEmpty="false"
        var falseCondition = new TaskCondition(TaskCondition.Operator.isempty, "${foo}", "false");
        assertFalse(falseCondition.evaluate("||", Map.of("foo", "")));
        assertTrue(falseCondition.evaluate("||", Map.of("foo", "xyz")));

        // isEmpty="something-else"
        try {
            new TaskCondition(from("<foo if='foo' isempty='neither-true-nor-false'/>"));
            fail();
        }
        catch (ConfigurationException ignored) { }
    }

    public void testEvaluate_hasmultiple() {
        // isMultiple="true"
        var trueCondition = new TaskCondition(TaskCondition.Operator.hasmultiple, "${foo}", "true");
        assertTrue(trueCondition.evaluate("||", Map.of("foo", "xyz||bar")));
        assertFalse(trueCondition.evaluate("||", Map.of("foo", "xyz")));

        // isMultiple="false"
        var falseCondition = new TaskCondition(TaskCondition.Operator.hasmultiple, "${foo}", "false");
        assertFalse(falseCondition.evaluate("||", Map.of("foo", "xyz||bar")));
        assertTrue(falseCondition.evaluate("||", Map.of("foo", "xyz")));

        // isMultiple="something-else"
        try {
            new TaskCondition(from("<foo if='foo' hasmultiple='neither-true-nor-false'/>"));
            fail();
        }
        catch (ConfigurationException ignored) { }
    }

    public void testEvaluate_numeric() {
        var condition = new TaskCondition(TaskCondition.Operator.gt, "${foo}", "12||34");
        assertTrue(condition.evaluate("||", Map.of("foo", "50||34.1"))); // All are greater
        assertFalse(condition.evaluate("||", Map.of("foo", "15||51"))); // Some are not greater
        assertFalse(condition.evaluate("||", Map.of("foo", "||51"))); // Some are empty
        assertFalse(condition.evaluate("||", Map.of("foo", "foo||51"))); // Some are not numbers
    }
}
