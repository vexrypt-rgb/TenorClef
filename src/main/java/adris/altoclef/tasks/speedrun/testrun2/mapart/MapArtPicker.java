package adris.altoclef.tasks.speedrun.testrun2.mapart;

import adris.altoclef.Debug;

import java.awt.FileDialog;
import java.awt.Frame;
import java.nio.file.Path;

/** Desktop file dialog. Falls back to inbox folder if AWT is blocked. */
public final class MapArtPicker {

    private MapArtPicker() {}

    public static void pickAndConvert() {
        MapArtFiles.ensure();
        new Thread(() -> {
            try {
                FileDialog fd = new FileDialog((Frame) null, "Map art image", FileDialog.LOAD);
                fd.setFilenameFilter((dir, name) -> {
                    String n = name.toLowerCase();
                    return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp");
                });
                fd.setVisible(true);
                String file = fd.getFile();
                String dir = fd.getDirectory();
                if (file == null || dir == null) {
                    Debug.logMessage("MAPART cancelled. Drop a PNG in " + MapArtFiles.inbox().toAbsolutePath());
                    return;
                }
                Path src = Path.of(dir, file);
                Path copied = MapArtFiles.copyInbox(src);
                Path out = MapArtConverter.convert(copied != null ? copied : src);
                if (out != null) {
                    Debug.logMessage("MAPART ready " + out.getFileName() + " — @mapart print");
                }
            } catch (Throwable t) {
                Debug.logWarning("MAPART picker " + t.getClass().getSimpleName()
                        + " — drop PNG in " + MapArtFiles.inbox().toAbsolutePath());
            }
        }, "t2-mapart-picker").start();
    }
}
