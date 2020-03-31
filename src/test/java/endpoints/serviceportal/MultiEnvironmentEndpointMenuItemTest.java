package endpoints.serviceportal;

import endpoints.PublishEnvironment;
import endpoints.config.Endpoint;
import endpoints.config.NodeName;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointContentMenuItem;
import endpoints.config.ServicePortalEndpointMenuItem.ServicePortalEndpointMenuFolder;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointLeafMenuItem;
import endpoints.serviceportal.MultiEnvironmentEndpointMenuItem.MultiEnvironmentEndpointMenuFolder;
import junit.framework.TestCase;

import java.util.List;
import java.util.Set;

import static endpoints.PublishEnvironment.live;
import static endpoints.PublishEnvironment.preview;
import static java.util.EnumSet.allOf;

public class MultiEnvironmentEndpointMenuItemTest extends TestCase {
    
    public void testMergeChildren() {
        
        
        /*
         *   Live environment:
         *       1 (folder)
         *          A (item; only on live)
         *          B (item; only on preview)
         *          C (item; on both)
         *          D (item)
         *       2 (folder)
         *          2-child (needs to have an item, to avoid pruning)
         *       
         *   Preview environment:
         *       1 (folder)
         *          A (item; only on live)
         *          B (item; only on preview)
         *          C (item; on both)
         *          E (item)
         *       2 (item; test item/folder merging)
         *       Pruned (folder)
         *          Pruned (item; only on live)
         *       
         *   Expected result:
         *       1 (Folder)
         *          A (only on live)
         *          B (only on preview)
         *          C (on both)
         *          D (only on live)
         *          E (only on preview)
         *       2 (Folder)
         *       2 (Item; only on preview)        
         */
        
        var allEnv = allOf(PublishEnvironment.class);

        var liveA = new ServicePortalEndpointContentMenuItem("A", Set.of(live), new NodeName("a"));
        var liveB = new ServicePortalEndpointContentMenuItem("B", Set.of(preview), new NodeName("b"));
        var liveC = new ServicePortalEndpointContentMenuItem("C", allEnv, new NodeName("live-c"));
        var liveD = new ServicePortalEndpointContentMenuItem("D", allEnv, new NodeName("d"));
        var live1 = new ServicePortalEndpointMenuFolder("1", allEnv, List.of(liveA, liveB, liveC, liveD));
        var live2Child = new ServicePortalEndpointContentMenuItem("2 Child", allEnv, new NodeName("2c"));
        var live2 = new ServicePortalEndpointMenuFolder("2", allEnv, List.of(live2Child));
        var liveRoot = new ServicePortalEndpointMenuFolder("Root", allEnv, List.of(live1, live2));
        
        var preA = new ServicePortalEndpointContentMenuItem("A", Set.of(live), new NodeName("a"));
        var preB = new ServicePortalEndpointContentMenuItem("B", Set.of(preview), new NodeName("b"));
        var preC = new ServicePortalEndpointContentMenuItem("C", allEnv, new NodeName("pre-c"));
        var preD = new ServicePortalEndpointContentMenuItem("E", allEnv, new NodeName("e"));
        var pre1 = new ServicePortalEndpointMenuFolder("1", allEnv, List.of(preA, preB, preC, preD));
        var pre2 = new ServicePortalEndpointContentMenuItem("2", allEnv, new NodeName("2"));
        var preItemPruned = new ServicePortalEndpointContentMenuItem("Pruned", Set.of(live), new NodeName("p"));
        var preFolderPruned = new ServicePortalEndpointMenuFolder("Pruned", allEnv, List.of(preItemPruned));
        var preRoot = new ServicePortalEndpointMenuFolder("Root", allEnv, List.of(pre1, pre2, preFolderPruned));
        
        var result = new MultiEnvironmentEndpointMenuFolder("Root");
        result.mergeChildren(live, liveRoot);
        result.mergeChildren(preview, preRoot);
        result.prune();
        
        assertEquals(3, result.children.size());
        assertEquals("1", result.children.get(0).menuItemName);
        assertEquals("2", result.children.get(1).menuItemName);
        assertEquals("2", result.children.get(2).menuItemName);
        
        var folder = (MultiEnvironmentEndpointMenuFolder) result.children.get(0);
        assertEquals(5, folder.children.size());
        assertEquals("A", folder.children.get(0).menuItemName);
        assertEquals("B", folder.children.get(1).menuItemName);
        assertEquals("C", folder.children.get(2).menuItemName);
        assertEquals("D", folder.children.get(3).menuItemName);
        assertEquals("E", folder.children.get(4).menuItemName);

        assertEquals(Set.of(live),    ((MultiEnvironmentEndpointLeafMenuItem)folder.children.get(0)).itemForEnvironment.keySet());
        assertEquals(Set.of(preview), ((MultiEnvironmentEndpointLeafMenuItem)folder.children.get(1)).itemForEnvironment.keySet());
        assertEquals(allEnv,             ((MultiEnvironmentEndpointLeafMenuItem)folder.children.get(2)).itemForEnvironment.keySet());
        assertEquals(Set.of(live),    ((MultiEnvironmentEndpointLeafMenuItem)folder.children.get(3)).itemForEnvironment.keySet());
        assertEquals(Set.of(preview), ((MultiEnvironmentEndpointLeafMenuItem)folder.children.get(4)).itemForEnvironment.keySet());
        
        assertEquals("a", ((ServicePortalEndpointContentMenuItem) 
            ((MultiEnvironmentEndpointLeafMenuItem)folder.children.get(0)).itemForEnvironment.get(live)).content.name);
        assertEquals("live-c", ((ServicePortalEndpointContentMenuItem)
            ((MultiEnvironmentEndpointLeafMenuItem)folder.children.get(2)).itemForEnvironment.get(live)).content.name);
        assertEquals("pre-c", ((ServicePortalEndpointContentMenuItem)
            ((MultiEnvironmentEndpointLeafMenuItem)folder.children.get(2)).itemForEnvironment.get(preview)).content.name);
    }
}