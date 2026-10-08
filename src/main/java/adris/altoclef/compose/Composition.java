package adris.altoclef.compose;

import adris.altoclef.AltoClef;
import adris.altoclef.tasksystem.Task;

/**
 * Contract for a user-written Composition. The source file defines one public class implementing this
 * interface; {@link #build} returns the ordinary TenorClef {@link Task} to perform. Everything after that is
 * the normal task path ({@code mod.runUserTask}); a Composition only decides WHAT to run.
 */
public interface Composition {
    Task build(AltoClef mod) throws Exception;
}
