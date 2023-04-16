package endpoints.datadrivencms;

import com.databasesandlife.util.DomParser;
import com.databasesandlife.util.gwtsafe.ConfigurationException;
import endpoints.condition.ConditionFolderList;
import endpoints.config.IntermediateValueName;
import endpoints.config.ParameterName;
import org.w3c.dom.Element;

import javax.annotation.Nonnull;
import java.util.Set;

/** Represents an &lt;instance&gt; in a &lt;data-driven-cms&gt; file */
public abstract class DataDrivenCmsInstance {
    
    protected final String contentId;
    protected final int priority;
    protected final ConditionFolderList conditionFolders;
    
    public DataDrivenCmsInstance(@Nonnull String contentId, @Nonnull Element element) throws ConfigurationException {
        this.contentId = contentId;
        this.priority = DomParser.parseMandatoryIntAttribute(element, "priority");
        this.conditionFolders = new ConditionFolderList(element);
    }

    public void assertParametersSuffice(
        @Nonnull Set<ParameterName> params, @Nonnull Set<IntermediateValueName> visibleIntermediateValues
    ) throws ConfigurationException {
        conditionFolders.assertParametersSuffice(params, visibleIntermediateValues);
    }
}
