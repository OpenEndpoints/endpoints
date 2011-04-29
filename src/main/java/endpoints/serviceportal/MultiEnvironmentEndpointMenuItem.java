package endpoints.serviceportal;

import endpoints.PublishEnvironment;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointLeafMenuItem;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointMenuFolder;
import lombok.EqualsAndHashCode;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@EqualsAndHashCode
public abstract class MultiEnvironmentEndpointMenuItem implements Serializable {
    
    public final @Nonnull String menuItemName;

    public MultiEnvironmentEndpointMenuItem(@Nonnull String menuItemName) {
        this.menuItemName = menuItemName;
    }

    @EqualsAndHashCode(callSuper = true)
    public static class MultiEnvironmentEndpointLeafMenuItem extends MultiEnvironmentEndpointMenuItem {
        public final @Nonnull Map<PublishEnvironment, ServicePortalEndpointLeafMenuItem> itemForEnvironment 
            = new EnumMap<>(PublishEnvironment.class);

        public MultiEnvironmentEndpointLeafMenuItem(@Nonnull String menuItemName) {
            super(menuItemName);
        }

        @Override
        public boolean contains(@CheckForNull MultiEnvironmentEndpointLeafMenuItem item) {
            return equals(item);
        }
        
        public @Nonnull PublishEnvironment getDefaultEnvironment() {
            for (var e : PublishEnvironment.values()) if (itemForEnvironment.containsKey(e)) return e;
            throw new RuntimeException("No environments supported");
        }
    }

    @EqualsAndHashCode(callSuper = true)
    public static class MultiEnvironmentEndpointMenuFolder extends MultiEnvironmentEndpointMenuItem {
        public final @Nonnull List<MultiEnvironmentEndpointMenuItem> children = new ArrayList<>();

        public MultiEnvironmentEndpointMenuFolder(@Nonnull String menuItemName) {
            super(menuItemName);
        }
        
        public void mergeChildren(@Nonnull PublishEnvironment environment, @Nonnull ServicePortalEndpointMenuFolder sourceFolder) {
            for (var sourceChild : sourceFolder.children) {
                var ourChildrenWithThisName = children.stream().filter(c -> c.menuItemName.equals(sourceChild.menuItemName));

                if (sourceChild instanceof ServicePortalEndpointMenuFolder) {
                    var sourceFolderChild = (ServicePortalEndpointMenuFolder) sourceChild;
                    var existingChild = ourChildrenWithThisName
                        .filter(c -> c instanceof MultiEnvironmentEndpointMenuFolder)
                        .map(c -> (MultiEnvironmentEndpointMenuFolder) c)
                        .findAny();
                    
                    final MultiEnvironmentEndpointMenuFolder ourChild;
                    if (existingChild.isPresent()) {
                        ourChild = existingChild.get();
                    }
                    else { 
                        ourChild = new MultiEnvironmentEndpointMenuFolder(sourceChild.menuItemName); 
                        children.add(ourChild);
                    }
                    
                    ourChild.mergeChildren(environment, sourceFolderChild);
                } 
                else if (sourceChild instanceof ServicePortalEndpointLeafMenuItem) {
                    var sourceLeafChild = (ServicePortalEndpointLeafMenuItem) sourceChild;
                    var existingChild = ourChildrenWithThisName
                        .filter(c -> c instanceof MultiEnvironmentEndpointLeafMenuItem)
                        .map(c -> (MultiEnvironmentEndpointLeafMenuItem) c)
                        .findAny();

                    final MultiEnvironmentEndpointLeafMenuItem ourChild;
                    if (existingChild.isPresent()) {
                        ourChild = existingChild.get();
                    }
                    else {
                        ourChild = new MultiEnvironmentEndpointLeafMenuItem(sourceChild.menuItemName);
                        children.add(ourChild);
                    }

                    if (sourceChild.environments.contains(environment))
                        ourChild.itemForEnvironment.put(environment, sourceLeafChild);
                } 
                else {
                    throw new RuntimeException(sourceChild.getClass().getName());
                }
            }
        }
        
        public void prune() {
            children.removeIf(e -> {
               if (e instanceof MultiEnvironmentEndpointMenuFolder) {
                   ((MultiEnvironmentEndpointMenuFolder) e).prune();
                   return ((MultiEnvironmentEndpointMenuFolder) e).children.isEmpty();
               }
               else if (e instanceof MultiEnvironmentEndpointLeafMenuItem) {
                   return ((MultiEnvironmentEndpointLeafMenuItem) e).itemForEnvironment.isEmpty();
               }
               else throw new RuntimeException(e.getClass().getName());
            });
        }
        
        @Override
        public boolean contains(@CheckForNull MultiEnvironmentEndpointLeafMenuItem item) {
            return children.stream().anyMatch(i -> i.contains(item));
        }
    }
    public abstract boolean contains(@CheckForNull MultiEnvironmentEndpointLeafMenuItem item);
}
