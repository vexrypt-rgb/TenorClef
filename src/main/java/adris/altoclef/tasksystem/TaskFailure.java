package adris.altoclef.tasksystem;

import java.util.Objects;

/**
 * Structured failure payload attached to a {@link Task}.
 * Failures are data — not just {@code false} / stop.
 */
public final class TaskFailure {

    private final FailureReason reason;
    private final String message;
    private final boolean recoverable;
    private final int retryCount;

    public TaskFailure(FailureReason reason, String message, boolean recoverable) {
        this(reason, message, recoverable, 0);
    }

    public TaskFailure(FailureReason reason, String message, boolean recoverable, int retryCount) {
        this.reason = reason != null ? reason : FailureReason.UNKNOWN;
        this.message = message != null ? message : "";
        this.recoverable = recoverable;
        this.retryCount = Math.max(0, retryCount);
    }

    public FailureReason getReason() {
        return reason;
    }

    public String getMessage() {
        return message;
    }

    public boolean isRecoverable() {
        return recoverable;
    }

    public int getRetryCount() {
        return retryCount;
    }

    public TaskFailure withRetryCount(int count) {
        return new TaskFailure(reason, message, recoverable, count);
    }

    public TaskFailure incrementedRetry() {
        return withRetryCount(retryCount + 1);
    }

    /** Maps recoverable flag to {@link TaskResult#RETRY} vs {@link TaskResult#FAILURE}. */
    public TaskResult toResult() {
        return recoverable ? TaskResult.RETRY : TaskResult.FAILURE;
    }

    @Override
    public String toString() {
        return reason + (message.isEmpty() ? "" : ": " + message)
                + (recoverable ? " (recoverable)" : "")
                + (retryCount > 0 ? " retry=" + retryCount : "");
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TaskFailure that)) return false;
        return recoverable == that.recoverable
                && retryCount == that.retryCount
                && reason == that.reason
                && Objects.equals(message, that.message);
    }

    @Override
    public int hashCode() {
        return Objects.hash(reason, message, recoverable, retryCount);
    }
}
