# Tungsten 1.16.1 (TenorClef parity)

Slim bridge-compatible Fabric build targeting Yarn 1.16.1.

```bash
export JAVA_HOME=...  # JDK 21 for Loom
./gradlew remapJar
cp build/libs/tungsten-fabric-*-1.16.1*.jar ../../libs/
```

Package `kaptainwutax.tungsten.*` matches AltoClef `TungstenBridge`.
Slim PathFinder = deterministic direct-walk (full Agent A* port is WIP; see artifacts java-fullport-wip).
