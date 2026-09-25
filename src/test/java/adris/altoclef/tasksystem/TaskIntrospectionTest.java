package adris.altoclef.tasksystem;

import adris.altoclef.tasksystem.TaskIntrospection.ChainView;
import adris.altoclef.tasksystem.TaskPropagationTest.FakeTask;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

public class TaskIntrospectionTest {

    @Test
    void treeExposesHierarchyInOrder() {
        FakeTask a = new FakeTask("A"), b = new FakeTask("B"), c = new FakeTask("C");
        List<String> t = TaskIntrospection.tree(new ChainView("User Tasks", 50, true, List.of(a, b, c)), 0);
        Assertions.assertEquals(4, t.size());
        Assertions.assertTrue(t.get(1).startsWith("└─ "));
        Assertions.assertTrue(t.get(2).startsWith("  └─ "));
        Assertions.assertTrue(t.get(3).startsWith("    └─ "));
    }

    @Test
    void whyReportsPreemptionByHigherChain() {
        FakeTask goal = new FakeTask("goal");
        FakeTask run = new FakeTask("runaway");
        ChainView user = new ChainView("User Tasks", 50, true, List.of(goal));
        ChainView mob = new ChainView("Mob Defense", 70, true, List.of(run));
        String out = String.join("\n", TaskIntrospection.why(mob, user, 0));
        Assertions.assertTrue(out.contains("PAUSED"), out);
        Assertions.assertTrue(out.contains("Mob Defense"), out);
    }

    @Test
    void whyNamesDeepestBlockingFailure() {
        FakeTask root = new FakeTask("root"), leaf = new FakeTask("leaf");
        leaf.fail(FailureReason.NO_PATH, "iron unreachable", true);
        ChainView user = new ChainView("User Tasks", 50, true, List.of(root, leaf));
        String out = String.join("\n", TaskIntrospection.why(user, user, 0));
        Assertions.assertTrue(out.contains("in control"), out);
        Assertions.assertTrue(out.contains("NO_PATH") && out.contains("iron unreachable"), out);
    }
}
