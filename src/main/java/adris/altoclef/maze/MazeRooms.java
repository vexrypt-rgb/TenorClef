// Ostinato: run101. Rooms furnished this session. Room 3 (hub) gathered polished_diorite and diorite by mining room 1's
// cladding (-51,94,-23) and room 2's (-118,125,-20): the furnisher only protected the room it was building, and the
// scanner blacklist that hid placed cells is cleared on combat and stuck recovery. Every room's box (carved cells plus
// the shell around them) and its planned cells (seal shaft included) stay off limits to gathers and Baritone breaks,
// except the room being furnished right now.
package adris.altoclef.maze;

import adris.altoclef.trackers.BlockScanner;
import net.minecraft.util.math.BlockPos;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

public final class MazeRooms {

    private record Room(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, Set<BlockPos> cells) {
        boolean contains(BlockPos p) {
            return (p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ)
                    || cells.contains(p);
        }
    }

    private static final List<Room> ROOMS = new CopyOnWriteArrayList<>();
    private static volatile Room active;

    private MazeRooms() {}

    /** The room being furnished: registered for later, and left open to its own furnisher until {@link #end()}. */
    public static void begin(Set<BlockPos> airCells, Set<BlockPos> plannedCells) {
        Set<BlockPos> box = airCells.isEmpty() ? plannedCells : airCells;
        if (box.isEmpty()) return;
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (BlockPos p : box) {
            minX = Math.min(minX, p.getX()); minY = Math.min(minY, p.getY()); minZ = Math.min(minZ, p.getZ());
            maxX = Math.max(maxX, p.getX()); maxY = Math.max(maxY, p.getY()); maxZ = Math.max(maxZ, p.getZ());
        }
        int g = airCells.isEmpty() ? 0 : 1; // the shell is one block outside the carved cells
        Room room = new Room(minX - g, minY - g, minZ - g, maxX + g, maxY + g, maxZ + g, Set.copyOf(plannedCells));
        Room same = null;
        for (Room r : ROOMS) if (r.minX == room.minX && r.minY == room.minY && r.minZ == room.minZ && r.maxX == room.maxX && r.maxY == room.maxY && r.maxZ == room.maxZ) same = r;
        if (same == null) {
            ROOMS.add(room);
            same = room;
            System.out.println("MAZETEST FURNISH rooms-protected " + ROOMS.size() + " new=" + room.minX + "," + room.minY + "," + room.minZ
                    + ".." + room.maxX + "," + room.maxY + "," + room.maxZ);
        }
        active = same;
        BlockScanner.protectedCells = MazeRooms::isProtected;
    }

    public static void end() {
        active = null;
    }

    /** Inside a room other than the one being furnished (and not one of that room's own cells). */
    public static boolean isProtected(BlockPos p) {
        Room a = active;
        if (a != null && a.contains(p)) return false;
        for (Room r : ROOMS) if (r != a && r.contains(p)) return true;
        return false;
    }
}
