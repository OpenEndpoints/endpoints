package endpoints.serviceportal.wicket.page;

import com.databasesandlife.util.gwtsafe.CleartextPassword;
import com.databasesandlife.util.jdbc.DbTransaction;
import endpoints.DeploymentParameters;
import endpoints.GitApplicationRepository;
import endpoints.config.ApplicationName;
import endpoints.generated.jooq.tables.records.ApplicationConfigRecord;
import endpoints.generated.jooq.tables.records.ServicePortalLoginApplicationRecord;
import endpoints.serviceportal.wicket.panel.ServicePortalFeedbackPanel;
import lombok.Getter;
import lombok.Setter;
import org.apache.wicket.RestartResponseException;
import org.apache.wicket.markup.html.basic.Label;
import org.apache.wicket.markup.html.form.*;
import org.apache.wicket.markup.html.link.BookmarkablePageLink;
import org.apache.wicket.model.LambdaModel;
import org.apache.wicket.model.Model;
import org.apache.wicket.request.mapper.parameter.PageParameters;
import org.jooq.exception.DataAccessException;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;

import static endpoints.generated.jooq.Tables.APPLICATION_CONFIG;

// Test:
//    - Create a new application but with all fields missing, errors
//    - Create a new application successfully with public, name is abc-123
//    - Edit an application and make no changes
//    - Edit a non-existent application, see 404
//    - Name is invalid e.g. spaces
//    - Create: name already in use
//    - Display name already in use
//    - Change to username/password, u missing, error
//    - Change to username/password, pw missing, error
//    - Change to private key, key missing, error
//    - Change to username/password, success
//    - Edit username only, success
//    - Edit password only, success
//    - Change to private key, success
//    - Edit but don't change key, success
//    - Edit and change key, success
//    - Git repo doesn't work or password wrong etc., error
//    - Use SSH URL with no auth, see it fail
//    - Use SSH URL with u/p, see it fail
//    - Use HTTPS URL with no auth, see it fail
//    - Use HTTPS URL with private key, see it fail
//    - Cancel

public class AdminEditApplicationPage extends AbstractLoggedInAdminPage {
    
    protected final @CheckForNull ApplicationName application;
    
    protected static final String notShown = "(not shown)";
    protected enum AuthType { authPublic, authUsernamePassword, authPrivateKey }
    
    protected @Getter @Setter String name, displayName, gitUrl, username, newPassword, newPrivateKey;
    protected @Getter @Setter AuthType authType;
    
    public AdminEditApplicationPage(@Nonnull PageParameters params) {
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            if (params.get("app").isEmpty()) application = null;
            else application = new ApplicationName(params.get("app").toString());
            
            if (application == null) {
                authType = AuthType.authPublic;
            } else {
                var record = tx.jooq().fetchOne(APPLICATION_CONFIG, APPLICATION_CONFIG.APPLICATION_NAME.eq(application));
                if (record == null) {
                    getSession().error("Application '" + application.name() + "' not found");
                    throw new RestartResponseException(AdminApplicationListPage.class);
                }
                
                name = record.getApplicationName().name();
                displayName = record.getDisplayName();
                gitUrl = record.getGitUrl();
                username = record.getGitUsername();
                newPassword = record.getGitPasswordCleartext() == null ? null : notShown;
                newPrivateKey = record.getGitRsaPrivateKeyCleartext() == null ? null : notShown;
                
                if (record.getGitUsername() != null) authType = AuthType.authUsernamePassword;
                else if (record.getGitRsaPrivateKeyCleartext() != null) authType = AuthType.authPrivateKey;
                else authType = AuthType.authPublic;
            }
            
            add(new Label("title", application == null ? "Add Application" : "Update Application '"+application.name()+"'"));
            
            var form = new Form<>("form");
            add(form);
            
            var authRadioGroup = new RadioGroup<>("authRadioGroup", LambdaModel.of(this::getAuthType, this::setAuthType));
            form.add(authRadioGroup);

            authRadioGroup.add(new ServicePortalFeedbackPanel("feedback"));
            authRadioGroup.add(new TextField<>("nameNew", LambdaModel.of(this::getName, this::setName))
                .setRequired(true).setVisible(application == null));
            authRadioGroup.add(new Label("nameExisting", name).setVisible(application != null));
            authRadioGroup.add(new TextField<>("displayName", LambdaModel.of(this::getDisplayName, this::setDisplayName))
                .setRequired(true));
            authRadioGroup.add(new TextField<>("gitUrl", LambdaModel.of(this::getGitUrl, this::setGitUrl))
                .setRequired(true));
            authRadioGroup.add(new TextField<>("username", LambdaModel.of(this::getUsername, this::setUsername)));
            authRadioGroup.add(new TextField<>("password", LambdaModel.of(this::getNewPassword, this::setNewPassword)));
            authRadioGroup.add(new TextArea<>("privateKey", LambdaModel.of(this::getNewPrivateKey, this::setNewPrivateKey)));
            
            for (var t : AuthType.values())
                authRadioGroup.add(new Radio<>(t.name(), Model.of(t)));
            
            form.add(new BookmarkablePageLink<>("cancel", AdminApplicationListPage.class));
            form.add(new Button("submit") {
                @Override public void onSubmit() {
                    AdminEditApplicationPage.this.onSubmit();
                }
            }.add(new Label("label", application == null ? "Add Application" : "Update Application")));
        }
    }
    
    protected void onSubmit() {
        name = name.toLowerCase();
        if ( ! name.matches("[a-z0-9-]+")) error("Application name must be a-z, 0-9 and hyphen only");
        if (authType == AuthType.authUsernamePassword && username == null) error("Username mandatory");
        if (authType == AuthType.authUsernamePassword && newPassword == null) error("Password mandatory");
        if (authType == AuthType.authPrivateKey && newPrivateKey == null) error("Private Key mandatory");
        if (hasFeedbackMessage()) return;
        
        try (var tx = DeploymentParameters.get().newDbTransaction()) {
            ApplicationConfigRecord r;
            if (application == null) r = tx.jooq().newRecord(APPLICATION_CONFIG);
            else r = tx.jooq().fetchSingle(APPLICATION_CONFIG, APPLICATION_CONFIG.APPLICATION_NAME.eq(application));
            
            if (application == null) r.setApplicationName(new ApplicationName(name));
            r.setDisplayName(displayName);
            r.setGitUrl(gitUrl);
            
            switch (authType) {
                case authPublic -> {
                    r.setGitUsername(null);
                    r.setGitPasswordCleartext(null);
                    r.setGitRsaPrivateKeyCleartext(null);
                }
                case authUsernamePassword -> {
                    r.setGitUsername(username);
                    if ( ! newPassword.equals(notShown)) r.setGitPasswordCleartext(new CleartextPassword(newPassword));
                    r.setGitRsaPrivateKeyCleartext(null);
                }
                case authPrivateKey -> {
                    r.setGitUsername(null);
                    r.setGitPasswordCleartext(null);
                    if ( ! newPrivateKey.equals(notShown)) r.setGitRsaPrivateKeyCleartext(newPrivateKey);
                }
                default -> throw new IllegalStateException(authType.name());
            }
            
            try {
                var repo = new GitApplicationRepository(r);
                repo.fetchLatestRevision();
            }
            catch (GitApplicationRepository.RepositoryCommandFailedException e) {
                error("Cannot reach Git: " + e.getMessage());
                return;
            }
            
            try { r.store(); }
            catch (DataAccessException e) {
                var u = DbTransaction.parseUniqueConstraintViolationOrNull(e.getMessage());
                if ("application_config_pkey".equals(u)) error("Application name already in use by another application");
                if ("application_config_display_name_key".equals(u)) error("Display name in use by another application");
                if (hasFeedbackMessage()) return;
                throw e;
            }
            
            if (application == null) {
                var association = new ServicePortalLoginApplicationRecord();
                association.setUsername(getSession().getLoggedInUserDataOrThrow().username);
                association.setApplicationName(r.getApplicationName());
                tx.insert(association);
            }
            
            if (application == null) getSession().info("Application '" + name + "' created successfully");
            else getSession().info("Application '" + name + "' updated successfully");
            
            setResponsePage(AdminApplicationListPage.class);
            
            tx.commit();
        }
    }
}
