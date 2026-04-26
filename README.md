# Cubiomes Integrated (Fabric 1.21.1)

Cubiomes Integrated embeds the C `cubiomes` library into a Fabric mod and exposes an in-game seed-search dashboard.

## What is implemented

- Fabric 1.21.1 mod scaffold (Java 21)
- JNA bridge (`NativeCubiomes`) to a native `cubiomes_jna` dynamic library
- Two-stage seed search engine (`SeedSearcher`) using `CompletableFuture` + background worker pool
- Hybrid guardrail for Stage 2 terrain filtering with configurable Java-verification budget
- Dashboard screen with structure, biome, and terrain controls
- Generate-and-join flow with direct-launch probe and create-world fallback

## Build native library

Build the native library from the project root:

```bash
cmake -S native -B native/build
cmake --build native/build --config Release
```

On macOS, the output is typically:

- `native/build/libcubiomes_jna.dylib`

Configure the path for JNA if needed:

```bash
export CUBIOMES_NATIVE_LIB="$(pwd)/native/build/libcubiomes_jna.dylib"
```

or pass as JVM property:

```bash
-Dcubiomes.native.lib=/absolute/path/to/libcubiomes_jna.dylib
```

## Run dev client

```bash
./gradlew runClient
```

In-game, press `O` to open the dashboard.

## Memory safety

- Native generator handles are wrapped in `AutoCloseable` and cleaned by `Cleaner`
- Search cancellation stops background work promptly
- Native objects are released when search ends or UI is closed

## Stage 2 terrain note

The terrain verifier class contains a documented hook point for exact `ChunkGenerator` integration and currently runs as a lightweight placeholder to keep the client responsive while developing full block-state simulation.
