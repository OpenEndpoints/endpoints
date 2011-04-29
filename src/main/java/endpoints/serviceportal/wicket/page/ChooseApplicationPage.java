package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.jdbc.DbTransaction;
import com.databasesandlife.util.wicket.LambdaDisplayValueChoiceRenderer;
import endpoints.generated.jooq.tables.records.ApplicationConfigRecord;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import endpoints.serviceportal.wicket.ServicePortalSession;
import endpoints.serviceportal.ServicePortalUsername;
import lombok.Getter;
import lombok.Setter;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.LambdaModel;
import org.apache.wicket.request.cycle.RequestCycle;

import javax.annotation.Nonnull;
import java.util.*;

import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;
import static endpoints.generated.jooq.Tables.SERVICE_PORTAL_LOGIN_APPLICATION;
import static org.jooq.impl.DSL.select;

public class ChooseApplicationPage extends AbstractPage {

    protected @Nonnull ServicePortalUsername username;
    protected @Nonnull List<ApplicationConfigRecord> applications;
    protected @Getter @Setter ApplicationConfigRecord chosenApplication;

    public ChooseApplicationPage(@Nonnull DbTransaction tx, @Nonnull ServicePortalUsername username) {
        this.username = username;

        this.applications = tx.jooq()
            .selectFrom(APPLICATION_CONFIG)
            .where(APPLICATION_CONFIG.APPLICATION_NAME.in(
                select(SERVICE_PORTAL_LOGIN_APPLICATION.APPLICATION_NAME)
                .from(SERVICE_PORTAL_LOGIN_APPLICATION)
                .where(SERVICE_PORTAL_LOGIN_APPLICATION.USERNAME.eq(username)))
            )
            .orderBy(APPLICATION_CONFIG.DISPLAY_NAME)
            .fetch();

        add(new ServicePortalFeedbackPanel("feedback"));

        var form = new Form<Void>("form") {
            @Override protected void onSubmit() {
                ChooseApplicationPage.this.onSubmit();
            }
        };
        add(form);

        var dropDown = new DropDownChoice<>("application",
            LambdaModel.of(this::getChosenApplication, this::setChosenApplication),
            this.applications, new LambdaDisplayValueChoiceRenderer<>(app -> app.getDisplayName()));
        dropDown.setRequired(true);
        form.add(dropDown);
    }

    protected void onSubmit() {
        ServicePortalSession.get().login(username, chosenApplication.getApplicationName(), chosenApplication.getDisplayName());
        continueToOriginalDestination();
        RequestCycle.get().setResponsePage(ApplicationHomePage.class);
    }
}
