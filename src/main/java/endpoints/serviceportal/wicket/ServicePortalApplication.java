package endpoints.serviceportal.wicket;

import com.databasesandlife.util.gwtsafe.CleartextPassword;
import com.databasesandlife.util.wicket.CleartextPasswordConverter;
import com.databasesandlife.util.wicket.UuidConverter;
import endpoints.DeploymentParameters;
import endpoints.serviceportal.ServicePortalUsername;
import endpoints.serviceportal.wicket.converter.ServicePortalUsernameConverter;
import endpoints.serviceportal.wicket.page.*;
import org.apache.wicket.ConverterLocator;
import org.apache.wicket.IConverterLocator;
import org.apache.wicket.markup.html.WebPage;
import org.apache.wicket.protocol.http.WebApplication;
import org.apache.wicket.request.Request;
import org.apache.wicket.request.Response;
import org.flywaydb.core.Flyway;

import javax.annotation.Nonnull;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.apache.wicket.settings.RequestCycleSettings.RenderStrategy.ONE_PASS_RENDER;

public class ServicePortalApplication extends WebApplication {
    
    public static @Nonnull ServicePortalApplication get() { 
        return (ServicePortalApplication) WebApplication.get(); 
    }

    @Override public @Nonnull Class<? extends WebPage> getHomePage() {
        return LoginPage.class;
    }

    @Override public void init() {
        super.init();

        var flyway = new Flyway();
        flyway.setDataSource(DeploymentParameters.get().jdbcUrl, null, null);
        flyway.setLocations("classpath:endpoints/migration");
        flyway.migrate();

        // Check that environment variables are set and valid
        DeploymentParameters.get();

        // If the session expires, then show "session expired", don't just silently lose the user's state
        // http://stackoverflow.com/a/29318116/220627
        getPageSettings().setRecreateBookmarkablePagesAfterExpiry(false);

        // Our HTML files are UTF-8 (by default Wicket uses the JVM encoding, which is non-UTF-8 by default on Windows)
        getMarkupSettings().setDefaultMarkupEncoding(StandardCharsets.UTF_8.name());

        // Avoid redirects to ?2 as they are visually ugly
        getRequestCycleSettings().setRenderStrategy(ONE_PASS_RENDER);

        // Beautiful URLs
        mountPage("/choose-application", ChooseApplicationPage.class);
        mountPage("/admin/", AdminApplicationListPage.class);
        mountPage("/change-password", ChangePasswordPage.class);
        mountPage("/home", ApplicationHomePage.class);
        mountPage("/request-log", RequestLogPage.class);
        mountPage("/publish", PublishPage.class);
        mountPage("/calculate-hash", CalculateHashPage.class);
        mountPage("/secret-key", GenerateNewSecretKeyPage.class);
    }

    @Override public @Nonnull ServicePortalSession newSession(@Nonnull Request request, @Nonnull Response response) {
        return new ServicePortalSession(request);
    }

    @Override protected IConverterLocator newConverterLocator() {
        ConverterLocator result = (ConverterLocator) super.newConverterLocator();
        result.set(CleartextPassword.class, new CleartextPasswordConverter());
        result.set(ServicePortalUsername.class, new ServicePortalUsernameConverter());
        result.set(UUID.class, new UuidConverter());
        return result;
    }
}
