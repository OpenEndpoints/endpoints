package endpoints.serviceportal.wicket.panel;

import com.databasesandlife.util.wicket.MultilineLabelWithClickableLinks;
import org.apache.wicket.AttributeModifier;
import org.apache.wicket.Component;
import org.apache.wicket.feedback.ContainerFeedbackMessageFilter;
import org.apache.wicket.feedback.FeedbackMessage;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.model.IModel;
import org.apache.wicket.model.Model;

import javax.annotation.Nonnull;

/**
 * Use this as &lt;wicket:container wicket:id="feedback"/&gt;.
 * Do not place this in a &lt;div&gt; as then the layout breaks.
 */
public class ServicePortalFeedbackPanel extends FeedbackPanel {

    public ServicePortalFeedbackPanel(String wicketId) {
        super(wicketId);
    }

    public ServicePortalFeedbackPanel(String wicketId, ContainerFeedbackMessageFilter containerFeedbackMessageFilter) {
        super(wicketId, containerFeedbackMessageFilter);
    }

    /**
     * Adds "class" to "messages" item
     */
    @Override protected ListItem<FeedbackMessage> newMessageItem(int index, @Nonnull IModel<FeedbackMessage> itemModel) {
        var result = super.newMessageItem(index, itemModel);
        result.add(AttributeModifier.append("class", new Model<String>() {
            @Override public String getObject() {
                if (result.getModelObject().isError()) return "uk-alert-danger";
                else return "uk-alert-success";
            }
        }));
        return result;
    }

    @Override protected Component newMessageDisplayComponent(String id, FeedbackMessage message) {
        // Multiline: Needed for "publish" which displays individual XSLT errors on multiple lines
        return new MultilineLabelWithClickableLinks(id, message.getMessage());
    }
}
