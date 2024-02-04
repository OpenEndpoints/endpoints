package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.task.TaskId;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Either a Task or &lt;success&gt; or &lt;error&gt;.
 * These can either be run in parallel or can have dependencies, either via intermediate variable or &lt;after&gt;.
 */
public abstract class EndpointExecutionParticipant {

    public final @Nonnull Set<TaskId> predecessors;
    public final @Nonnull Set<IntermediateValueName> inputIntermediateValues;
    
    /** 
     * Not all tasks have IDs and &lt;success&gt; never has an ID. 
     * If it doesn't have an ID, it cannot be referenced in an &lt;after&gt;.
     */
    public @CheckForNull TaskId getTaskIdOrNull() {
        return null;
    }
    
    protected abstract @Nonnull String getHumanReadableId();
    
    public EndpointExecutionParticipant(@Nonnull Element config) throws ConfigurationException {
        predecessors = DomParser.parseSet(config, "after", "task-id")
            .stream().map(TaskId::new).collect(Collectors.toSet());
        inputIntermediateValues = DomParser.parseSet(config, "input-intermediate-value", "name")
            .stream().map(IntermediateValueName::new).collect(Collectors.toSet());
    }

    public @Nonnull Set<IntermediateValueName> getOutputIntermediateValues() {
        return Set.of();
    }

    protected record ElementAndRelationship(
        @Nonnull EndpointExecutionParticipant from,
        @Nonnull String relationship
    ) { }
    
    protected static void assertNoCircularDependenciesStartingFrom(
        @Nonnull List<EndpointExecutionParticipant> nodes, 
        @Nonnull List<ElementAndRelationship> pathSoFar,
        @Nonnull EndpointExecutionParticipant toVisit
    ) throws ConfigurationException {
        if (pathSoFar.stream().anyMatch(x -> x.from == toVisit)) 
            throw new ConfigurationException("Circular dependency found: " + 
                pathSoFar.stream()
                    .map(p -> p.from.getHumanReadableId() + " " + p.relationship)
                    .collect(Collectors.joining(", ")));
        
        for (var after : toVisit.predecessors) {
            var sources = nodes.stream().filter(n -> after.equals(n.getTaskIdOrNull())).toList();
            if (sources.isEmpty()) throw new ConfigurationException(
                toVisit.getHumanReadableId() + ": <after task-id='" + after.id() + "'/> but no task found with this ID");
            if (sources.size() > 1) throw new ConfigurationException(
                "Multiple tasks with same ID '" + after.id() + "'");
            var relationship = "has <after task-id='" + after.id() + "'/>";
            var pathSoFarInclUs = Stream.concat(pathSoFar.stream(), Stream.of(new ElementAndRelationship(toVisit, relationship)));
            assertNoCircularDependenciesStartingFrom(nodes, pathSoFarInclUs.toList(), sources.get(0));
        }
        
        for (var variable : toVisit.inputIntermediateValues) {
            var sources = nodes.stream().filter(n -> n.getOutputIntermediateValues().contains(variable)).toList();
            if (sources.isEmpty()) throw new ConfigurationException(
                toVisit.getHumanReadableId() + ": No <task>s produce output intermediate value '" + variable.name + "'");
            if (sources.size() > 1) throw new ConfigurationException(
                "Multiple <task>s produce output intermediate value '" + variable.name + "'");
            var relationship = "has <input-intermediate-value name='"+variable.name+"'/> " +
                "and " + toVisit.getHumanReadableId() + " has <output-intermediate-value name='"+variable.name+"'/>";
            var pathSoFarInclUs = Stream.concat(pathSoFar.stream(), Stream.of(new ElementAndRelationship(toVisit, relationship)));
            assertNoCircularDependenciesStartingFrom(nodes, pathSoFarInclUs.toList(), sources.get(0));
        }
    }
    
    public static void assertNoCircularDependencies(@Nonnull List<EndpointExecutionParticipant> nodes)
    throws ConfigurationException {
        for (var x : nodes) 
            assertNoCircularDependenciesStartingFrom(nodes, List.of(), x);
    }
}
