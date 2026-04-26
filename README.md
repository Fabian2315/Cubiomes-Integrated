# Cubiomes Integrated

Cubiomes Integrated is a Fabric 1.21.1 mod that embeds the C `cubiomes` library and exposes an in-game seed search dashboard.

## Features

- Fabric 1.21.1 mod scaffold targeting Java 21
- JNA bridge to a native `cubiomes_jna` library
- Two-stage seed search pipeline backed by background workers
- Structure, biome, and terrain filters in the dashboard UI
- Generate-and-join flow with a direct-launch probe and a create-world fallback

## Requirements

- Java 21
- CMake 3.16 or newer
- A local Fabric development environment

## Build native library

Build the native library from the project root:

```bash
cmake -S native -B native/build
cmake --build native/build --config Release
```

The expected output is:

- macOS: `native/build/libcubiomes_jna.dylib`
- Linux: `native/build/libcubiomes_jna.so`
- Windows: `native/build/cubiomes_jna.dll`

If you want to point the mod at a locally built binary, set either:

```bash
export CUBIOMES_NATIVE_LIB="$(pwd)/native/build/libcubiomes_jna.dylib"
```

or:

```bash
-Dcubiomes.native.lib=/absolute/path/to/libcubiomes_jna.dylib
```

## Run the dev client

```bash
./gradlew runClient
```

In game, press `O` to open the Cubiomes dashboard.

## Build the mod

```bash
./gradlew build
```

## Notes

- Native generator handles are wrapped in `AutoCloseable` and cleaned up automatically.
- Search cancellation stops background work promptly.
- Terrain verification currently uses a lightweight placeholder so the client stays responsive while the full block-state integration is developed.
