package endpoints.task;

import junit.framework.TestCase;

import java.util.Map;

public class TaskConditionTest extends TestCase {

    public void testEvaluate() {
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
}
