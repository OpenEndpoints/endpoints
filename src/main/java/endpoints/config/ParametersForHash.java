package endpoints.config;

import com.databasesandlife.util.MD5Hex;
import endpoints.PublishEnvironment;
import lombok.RequiredArgsConstructor;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class ParametersForHash {

    public final @Nonnull List<ParameterName> parameters;

    public String calculateHash(
        @Nonnull String secretKey, @Nonnull PublishEnvironment environment, @Nonnull NodeName endpoint,
        @Nonnull Map<ParameterName, String> parameterValues
    ) {
        parameterValues.forEach((k,v) -> { if (v == null) throw new NullPointerException("Parameter " + k.name); });
        var paramValues = parameters.stream().map(k -> parameterValues.get(k)).collect(Collectors.joining());
        var cleartext = endpoint.name + paramValues + environment.name() + secretKey;
        return MD5Hex.sha256hex(cleartext);
    }
}
