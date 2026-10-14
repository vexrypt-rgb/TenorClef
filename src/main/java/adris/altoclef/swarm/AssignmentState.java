package adris.altoclef.swarm;

/** Lifecycle of an assignment. State is separate from outcome: only SUCCEEDED means the objective was met. */
public enum AssignmentState {
    UNASSIGNED, ASSIGNED, ACCEPTED, RUNNING, SUCCEEDED, FAILED, CANCELLED;

    public boolean isTerminal() {
        return this == SUCCEEDED || this == FAILED || this == CANCELLED;
    }

    public boolean isHeld() {
        return this == ASSIGNED || this == ACCEPTED || this == RUNNING;
    }
}
