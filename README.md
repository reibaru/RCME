# RCME – Render Chunk Mesh Engine for Minecraft Forge 1.20.1

RCME is an experimental rendering optimization mod for
Minecraft Forge 1.20.1.  
It hooks into `RenderChunk`, `RebuildTask`, and `VertexBuffer` to cache GPU-ready
chunk meshes and skip redundant rebuilds, aiming to reduce rendering overhead.

This project is currently under active development.

---

##  Features (Planned / In Progress)

- Cache compiled chunk meshes per `RenderType`
- Skip unnecessary chunk rebuilds when camera movement is small, since cached meshes
  are reused continuously after entering the world
- Diff-based partial chunk rebuilds
- GPU-side mesh upload via custom `VertexBuffer` hooks
- Integration with Forge’s rendering pipeline (1.20.1)

---

##  Technical Overview

RCME injects into the following classes:

- `ChunkRenderDispatcher.RenderChunk.RebuildTask`
- `RenderChunk`
- `RenderChunkStorage`
- `VertexBuffer`
- `LevelRenderer`

The mod uses Mixins to intercept chunk rebuilds, extract mesh data, and store it
on disk for reuse.

---

##  Current Issue (Help Wanted)

On Forge **1.20.1 (47.x)**, none of the Mixins in `blockcache.mixins.json` are being applied:

- No `@Inject` or `@Redirect` fires  
- MixinTrace shows no apply logs for any RCME mixins  
- `blockcache/cache` directory is never created  
- Chunk rebuild hooks (`RebuildTaskMixin`) never run  

### Already verified:

- `mods.toml` contains a correct `[[mixins]]` section  
- `blockcache.mixins.json` is located at the **root of the JAR**  
- `package` matches the actual folder structure  
- Accessor signatures match 1.20.1 vanilla classes  
- `defaultRequire = 0`  
- `refmap` removed (Forge does not require it)  
- No crashes occur — Mixins are silently ignored  

If you have experience with Forge’s Mixin loader on 1.20.1,  
**any insight or suggestions would be greatly appreciated.**

---

##  Project Structure

src/main/java/com/reibaru/blockcache/
  ├── api/
  ├── client/
  ├── render/
  └── mixin/
      ├── accessor/
      └── (various mixins)
