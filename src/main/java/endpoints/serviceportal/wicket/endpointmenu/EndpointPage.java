package endpoints.serviceportal.wicket.endpointmenu;

import endpoints.PublishEnvironment;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointContentMenuItem;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointFormMenuItem;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointLeafMenuItem;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import endpoints.serviceportal.wicket.page.AbstractLoggedInApplicationPage;
import lombok.Getter;
import lombok.Setter;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.DropDownChoice;
import org.apache.wicket.markup.html.form.EnumChoiceRenderer;
import org.apache.wicket.markup.html.form.Form;
import org.apache.wicket.model.LambdaModel;

import javax.annotation.Nonnull;

import java.util.Arrays;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

public abstract class EndpointPage extends AbstractLoggedInApplicationPage {
    
    protected @Getter @Setter @Nonnull PublishEnvironment environmentDropDownChoice;

    public EndpointPage(@Nonnull PublishEnvironment environment, @Nonnull MultiEnvironmentEndpointLeafMenuItem item) {
        super(null, item);
        
        this.environmentDropDownChoice = environment;

        add(new Label("name", item.menuItemName));
        
        var environmentForm = new Form<Void>("environmentForm") {
            @Override protected void onSubmit() {
                super.onSubmit();
                EndpointPage.this.setResponsePage(newPage(environmentDropDownChoice, item));
            }
        };
        environmentForm.add(new DropDownChoice<>("environment", 
            LambdaModel.of(this::getEnvironmentDropDownChoice, this::setEnvironmentDropDownChoice),
            Arrays.stream(PublishEnvironment.values()).filter(e -> item.itemForEnvironment.containsKey(e)).collect(toList()),
            new EnumChoiceRenderer<>(this)));
        environmentForm.setVisible(item.itemForEnvironment.size() > 1); // Don't display drop-down if there is nothing to choose
        add(environmentForm);
    }

    public static @Nonnull AbstractLoggedInApplicationPage newPage(
        @Nonnull PublishEnvironment environment, @Nonnull MultiEnvironmentEndpointLeafMenuItem item
    ) {
        var i = item.itemForEnvironment.get(environment);

        if (i instanceof ServicePortalEndpointContentMenuItem) return new EndpointContentPage(environment, item);
        else if (i instanceof ServicePortalEndpointFormMenuItem) return new EndpointFormPage(environment, item);
        else throw new RuntimeException("Unexpected class: " + i.getClass());
    }
}
