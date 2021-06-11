package endpoints.serviceportal.wicket.page;

import endpoints.DeploymentParameters;
import endpoints.generated.jooq.tables.records.ApplicationConfigRecord;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.wicket.ServicePortalSession.LoggedInApplicationData;
import org.apache.wicket.markup.html.WebMarkupContainer;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;
import org.apache.wicket.markup.html.list.ListItem;
import org.apache.wicket.markup.html.list.ListView;
import org.apache.wicket.markup.html.panel.FeedbackPanel;
import org.apache.wicket.request.cycle.RequestCycle;
import org.apache.wicket.request.mapper.parameter.PageParameters;

import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static endpoints.generated.jooq.Tables.SERVICE_PORTAL_LOGIN_APPLICATION;

public class AdminApplicationListPage extends AbstractLoggedInAdminPage {
    
    public AdminApplicationListPage() {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            add(new FeedbackPanel("feedback"));

            var applications = tx.jooq().select()
                .from(APPLICATION_CONFIG)
                .join(SERVICE_PORTAL_LOGIN_APPLICATION)
                .on(SERVICE_PORTAL_LOGIN_APPLICATION.APPLICATION_NAME.eq(APPLICATION_CONFIG.APPLICATION_NAME))
                .where(SERVICE_PORTAL_LOGIN_APPLICATION.USERNAME.eq(getSession().getLoggedInUserDataOrThrow().username))
                .fetchInto(APPLICATION_CONFIG);

            add(new WebMarkupContainer("noApplications").setVisible(applications.isEmpty()));
            add(new ListView<>("application", applications) {
                @Override protected void populateItem(ListItem<ApplicationConfigRecord> item) {
                    var record = item.getModelObject();
                    item.add(new Label("name", record.getApplicationName().name));
                    item.add(new Label("displayName", record.getDisplayName()));
                    item.add(new BookmarkablePageLink<>("edit", AdminEditApplicationPage.class, 
                        new PageParameters().set("app", record.getApplicationName().name)));
                    item.add(new Link<>("delete") {
                        @Override public void onClick() {
                            setResponsePage(new AdminDeleteApplicationPage(record));
                        }
                    });
                    item.add(new Link<>("login") {
                        @Override public void onClick() {
                            ServicePortalSession.get().loggedInApplicationData =
                                new LoggedInApplicationData(record.getApplicationName(), record.getDisplayName());
                            RequestCycle.get().setResponsePage(ApplicationHomePage.class);
                        }
                    });
                }
            });

            add(new BookmarkablePageLink<>("add", AdminEditApplicationPage.class));
        }
    }
}
