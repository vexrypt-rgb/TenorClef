package adris.altoclef.fugue;

import adris.altoclef.tasksystem.FailureReason;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A netted dependency graph: what is still missing for the goal after the current inventory has been allocated
 * once across every consumer. Empty = the goal's requirements are all held. Immutable once built.
 */
public final class FuguePlan {

    private final Map<String, FugueNode> nodes;
    private final FailureReason error;
    private final String errorMessage;

    FuguePlan(Map<String, FugueNode> nodes, FailureReason error, String errorMessage) {
        this.nodes = Collections.unmodifiableMap(new LinkedHashMap<>(nodes));
        this.error = error;
        this.errorMessage = errorMessage;
    }

    static FuguePlan error(FailureReason reason, String message) {
        return new FuguePlan(Map.of(), reason, message);
    }

    public boolean isError() { return error != null; }
    public FailureReason getError() { return error; }
    public String getErrorMessage() { return errorMessage; }
    public boolean isEmpty() { return nodes.isEmpty(); }
    public Map<String, FugueNode> getNodes() { return nodes; }
    public FugueNode get(String key) { return nodes.get(key); }

    /** Nodes none of whose dependencies are still in the plan, in plan order. */
    public List<FugueNode> ready() {
        List<FugueNode> out = new ArrayList<>();
        for (FugueNode n : nodes.values()) {
            boolean ok = true;
            for (String d : n.deps) {
                if (nodes.containsKey(d)) { ok = false; break; }
            }
            if (ok) out.add(n);
        }
        return out;
    }

    /** "key x need" for every node, in plan order, for logs. */
    public String summary() {
        if (isError()) return "ERROR " + error + ": " + errorMessage;
        StringBuilder sb = new StringBuilder();
        for (FugueNode n : nodes.values()) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(n.getKey()).append('x').append(n.need);
        }
        return sb.length() == 0 ? "(nothing missing)" : sb.toString();
    }
}
