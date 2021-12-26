package endpoints.serviceportal.wicket.panel;

import org.apache.wicket.markup.ComponentTag;
import org.apache.wicket.markup.MarkupStream;
import org.apache.wicket.markup.html.WebComponent;
import org.apache.wicket.util.string.Strings;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.util.UUID;
import java.util.regex.Pattern;

public class SubstringHighlightLabel extends WebComponent {
    
    @CheckForNull String value, highlight;
    
    public SubstringHighlightLabel(String id, @CheckForNull String highlight, @CheckForNull String value) {
        super(id);
        this.value = value;
        this.highlight = highlight;
    }
    
    public boolean matches() {
        if (value == null || highlight == null) return false;
        return value.toLowerCase().contains(highlight.toLowerCase());
    }
    
    protected static @Nonnull CharSequence encodeHtml(@CheckForNull String highlight, @CheckForNull String value) {
        var start = UUID.randomUUID().toString();
        var end = UUID.randomUUID().toString();

        var html = (CharSequence) value;
        if (html == null) html = "";
        if (highlight != null) {
            var m = Pattern.compile(Pattern.quote(highlight), Pattern.CASE_INSENSITIVE).matcher(html);
            var result = new StringBuffer();
            while (m.find()) m.appendReplacement(result, start + m.group() + end);
            m.appendTail(result);
            html = result;
        }
        html = Strings.escapeMarkup(html);
        html = html.toString().replace(start, "<span class='filter-highlight'>");
        html = html.toString().replace(end, "</span>");

        return html;
    }

    @Override public void onComponentTagBody(MarkupStream markupStream, ComponentTag openTag) {
        replaceComponentTagBody(markupStream, openTag, encodeHtml(highlight, value));
    }
}
