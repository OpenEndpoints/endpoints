package endpoints;

import org.slf4j.LoggerFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;
import org.json.XML;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.regex.Pattern;
import java.util.stream.StreamSupport;

import static com.databasesandlife.util.DomParser.newDocumentBuilder;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

public class JsonToXmlConverter {

    protected static final Pattern patternFirstChar = Pattern.compile("^[^a-z_]");
    protected static final Pattern patternNonFirstChar = Pattern.compile("[^a-z0-9-_.]");
    
    protected @Nonnull String makeKeySafeForXml(@Nonnull Pattern pattern, @Nonnull String key) {
        var result = new StringBuilder();
        var matcher = pattern.matcher(key);
        while (matcher.find()) {
            var c = matcher.group();
            assert c.length() == 1;
            matcher.appendReplacement(result, String.format("_%04x_", (int) c.toCharArray()[0]));
        }
        matcher.appendTail(result);
        return result.toString();
    }

    /**
     * Alter JSON to have keys which are safe as XML element names.
     * JSON objects can have keys such as "foo bar" but XML elements cannot have keys such as &lt;foo bar&gt;.
     * The default JSON-to-XML converter simply converts JSON object keys to XML element names verbatim.
     */
    protected @Nonnull Object makeKeysInJsonSafeForXml(@Nonnull Object input) {
        if (input instanceof JSONObject) 
            return new JSONObject(((JSONObject) input).keySet().stream().collect(toMap(
                key -> makeKeySafeForXml(patternNonFirstChar, makeKeySafeForXml(patternFirstChar, key)),
                key -> makeKeysInJsonSafeForXml(((JSONObject) input).opt(key))
            )));
        else if (input instanceof JSONArray) 
            return new JSONArray(StreamSupport.stream(((JSONArray) input).spliterator(), false)
                .map(e -> makeKeysInJsonSafeForXml(e))
                .collect(toList()));
        else return input;
    }
    
    public @Nonnull Element convert(@Nonnull String contentType, @Nonnull InputStream jsonInputStream, @Nonnull String rootElement) 
    throws JSONException, IOException {
        
        @SuppressWarnings("UnstableApiUsage") 
        var charset = com.google.common.net.MediaType.parse(contentType).charset().or(UTF_8);
        
        String xmlString;
        try (var reader = new InputStreamReader(jsonInputStream, charset)) {
            var jsonObject = new JSONTokener(reader).nextValue();
            var jsonObjectWithSafeKeys = makeKeysInJsonSafeForXml(jsonObject);
            xmlString = XML.toString(jsonObjectWithSafeKeys);
        }

        var xmlIncludingHeader = "<?xml version=\"1.0\" encoding=\"utf-8\"?><"+rootElement+">"
            + xmlString + "</"+rootElement+">";
        
        try {
            return newDocumentBuilder().parse(new ByteArrayInputStream(xmlIncludingHeader.getBytes(UTF_8))).getDocumentElement();
        }
        catch (SAXException e) {
            LoggerFactory.getLogger(getClass()).info("JSON converted to XML: " + xmlIncludingHeader);
            throw new JSONException(e);
        }
    } 
}
