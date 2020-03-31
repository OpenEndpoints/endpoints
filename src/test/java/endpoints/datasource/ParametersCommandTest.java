package endpoints.datasource;

import endpoints.UploadedFile;
import junit.framework.TestCase;
import lombok.RequiredArgsConstructor;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Arrays.asList;

public class ParametersCommandTest extends TestCase {
    
    @RequiredArgsConstructor
    protected class TestFile extends UploadedFile {
        protected final String contents;

        @Nonnull @Override public String getFieldName() { return "foo"; }
        @Nonnull @Override public String getContentType() { return "text/plain"; }
        @Nonnull @Override public InputStream getInputStream() { return new ByteArrayInputStream(contents.getBytes(UTF_8)); }
        @CheckForNull @Override public String getSubmittedFileName() { return "foo.txt"; }
    }

    public void testCreateParametersElements() {
        var element = ParametersCommand.createParametersElements(Map.of(), 
            Map.of(), List.of(new TestFile("foo"), new TestFile("<foo/>")));
        assertEquals(2, element.length);
        assertEquals("file-upload", element[0].getTagName());
        assertEquals(0, element[0].getChildNodes().getLength());
        assertEquals("file-upload", element[1].getTagName());
        assertEquals(1, element[1].getChildNodes().getLength());
        assertEquals("foo", element[1].getChildNodes().item(0).getNodeName());
    }
}