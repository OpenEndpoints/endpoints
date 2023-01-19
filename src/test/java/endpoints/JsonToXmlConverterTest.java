package endpoints;

import junit.framework.TestCase;
import org.json.JSONArray;
import org.json.JSONTokener;

import java.io.StringReader;

public class JsonToXmlConverterTest extends TestCase {

    public void testMakeKeysSafeForXml() {
        var jsonString = "[ { \"1foo BA\uffffr1\": 123 } ]";
        var json = new JSONTokener(new StringReader(jsonString)).nextValue();
        var safeJson = (JSONArray) new JsonToXmlConverter().makeKeysInJsonSafeForXml(json);
        var safeJsonString = safeJson.toString();
        assertEquals("[{\"_0031_foo_0020_BA_ffff_r1\":123}]", safeJsonString);
    }
}
