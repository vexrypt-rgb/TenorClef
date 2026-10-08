package adris.altoclef.compose;

import javax.tools.*;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.*;

/**
 * In-memory javac for Composition sources. Nothing touches disk and each compile gets a fresh class loader,
 * so edited sources take effect on the next Perform without restarting the game.
 */
public final class CompositionCompiler {
    private CompositionCompiler() {}

    public static final class Diagnostic {
        public final long line;
        public final long column;
        public final String message;

        Diagnostic(long line, long column, String message) {
            this.line = line;
            this.column = column;
            this.message = message;
        }

        @Override
        public String toString() {
            return (line > 0 ? "line " + line + ":" + column + " " : "") + message;
        }
    }

    public static final class Result {
        public final Class<? extends Composition> type;
        public final List<Diagnostic> diagnostics;
        public final String error;

        Result(Class<? extends Composition> type, List<Diagnostic> diagnostics, String error) {
            this.type = type;
            this.diagnostics = diagnostics;
            this.error = error;
        }

        public boolean ok() {
            return type != null;
        }
    }

    private static final class Source extends SimpleJavaFileObject {
        private final String code;

        Source(String className, String code) {
            super(URI.create("string:///" + className.replace('.', '/') + Kind.SOURCE.extension), Kind.SOURCE);
            this.code = code;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return code;
        }
    }

    private static final class Output extends SimpleJavaFileObject {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        Output(String className) {
            super(URI.create("mem:///" + className.replace('.', '/') + Kind.CLASS.extension), Kind.CLASS);
        }

        @Override
        public OutputStream openOutputStream() {
            return bytes;
        }
    }

    private static final class MemoryLoader extends ClassLoader {
        private final Map<String, Output> classes;

        MemoryLoader(ClassLoader parent, Map<String, Output> classes) {
            super(parent);
            this.classes = classes;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            Output o = classes.get(name);
            if (o == null) throw new ClassNotFoundException(name);
            byte[] b = o.bytes.toByteArray();
            return defineClass(name, b, 0, b.length);
        }
    }

    /** Public class name declared in the source, or null. */
    static String declaredClassName(String source) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(?m)^\\s*public\\s+(?:final\\s+)?class\\s+([A-Za-z_$][\\w$]*)").matcher(source);
        return m.find() ? m.group(1) : null;
    }

    @SuppressWarnings("unchecked")
    public static Result compile(String source) {
        String simple = declaredClassName(source);
        if (simple == null) {
            return new Result(null, List.of(),
                    "No 'public class X implements Composition' found in the source.");
        }
        String pkg = "";
        java.util.regex.Matcher pm = java.util.regex.Pattern
                .compile("(?m)^\\s*package\\s+([\\w.]+)\\s*;").matcher(source);
        if (pm.find()) pkg = pm.group(1) + ".";
        String fqn = pkg + simple;

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) {
            return new Result(null, List.of(), "No system Java compiler in this runtime (jdk.compiler missing).");
        }
        DiagnosticCollector<JavaFileObject> diags = new DiagnosticCollector<>();
        Map<String, Output> out = new HashMap<>();
        StandardJavaFileManager std = javac.getStandardFileManager(diags, null, null);
        JavaFileManager fm = new ForwardingJavaFileManager<>(std) {
            @Override
            public JavaFileObject getJavaFileForOutput(Location loc, String className, JavaFileObject.Kind kind,
                                                       FileObject sibling) {
                Output o = new Output(className);
                out.put(className, o);
                return o;
            }
        };
        List<String> opts = new ArrayList<>(List.of("-proc:none", "-nowarn", "-g",
                "-classpath", System.getProperty("java.class.path", "")));
        try {
            boolean ok = javac.getTask(null, fm, diags, opts, null, List.of(new Source(fqn, source))).call();
            List<Diagnostic> list = new ArrayList<>();
            for (javax.tools.Diagnostic<? extends JavaFileObject> d : diags.getDiagnostics()) {
                if (d.getKind() == javax.tools.Diagnostic.Kind.ERROR) {
                    list.add(new Diagnostic(d.getLineNumber(), d.getColumnNumber(), d.getMessage(Locale.ROOT)));
                }
            }
            if (!ok || !list.isEmpty()) return new Result(null, list, list.isEmpty() ? "compilation failed" : null);
            ClassLoader parent = CompositionCompiler.class.getClassLoader();
            Class<?> c = new MemoryLoader(parent, out).loadClass(fqn);
            if (!Composition.class.isAssignableFrom(c)) {
                return new Result(null, List.of(), simple + " does not implement adris.altoclef.compose.Composition");
            }
            return new Result((Class<? extends Composition>) c, list, null);
        } catch (Throwable t) {
            return new Result(null, List.of(), "compiler error: " + t);
        } finally {
            try {
                fm.close();
            } catch (Exception ignored) {
            }
        }
    }
}
