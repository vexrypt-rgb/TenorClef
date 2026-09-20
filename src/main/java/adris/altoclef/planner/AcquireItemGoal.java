package adris.altoclef.planner;

import java.util.List;
import java.util.Objects;

/**
 * Demo goal: hold at least {@code count} of catalogue item {@code itemKey}.
 * Satisfaction is inventory-only (no world scan).
 */
public final class AcquireItemGoal implements Goal {

    private final String itemKey;
    private final int count;

    public AcquireItemGoal(String itemKey, int count) {
        if (itemKey == null || itemKey.isBlank()) {
            throw new IllegalArgumentException("itemKey required");
        }
        this.itemKey = itemKey.trim();
        this.count = Math.max(1, count);
    }

    public String getItemKey() {
        return itemKey;
    }

    public int getCount() {
        return count;
    }

    @Override
    public String getId() {
        return "acquire:" + itemKey;
    }

    @Override
    public String getDescription() {
        return "Get " + count + " of " + itemKey;
    }

    @Override
    public List<Requirement> getRequirements() {
        return List.of(Requirement.item(itemKey, count));
    }

    @Override
    public boolean isSatisfied(InventoryView inventory) {
        if (inventory == null) {
            return false;
        }
        return inventory.getCount(itemKey) >= count;
    }

    /** How many more are needed (0 if satisfied). */
    public int remaining(InventoryView inventory) {
        if (inventory == null) {
            return count;
        }
        return Math.max(0, count - inventory.getCount(itemKey));
    }

    @Override
    public String toString() {
        return getDescription();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AcquireItemGoal that)) return false;
        return count == that.count && Objects.equals(itemKey, that.itemKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(itemKey, count);
    }
}
