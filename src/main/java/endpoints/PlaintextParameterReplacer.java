package endpoints;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.regex.Matcher.quoteReplacement;
import static java.util.stream.Collectors.joining;

public class PlaintextParameterReplacer {

    public static void assertParametersSuffice(
        @Nonnull Set<String> params, @CheckForNull CharSequence template, @Nonnull String msg
    ) throws ConfigurationException {
        if (template == null) return;
        var m = Pattern.compile("\\$\\{([\\w-]+)}").matcher(template);
        while (m.find())
            if ( ! params.contains(m.group(1))) {
                var availableParameters = params.isEmpty() ? "no parameters are available" : "available parameters are " +
                    params.stream().sorted().map(p -> "${"+p+"}").collect(joining(", "));
                throw new ConfigurationException(msg+": Pattern '"+template+"' contains parameter ${"+m.group(1)+"}" +
                    " but it is not available; " + availableParameters);
            }
    }
    
    public static @Nonnull Set<String> getKeys(
        @Nonnull Set<ParameterName> params, @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) {
        var stringKeys = new HashSet<String>();
        stringKeys.addAll(params.stream().map(k -> k.name).collect(Collectors.toSet()));
        stringKeys.addAll(visibleIntermediateValues.stream().map(k -> k.name).collect(Collectors.toSet()));
        stringKeys.addAll(TransformationContext.getSystemParameterNames());
        return stringKeys;
    }

    public static void assertParametersSuffice(
        @Nonnull Set<ParameterName> params, @Nonnull Set<IntermediateValueName> visibleIntermediateValues,
        @CheckForNull CharSequence template, @Nonnull String msg
    ) throws ConfigurationException {
        assertParametersSuffice(getKeys(params, visibleIntermediateValues), template, msg);
    }
    
    public static boolean containsParameters(@CheckForNull CharSequence template) {
        try {
            assertParametersSuffice(Set.of(), template, "");
            return false;
        }
        catch (ConfigurationException e) {
            return true;
        }
    }

    /** Replaces variables such as ${XYZ} in the template. Variables which are not found remain in their original unreplaced form. */
    public static String replacePlainTextParameters(@Nonnull String template, @Nonnull Map<String, LazyCachingValue> parameters) {
        var result = new StringBuilder();
        var matcher = Pattern.compile("\\$\\{(.+?)}").matcher(template);
        while (matcher.find()) {
            var name = matcher.group(1);
            var replacementLazyValue = parameters.get(name);
            var replacement = replacementLazyValue == null ? matcher.group() : replacementLazyValue.get();
            matcher.appendReplacement(result, quoteReplacement(replacement));
        }
        matcher.appendTail(result);
        return result.toString();
    }

}
