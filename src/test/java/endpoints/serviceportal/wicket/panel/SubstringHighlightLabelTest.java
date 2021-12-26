package endpoints.serviceportal.wicket.panel;

import junit.framework.TestCase;

import static endpoints.serviceportal.wicket.panel.SubstringHighlightLabel.encodeHtml;

public class SubstringHighlightLabelTest extends TestCase {

    public void testEncodeHtml() {
        assertEquals("&lt;", encodeHtml(null, "<"));
        assertEquals("<span class='filter-highlight'>f&lt;</span>o&lt;", encodeHtml("f<", "f<o<"));
    }
}