package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.task.TaskId;
import junit.framework.TestCase;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;

import static endpoints.config.EndpointExecutionParticipant.assertNoCircularDependencies;

public class EndpointExecutionParticipantTest extends TestCase {
    
    protected static class Task extends EndpointExecutionParticipant {
        public @CheckForNull TaskId id;
        public @Nonnull Set<IntermediateValueName> outputs;
        @Override public @CheckForNull TaskId getTaskIdOrNull() { return id; }
        @Override protected @Nonnull String getHumanReadableId() { return id == null ? "unit test" : id.id; }
        public Task(@Nonnull Element element) throws ConfigurationException { super(element); }
        @Override public @Nonnull Set<IntermediateValueName> getOutputIntermediateValues() { return outputs; }
    }

    @SneakyThrows(ConfigurationException.class)
    protected @Nonnull Task newTask(
        @CheckForNull String id, @Nonnull Set<TaskId> before, 
        @Nonnull Set<IntermediateValueName> inputs, @Nonnull Set<IntermediateValueName> outputs
    ) {
        var xml = new StringBuilder();
        xml.append("<foo>");
        for (var x : before) xml.append("<after task-id='").append(x.id).append("'/>");
        for (var x : inputs) xml.append("<input-intermediate-value name='").append(x.name).append("'/>");
        xml.append("</foo>");

        var element = DomParser.from(xml.toString());
        var result = new Task(element);
        result.id = id == null ? null : new TaskId(id);
        result.outputs = outputs;

        return result;
    }

    protected @Nonnull Task newTask(@Nonnull Set<IntermediateValueName> inputs, @Nonnull Set<IntermediateValueName> outputs) {
        return newTask(null, Set.of(), inputs, outputs);        
    }

    public void testAssertNoCircularDependencies_after() throws Exception {
        var a = newTask("a", Set.of(), Set.of(), Set.of());
        var b = newTask(null, Set.of(new TaskId("a")), Set.of(), Set.of());
        
        // No circular dependencies
        assertNoCircularDependencies(List.of(a, b));
        
        // c -> d -> c
        var c = newTask("c", Set.of(new TaskId("d")), Set.of(), Set.of());
        var d = newTask("d", Set.of(new TaskId("c")), Set.of(), Set.of());
        try {
            assertNoCircularDependencies(List.of(c, d));
            fail();
        }
        catch (ConfigurationException e) {
            assertTrue(e.getMessage().contains("c has <after task-id='d'/>, d has <after task-id='c'/>"));
        }
    }

    public void testAssertNoCircularDependencies_variables() throws Exception {
        var a = new IntermediateValueName("a");
        var b = new IntermediateValueName("b");
        var c = new IntermediateValueName("c");
        
        // No dependencies
        assertNoCircularDependencies(List.of(newTask(Set.of(), Set.of()), newTask(Set.of(), Set.of())));
        
        // Missing dependency
        try {
            assertNoCircularDependencies(List.of(newTask(Set.of(), Set.of()), newTask(Set.of(a), Set.of())));
            fail();
        }
        catch (ConfigurationException e) {
            assertTrue(e.getMessage().contains("No "));
        }

        // Produces unused dependency is OK
        assertNoCircularDependencies(List.of(newTask(Set.of(), Set.of(a)), newTask(Set.of(), Set.of())));

        // Normal linear chain is OK
        // Ordering doesn't matter
        assertNoCircularDependencies(List.of(
            newTask(Set.of(a), Set.of(b)),
            newTask(Set.of(), Set.of(a)),
            newTask(Set.of(b), Set.of(c))));

        // Multiple values are OK
        assertNoCircularDependencies(List.of(
            newTask(Set.of(), Set.of(a, b, c)),
            newTask(Set.of(a), Set.of()),
            newTask(Set.of(a), Set.of()),
            newTask(Set.of(b), Set.of()),
            newTask(Set.of(c), Set.of())));

        // Multiple producers
        try {
            assertNoCircularDependencies(List.of(
                newTask(Set.of(), Set.of(a)),
                newTask(Set.of(), Set.of(a)),
                newTask(Set.of(a), Set.of())));
            fail();
        }
        catch (ConfigurationException e) {
            assertTrue(e.getMessage().contains("Multiple"));
        }

        // Circular
        try {
            assertNoCircularDependencies(List.of(
                newTask(Set.of(a), Set.of(b)),
                newTask(Set.of(b), Set.of(a))));
            fail();
        }
        catch (ConfigurationException e) {
            assertTrue(e.getMessage().contains("Circular"));
        }
    }
}