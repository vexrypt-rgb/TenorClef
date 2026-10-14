package adris.altoclef.swarm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Exclusive claims on named resources (equipment, a crafting station, a rare item, a place).
 * One owner per resource; the owner is an agent id. Deliberately not distributed: the coordinator
 * is the only writer.
 */
public final class ReservationTable {
    public record Claim(String resource, String owner, String purpose) {}

    private final Map<String, Claim> claims = new LinkedHashMap<>();

    /** @return true if the resource is now (or already was) held by owner. */
    public boolean reserve(String resource, String owner, String purpose) {
        Claim existing = claims.get(resource);
        if (existing != null) return existing.owner().equals(owner);
        claims.put(resource, new Claim(resource, owner, purpose));
        return true;
    }

    public boolean release(String resource, String owner) {
        Claim c = claims.get(resource);
        if (c == null || !c.owner().equals(owner)) return false;
        claims.remove(resource);
        return true;
    }

    /** Frees everything an owner holds (agent died or work ended). @return the freed resources. */
    public List<String> releaseAll(String owner) {
        List<String> freed = new ArrayList<>();
        claims.values().removeIf(c -> {
            if (c.owner().equals(owner)) {
                freed.add(c.resource());
                return true;
            }
            return false;
        });
        return freed;
    }

    public String holder(String resource) {
        Claim c = claims.get(resource);
        return c == null ? null : c.owner();
    }

    public List<Claim> all() {
        return new ArrayList<>(claims.values());
    }
}
