package endpoints.serviceportal.wicket.page;

import org.apache.wicket.markup.html.panel.FeedbackPanel;

public class AdminApplicationListPage extends AbstractLoggedInAdminPage {
    
    public AdminApplicationListPage() {
        add(new FeedbackPanel("feedback"));
    }
}
