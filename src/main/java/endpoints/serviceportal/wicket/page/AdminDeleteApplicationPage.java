package endpoints.serviceportal.wicket.page;

import endpoints.DeploymentParameters;
import endpoints.config.ApplicationName;
import endpoints.generated.jooq.tables.records.ApplicationConfigRecord;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.markup.html.link.Link;

import javax.annotation.Nonnull;

import static endpoints.generated.jooq.Tables.*;
import static org.jooq.impl.DSL.select;

public class AdminDeleteApplicationPage extends AbstractLoggedInAdminPage {
    
    public AdminDeleteApplicationPage(@Nonnull ApplicationConfigRecord app) {
        add(new Label("name", app.getApplicationName().name));
        add(new Label("displayName", app.getDisplayName()));
        add(new Link<>("submit") {
            @Override public void onClick() {
                AdminDeleteApplicationPage.this.onSubmit(app.getApplicationName());
            }
        });
        add(new BookmarkablePageLink<>("cancel", AdminApplicationListPage.class));
    }
    
    public void onSubmit(@Nonnull ApplicationName name) {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            tx.jooq().deleteFrom(SHORT_LINK_TO_ENDPOINT_PARAMETER)
                .where(SHORT_LINK_TO_ENDPOINT_PARAMETER.SHORT_LINK_TO_ENDPOINT_CODE.in(
                    select(SHORT_LINK_TO_ENDPOINT.SHORT_LINK_TO_ENDPOINT_CODE)
                    .from(SHORT_LINK_TO_ENDPOINT)
                    .where(SHORT_LINK_TO_ENDPOINT.APPLICATION.eq(name))
                )).execute();
            tx.jooq().deleteFrom(SHORT_LINK_TO_ENDPOINT)
                .where(SHORT_LINK_TO_ENDPOINT.APPLICATION.eq(name)).execute();
            tx.jooq().deleteFrom(APPLICATION_PUBLISH)
                .where(APPLICATION_PUBLISH.APPLICATION_NAME.eq(name)).execute();
            tx.jooq().deleteFrom(REQUEST_LOG)
                .where(REQUEST_LOG.APPLICATION.eq(name)).execute();
            tx.jooq().deleteFrom(SERVICE_PORTAL_LOGIN_APPLICATION)
                .where(SERVICE_PORTAL_LOGIN_APPLICATION.APPLICATION_NAME.eq(name)).execute();
            tx.jooq().deleteFrom(APPLICATION_CONFIG)
                .where(APPLICATION_CONFIG.APPLICATION_NAME.eq(name)).execute();
            getSession().info("Application '" + name.name + "' successfully deleted.");
            setResponsePage(AdminApplicationListPage.class);
            tx.commit();
        }
    }
}
