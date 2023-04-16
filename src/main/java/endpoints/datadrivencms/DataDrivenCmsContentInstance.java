package endpoints.datadrivencms;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;

/** Represents an &lt;instance&gt; a &lt;content&gt; in a &lt;data-driven-cms&gt; file */
public class DataDrivenCmsContentInstance extends DataDrivenCmsInstance {
    
    protected final Element copy;

    public DataDrivenCmsContentInstance(@Nonnull String contentId, @Nonnull Element element) throws ConfigurationException {
        super(contentId, element);
        DomParser.assertNoOtherElements(element, "condition-folder", "copy");
        this.copy = DomParser.getMandatorySingleSubElement(element, "copy");
    }
}
