package adris.altoclef.planner;

import java.util.Objects;

/**
 * A condition a {@link Goal} needs satisfied. Phase 7 v1 supports
 * catalogue item counts and opaque predicate ids (checked by the goal).
 */
public final class Requirement {

    public enum Kind {
        /** Need {@link #getCount()} of catalogue key {@link #getId()}. */
        ITEM_COUNT,
        /** Opaque predicate id; satisfaction left to the {@link Goal}. */
        PREDICATE
    }

    private final Kind kind;
    private final String id;
    private final int count;

    private Requirement(Kind kind, String id, int count) {
        this.kind = kind != null ? kind : Kind.ITEM_COUNT;
        this.id = id != null ? id : "";
        this.count = Math.max(0, count);
    }

    public static Requirement item(String catalogueKey, int count) {
        return new Requirement(Kind.ITEM_COUNT, catalogueKey, count);
    }

    public static Requirement predicate(String predicateId) {
        return new Requirement(Kind.PREDICATE, predicateId, 0);
    }

    public Kind getKind() {
        return kind;
    }

    /** Catalogue key or predicate id. */
    public String getId() {
        return id;
    }

    public int getCount() {
        return count;
    }

    @Override
    public String toString() {
        if (kind == Kind.ITEM_COUNT) {
            return "item:" + id + " x" + count;
        }
        return "predicate:" + id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Requirement that)) return false;
        return count == that.count && kind == that.kind && Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(kind, id, count);
    }
}
