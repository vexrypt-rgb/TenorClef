package adris.altoclef.tasksystem;

import java.util.Objects;

/**
 * Result of consulting {@link RecoveryManager} for a {@link FailureReason}.
 */
public final class RecoveryDecision {

    private final RecoveryAction action;
    private final int attempt;
    private final int maxAttempts;
    private final String note;

    public RecoveryDecision(RecoveryAction action, int attempt, int maxAttempts, String note) {
        this.action = action != null ? action : RecoveryAction.ESCALATE;
        this.attempt = Math.max(0, attempt);
        this.maxAttempts = Math.max(0, maxAttempts);
        this.note = note != null ? note : "";
    }

    public RecoveryAction getAction() {
        return action;
    }

    public int getAttempt() {
        return attempt;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public String getNote() {
        return note;
    }

    /** Soft continue: retry / alternate / wait. */
    public boolean shouldContinue() {
        return action == RecoveryAction.RETRY
                || action == RecoveryAction.ALTERNATE_PATH
                || action == RecoveryAction.ALTERNATE_TARGET
                || action == RecoveryAction.WAIT;
    }

    public boolean isTerminal() {
        return action == RecoveryAction.ABORT || action == RecoveryAction.ESCALATE;
    }

    /** Map to TaskResult for absorb / status. */
    public TaskResult toTaskResult() {
        return switch (action) {
            case RETRY, ALTERNATE_PATH, ALTERNATE_TARGET -> TaskResult.RETRY;
            case WAIT -> TaskResult.BLOCKED;
            case ABORT, ESCALATE -> TaskResult.FAILURE;
        };
    }

    public String enrichMessage(String base) {
        String b = base != null ? base : "";
        String suffix = " [recovery=" + action
                + (maxAttempts > 0 ? " " + attempt + "/" + maxAttempts : "")
                + (note.isEmpty() ? "" : " " + note)
                + "]";
        if (b.contains("[recovery=")) {
            return b;
        }
        return b + suffix;
    }

    @Override
    public String toString() {
        return action + (maxAttempts > 0 ? " " + attempt + "/" + maxAttempts : "")
                + (note.isEmpty() ? "" : " (" + note + ")");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RecoveryDecision that)) return false;
        return attempt == that.attempt
                && maxAttempts == that.maxAttempts
                && action == that.action
                && Objects.equals(note, that.note);
    }

    @Override
    public int hashCode() {
        return Objects.hash(action, attempt, maxAttempts, note);
    }
}
