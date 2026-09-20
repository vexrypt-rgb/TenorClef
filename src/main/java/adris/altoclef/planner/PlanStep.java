package adris.altoclef.planner;

import java.util.Objects;

/**
 * One actionable step in a {@link Plan}. Prefers a TaskCatalogue key over
 * inventing new gather logic (Phase 7 hypothesis).
 */
public final class PlanStep {

    private final String catalogueKey;
    private final int count;
    private final String label;
    private PlanStepStatus status;

    public PlanStep(String catalogueKey, int count) {
        this(catalogueKey, count, null);
    }

    public PlanStep(String catalogueKey, int count, String label) {
        if (catalogueKey == null || catalogueKey.isBlank()) {
            throw new IllegalArgumentException("catalogueKey required");
        }
        this.catalogueKey = catalogueKey.trim();
        this.count = Math.max(1, count);
        this.label = (label != null && !label.isBlank())
                ? label
                : ("collect " + this.count + " " + this.catalogueKey);
        this.status = PlanStepStatus.PENDING;
    }

    public String getCatalogueKey() {
        return catalogueKey;
    }

    public int getCount() {
        return count;
    }

    public String getLabel() {
        return label;
    }

    public PlanStepStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStepStatus status) {
        this.status = status != null ? status : PlanStepStatus.PENDING;
    }

    /** Copy with fresh PENDING status (for naive replan rebuilds). */
    public PlanStep copyFresh() {
        return new PlanStep(catalogueKey, count, label);
    }

    @Override
    public String toString() {
        return label + " [" + status + "]";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PlanStep that)) return false;
        return count == that.count
                && Objects.equals(catalogueKey, that.catalogueKey)
                && Objects.equals(label, that.label);
    }

    @Override
    public int hashCode() {
        return Objects.hash(catalogueKey, count, label);
    }
}
