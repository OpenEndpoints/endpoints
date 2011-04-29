package endpoints;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.config.ParameterName;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.joining;

public class PlaintextParameterReplacer {

    /** Replaces variables such as ${XYZ} in the template. Variables which are not found remain in their original unreplaced form. */
    public static @Nonnull String replacePlainTextParameters(@Nonnull String template, @Nonnull Map<ParameterName,String> parameters) {
        for (var paramEntry : parameters.entrySet())
            template = template.replace("${" + paramEntry.getKey().name + "}", paramEntry.getValue());
        return template;
    }

    public static void assertParametersSuffice(
        @Nonnull Collection<ParameterName> params, @CheckForNull CharSequence template, @Nonnull String msg
    ) throws ConfigurationException {
        if (template == null) return;
        var m = Pattern.compile("\\$\\{([\\w-]+)\\}").matcher(template);
        while (m.find())
            if ( ! params.contains(new ParameterName(m.group(1))))
                throw new ConfigurationException(msg+": Pattern '"+template+"' contains parameter ${"+m.group(1)+"}" +
                    " but it is not available; available parameters are; " +
                    params.stream().map(p -> "${"+p.name+"}").collect(joining(", ")));
    }
}
