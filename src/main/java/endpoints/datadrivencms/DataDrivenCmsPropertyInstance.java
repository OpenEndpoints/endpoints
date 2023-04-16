package endpoints.datadrivencms;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;

/** Represents an &lt;instance&gt; a &lt;property&gt; in a &lt;data-driven-cms&gt; file */
public class DataDrivenCmsPropertyInstance extends DataDrivenCmsInstance {

    protected final String value;

    public DataDrivenCmsPropertyInstance(@Nonnull String contentId, @Nonnull Element element) throws ConfigurationException {
        super(contentId, element);
        DomParser.assertNoOtherElements(element, "condition-folder");
        this.value = DomParser.getMandatoryAttribute(element, "value");
    }
}
