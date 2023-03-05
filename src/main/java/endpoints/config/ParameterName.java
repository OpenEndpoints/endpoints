package endpoints.config;

import com.databasesandlife.util.gwtsafe.ConfigurationException;
import lombok.Value;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.regex.Pattern;

@Value
public class ParameterName implements Serializable, Comparable<ParameterName> {
    
    public final static String desc = "must have at least one character, and only consist of A-Z, a-z, 0-9, underscore, hyphen or dot";
    public final static Pattern regex = Pattern.compile("[A-Za-z0-9_.-]+");
    
    public final @Nonnull String name;

    public void assertNameValid() throws ConfigurationException {
        if ( ! regex.matcher(name).matches())
            throw new ConfigurationException("Parameter name '" + name + "' invalid: " + desc);
    }

    @Override public int compareTo(@Nonnull ParameterName x) { return name.compareTo(x.name); }
}
