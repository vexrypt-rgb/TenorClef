package adris.altoclef.tasks.speedrun.testrun2;

import adris.altoclef.tasks.speedrun.testrun2.util.GameFiles;
import adris.altoclef.tasks.speedrun.testrun2.util.RunLog;

import java.util.ArrayDeque;

/**
 * Structured fault line. Chat stays one code; the file has the playbook.
 *
 * Grep {@code altoclef/faults.log} for FAULT.
 */
public final class T2Fault {

    public static final String FILE = "faults.log";
    private static final ArrayDeque<String> LAST = new ArrayDeque<>();
    private static String lastCode = "";
    private static long lastMs;

    private T2Fault() {}

    public static void record(String code, String evidence) {
        long now = System.currentTimeMillis();
        if (code.equals(lastCode) && now - lastMs < 4000) return;
        lastCode = code;
        lastMs = now;
        String line = SpeedrunClock.now() + " " + code + " | " + evidence + " | " + hint(code);
        LAST.addLast(line);
        while (LAST.size() > 12) LAST.removeFirst();
        GameFiles.append(FILE, "FAULT " + line);
        RunLog.line("FAULT " + line);
    }

    public static String recent() {
        if (LAST.isEmpty()) return "(no faults this session)";
        StringBuilder sb = new StringBuilder();
        for (String s : LAST) {
            if (sb.length() > 0) sb.append(" ;; ");
            sb.append(s);
        }
        return sb.toString();
    }

    public static String hint(String code) {
        if (code == null) return "unknown";
        return switch (code) {
            case "E10", "E102", "S102" -> "water still: swim+jump, do not pillar";
            case "E100", "S100" -> "jump-in-place: release jump, turn 90, walk. check cobble reserve";
            case "E98" -> "same XZ: ignore if Y changing (mining). else cancel path";
            case "E104", "S104" -> "GUI open: close screen before pathing";
            case "E105", "S105" -> "lava bucket and no water: get water BEFORE construct, do not swap child";
            case "E103", "S103" -> "starving: eat overlay, do not replace portal child";
            case "E108", "S108" -> "crafting table under feet: walk off, do not jump-place";
            case "E110", "S110" -> "piglin and no gold helm: equip gold or leave range";
            case "E111", "S111" -> "null portal child: parent must keep Construct sticky";
            case "E50" -> "death: @back after respawn; read last_death.txt";
            case "E60", "E119" -> "portal ignored: stand in portal block, do not rebuild";
            case "C210" -> "fell in hole: walk, do not spend reserved cobble";
            case "C211" -> "gather stale 45s: wander then retry catalogue";
            case "E116" -> "hole loop: stop place, surface bail";
            default -> "see T2Codes / paste faults.log";
        };
    }
}
