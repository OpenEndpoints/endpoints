package endpoints.config;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import org.w3c.dom.Element;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.stream.Collectors.toList;

public abstract class IntermediateValueProducerConsumer {

    public final @Nonnull Set<IntermediateValueName> inputIntermediateValues;
    
    public IntermediateValueProducerConsumer(@CheckForNull Element config) throws ConfigurationException {
        if (config == null) inputIntermediateValues = emptySet();
        else inputIntermediateValues = DomParser.parseSet(config, "input-intermediate-value", "name")
            .stream().map(IntermediateValueName::new).collect(Collectors.toSet());
    }

    public @Nonnull Set<IntermediateValueName> getOutputIntermediateValues() {
        return emptySet();
    }
    
    protected static void assertNoCircularDependenciesStartingFrom(
        @Nonnull List<IntermediateValueProducerConsumer> nodes, 
        @Nonnull List<IntermediateValueProducerConsumer> alreadyVisited,
        @Nonnull IntermediateValueProducerConsumer toVisit
    ) throws ConfigurationException {
        if (alreadyVisited.contains(toVisit)) 
            throw new ConfigurationException("Intermediate Values: Circular Dependencies");
        
        var alreadyVisitedIncludingUs = new ArrayList<>(alreadyVisited);
        alreadyVisitedIncludingUs.add(toVisit);
        
        for (var variable : toVisit.inputIntermediateValues) {
            var sources = nodes.stream().filter(n -> n.getOutputIntermediateValues().contains(variable)).collect(toList());
            if (sources.size() == 0) throw new ConfigurationException(
                "No <task>s produce output intermediate value '" + variable.name + "'");
            if (sources.size() > 1) throw new ConfigurationException(
                "Multiple <task>s produce output intermediate value '" + variable.name + "'");
            assertNoCircularDependenciesStartingFrom(nodes, alreadyVisitedIncludingUs, sources.get(0));
        }
    }
    
    public static void assertNoCircularDependencies(@Nonnull List<IntermediateValueProducerConsumer> nodes)
    throws ConfigurationException {
        for (var x : nodes) 
            assertNoCircularDependenciesStartingFrom(nodes, emptyList(), x);
    }
}
