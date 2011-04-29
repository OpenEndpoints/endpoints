package endpoints.datasource;

import junit.framework.TestCase;
import org.w3c.dom.Element;

public class DataSourceCommandResultTest extends TestCase {

    public void testGetOrThrow_TransformationFailedException() {
        var result  = new DataSourceCommandResult() {
            @Override protected Element[] populateOrThrow() throws TransformationFailedException {
                throw new TransformationFailedException("foo");
            }
        };

        try { result.getOrThrow(); }
        catch (TransformationFailedException e) { assertEquals("foo", e.getMessage()); }
    }
}