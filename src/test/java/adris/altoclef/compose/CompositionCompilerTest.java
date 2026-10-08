package adris.altoclef.compose;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class CompositionCompilerTest {

    @Test
    void templateCompilesAndInstantiates() throws Exception {
        CompositionCompiler.Result r = CompositionCompiler.compile(CompositionStore.template("Demo"));
        assertTrue(r.ok(), () -> r.diagnostics + " " + r.error);
        assertNotNull(r.type.getDeclaredConstructor().newInstance());
    }

    @Test
    void syntaxErrorGivesLineDiagnostics() {
        String bad = CompositionStore.template("Bad").replace("return Kit", "retrun Kit");
        CompositionCompiler.Result r = CompositionCompiler.compile(bad);
        assertFalse(r.ok());
        assertFalse(r.diagnostics.isEmpty());
        assertTrue(r.diagnostics.get(0).line > 0);
    }

    @Test
    void missingClassIsReported() {
        CompositionCompiler.Result r = CompositionCompiler.compile("class Hidden {}");
        assertFalse(r.ok());
        assertNotNull(r.error);
    }

    @Test
    void recompileAfterEditSeesNewBehaviour() throws Exception {
        String v1 = "import adris.altoclef.*; import adris.altoclef.compose.*; import adris.altoclef.tasksystem.*;\n"
                + "public class Ed implements Composition { public Task build(AltoClef m) { return Kit.waitMs(1); } }";
        String v2 = v1.replace("waitMs(1)", "forever(\"x\")");
        Class<?> a = CompositionCompiler.compile(v1).type;
        Class<?> b = CompositionCompiler.compile(v2).type;
        assertNotSame(a, b, "fresh class loader per compile");
        assertFalse(((Composition) b.getDeclaredConstructor().newInstance()).build(null).isFinished());
    }

    @Test
    void storeRoundTripDuplicateAndSanitize(@TempDir Path tmp) throws Exception {
        CompositionStore s = new CompositionStore(tmp);
        s.save("Alpha", CompositionStore.template("Alpha"));
        assertEquals(java.util.List.of("Alpha"), s.list());
        String copy = s.duplicate("Alpha");
        assertEquals("AlphaCopy", copy);
        assertTrue(s.load(copy).contains("class AlphaCopy"));
        assertTrue(CompositionCompiler.compile(s.load(copy)).ok());
        assertEquals("C1x", CompositionStore.sanitize("1x!"));
        assertEquals("Untitled", CompositionStore.sanitize("  "));
    }
}
