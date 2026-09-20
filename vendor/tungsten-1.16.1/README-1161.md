# Tungsten 1.16.1 (TenorClef fullport)

Full physics A\* Fabric build targeting Yarn **1.16.1+build.21** (not the slim direct-walk stub).

```bash
export JAVA_HOME=...  # JDK 21 for Loom
./gradlew remapJar
cp build/libs/tungsten-fabric-*-1.16.1*.jar ../../libs/
```

Package `kaptainwutax.tungsten.*` matches AltoClef `TungstenBridge` (`PATHFINDER.find`, `EXECUTOR`, `FollowEntityTask`, `TungstenMod`, `TungstenModDataContainer`).

1.16.1 API shims live in `kaptainwutax.tungsten.compat.McCompat` and a few vendor-local adaptations (`VoxelWorld`, `AgentShapeContext`, `AccessorEntity`).
