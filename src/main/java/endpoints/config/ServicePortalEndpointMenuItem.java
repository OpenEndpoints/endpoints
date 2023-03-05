package endpoints.config;

import endpoints.PublishEnvironment;
import lombok.EqualsAndHashCode;

import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

@EqualsAndHashCode
public abstract class ServicePortalEndpointMenuItem implements Serializable {
    
    public final @Nonnull String menuItemName;
    public final @Nonnull Set<PublishEnvironment> environments;

    public ServicePortalEndpointMenuItem(@Nonnull String menuItemName, @Nonnull Set<PublishEnvironment> environments) {
        this.menuItemName = menuItemName;
        this.environments = environments;
    }

    @EqualsAndHashCode(callSuper = true)
    public abstract static class ServicePortalEndpointLeafMenuItem extends ServicePortalEndpointMenuItem {
        public ServicePortalEndpointLeafMenuItem(@Nonnull String menuItemName, @Nonnull Set<PublishEnvironment> environments) {
            super(menuItemName, environments);
        }
    }

    @EqualsAndHashCode(callSuper = true)
    public static class ServicePortalEndpointContentMenuItem extends ServicePortalEndpointLeafMenuItem {
        public final @Nonnull NodeName content;

        public ServicePortalEndpointContentMenuItem(
            @Nonnull String menuItemName, @Nonnull Set<PublishEnvironment> environments,
            @Nonnull NodeName content
        ) {
            super(menuItemName, environments);
            this.content = content;
        }
    }

    @EqualsAndHashCode(callSuper = true)
    public static class ServicePortalEndpointFormMenuItem extends ServicePortalEndpointLeafMenuItem {
        public final @Nonnull NodeName form, result;
        
        public ServicePortalEndpointFormMenuItem(
            @Nonnull String menuItemName, @Nonnull Set<PublishEnvironment> environments, 
            @Nonnull NodeName form, @Nonnull NodeName result
        ) {
            super(menuItemName, environments);
            this.form = form;
            this.result = result;
        }
    }

    @EqualsAndHashCode(callSuper = true)
    public static class ServicePortalEndpointMenuFolder extends ServicePortalEndpointMenuItem {
        public final @Nonnull List<ServicePortalEndpointMenuItem> children;

        public ServicePortalEndpointMenuFolder(
            @Nonnull String menuItemName, @Nonnull Set<PublishEnvironment> environments,
            @Nonnull List<ServicePortalEndpointMenuItem> children
        ) {
            super(menuItemName, environments);
            this.children = children;
        }
    }
}
