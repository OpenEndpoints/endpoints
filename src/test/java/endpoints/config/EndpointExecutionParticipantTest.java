package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import junit.framework.TestCase;
import lombok.SneakyThrows;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.Set;

import static endpoints.config.EndpointExecutionParticipant.assertNoCircularDependencies;
import static java.util.Arrays.asList;

public class EndpointExecutionParticipantTest extends TestCase {
    
    protected static class Task extends EndpointExecutionParticipant {
        public @Nonnull Set<IntermediateValueName> outputs;
        public Task(@Nonnull Element element) throws ConfigurationException { super(element); }
        @Override public @Nonnull Set<IntermediateValueName> getOutputIntermediateValues() { return outputs; }
    }

    @SneakyThrows(ConfigurationException.class)
    protected Task newTask(@Nonnull Set<IntermediateValueName> inputs, @Nonnull Set<IntermediateValueName> outputs) {
        var xml = new StringBuilder();
        xml.append("<foo>");
        for (var x : inputs) xml.append("<input-intermediate-value name='").append(x.name).append("'/>");
        xml.append("</foo>");

        var element = DomParser.from(xml.toString());
        var result = new Task(element);
        result.outputs = outputs;

        return result;
    }

    public void testAssertNoCircularDependencies() throws Exception {
        var a = new IntermediateValueName("a");
        var b = new IntermediateValueName("b");
        var c = new IntermediateValueName("c");
        
        // No dependencies
        assertNoCircularDependencies(asList(newTask(Set.of(), Set.of()), newTask(Set.of(), Set.of())));
        
        // Missing dependency
        try {
            assertNoCircularDependencies(asList(newTask(Set.of(), Set.of()), newTask(Set.of(a), Set.of())));
            fail();
        }
        catch (ConfigurationException e) {
            assertTrue(e.getMessage().contains("No "));
        }

        // Produces unused dependency is OK
        assertNoCircularDependencies(asList(newTask(Set.of(), Set.of(a)), newTask(Set.of(), Set.of())));

        // Normal linear chain is OK
        // Ordering doesn't matter
        assertNoCircularDependencies(asList(
            newTask(Set.of(a), Set.of(b)),
            newTask(Set.of(), Set.of(a)),
            newTask(Set.of(b), Set.of(c))));

        // Multiple values are OK
        assertNoCircularDependencies(asList(
            newTask(Set.of(), Set.of(a, b, c)),
            newTask(Set.of(a), Set.of()),
            newTask(Set.of(a), Set.of()),
            newTask(Set.of(b), Set.of()),
            newTask(Set.of(c), Set.of())));

        // Multiple producers
        try {
            assertNoCircularDependencies(asList(
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
            assertNoCircularDependencies(asList(
                newTask(Set.of(a), Set.of(b)),
                newTask(Set.of(b), Set.of(a))));
            fail();
        }
        catch (ConfigurationException e) {
            assertTrue(e.getMessage().contains("Circular"));
        }
    }
}