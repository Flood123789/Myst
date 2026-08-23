# Mystcraft Reforged

Mystcraft Reforged is a Fabric 1.20.1 mod that rebuilds the core Mystcraft loop: collect symbol pages, bind them into Descriptive Books, generate authored dimensions ("Ages"), and travel between those Ages with books and crystal portals. Authored choices also determine an Age's terrain, biomes, sky, time, weather, physics, structures, and instability.

This README is primarily a code map. Start with the flows below when you need to answer "what points to this?" or "what does this part do?"

## Mod outline

### The shortest useful mental model

```text
symbol pages
    -> workstation inventories / book NBT
    -> AgeCompiler (interprets page grammar)
    -> AgeProfileManager (creates and persists the AgeProfile)
    -> AgeBuilder (chooses biome source + chunk generator)
    -> DimensionInjector mixin (adds the runtime ServerWorld)
    -> server tick managers (time, weather, instability, decay)
    -> ModMessages -> client caches -> custom sky/colors/rendering
```

An **AgeProfile** is the central data object. Nearly every Age system either writes it, reads it, persists it, or sends a subset of it to the client.

### Main entry points

| Entry point | Called by | Points to / responsibility |
| --- | --- | --- |
| `MystcraftReforged.onInitialize()` | Fabric on both client and dedicated server | Loads config; registers symbols, items, recipes, blocks, entities, screens, packets, commands, worldgen, and server lifecycle callbacks. |
| `MystcraftReforgedClient.onInitializeClient()` | Fabric on the game client | Registers screens, renderers, color providers, client packets, sky effects, and client-only mixin support. |
| `MystcraftReforgedDataGenerator` | Fabric data generation | Hook for generated resources. Most current resources are hand-written under `src/main/resources`. |
| `fabric.mod.json` | Fabric Loader | Declares the entry points, dependencies, mixin lists, mod id, and metadata. |
| `mystcraft-reforged.mixins.json` | Mixin | Declares server/common injections used for dynamic dimensions, portals, spawn behavior, time, weather, and decay. |
| `mystcraft-reforged.client.mixins.json` | Mixin on the client only | Declares rendering, color, sound, particle, gravity, and weather injections. |

### Core Age creation flow

1. A `DescriptiveBookItem` contains a name and ordered symbol ids in item NBT. Workstation screen handlers build and modify that data.
2. Activating an unlinked Descriptive Book resolves a safe, unique dimension id and calls the `DimensionInjector` bridge implemented by `MinecraftServerMixin`.
3. `AgeCompiler.compile()` reads symbols in order. Modifiers such as colors are held until a compatible target consumes them; conflicting or unused pages add instability.
4. `AgeProfileManager.getOrGenerateProfile()` converts the compiler output into an `AgeProfile`, fills unspecified choices deterministically from the Age seed, and saves it as JSON in the world save.
5. `AgeBuilder.buildGenerator()` maps that profile to a biome source and chunk generator. Special terrain types select custom generators; ordinary types rebuild or tune vanilla noise generation.
6. The server mixin creates the runtime dimension and exposes it as a `ServerWorld`.
7. The book teleports the player only after the destination chunk and safe entry location are ready. `PlayerSpawnMemory` records per-Age respawn information.

Important rule: symbol order is meaningful. A color page before `color_sky` modifies the sky; an unconsumed color is a grammar error and increases instability. When exclusive pages conflict, the last matching page wins and the conflict is scored.

### Travel and dimension relationships

| Starting point | Coordinator | Destination / result |
| --- | --- | --- |
| Descriptive Book | `DescriptiveBookItem` | Creates or opens its authored Age, then enters at a safe surface anchor. |
| Linking Book | `LinkingBookItem` + `LinkingBookTarget` | Binds to a world/position, then returns to that exact target when activated. |
| Crystal portal | `CrystalPortalBlock` + `AgePortalRouting` | Reads a book from a receptacle, finds the target, builds a safe portal exit, and transfers entities. |
| Nether portal inside an Age | `NetherPortalBlockMixin` + `AgeSubdimensionManager` | Routes to that Age's derived Nether realm instead of the vanilla Nether. |
| End portal inside an Age | `EndPortalBlockMixin` + `AgeSubdimensionManager` | Routes to that Age's derived End realm and back to its root realm. |
| Respawn inside an Age | player mixins + `PlayerSpawnMemory` | Restores the player's remembered spawn for the same Age family. |

An Age family uses ids derived from one root id:

```text
mystcraft-reforged:<age>              root / Overworld role
mystcraft-reforged:<age>__nether      derived Nether role
mystcraft-reforged:<age>__end         derived End role
```

The derived realms share the root Age's authored state. They must not independently advance time, weather, death, or stability because doing so would make the family drift out of sync.

### Runtime data flow

| Data | Owner (source of truth) | Consumers |
| --- | --- | --- |
| `AgeProfile` | Server: `AgeProfileManager` and JSON files in the save | `AgeBuilder`, lifecycle, weather, instability, travel, spawn rules, packet serialization. |
| Visible Age colors/effects | Server profile, copied by `ModMessages` | `ClientAgeCache`, biome color mixins, fog renderer, `CustomSkyPainter`, particles. |
| Visible Age time | Server profile and root-Age tick | `ClientAgeTimeCache`, world/day-night mixins, sky renderer. |
| Book target and symbols | ItemStack NBT | book items, workstation handlers/screens, block entities/renderers, preview UI. |
| Player spawn memory | Player persistent NBT through `PlayerSpawnMemoryAccess` | respawn callbacks and post-teleport restoration. |
| Balance settings | `config/mystcraft-reforged.json`, loaded by `MystcraftConfig` | instability chances, world generation tuning, and page/loot pools. |

The server is authoritative. Client caches exist only so rendering code can answer frequent questions without accessing server-only classes.

### Subsystem map

| Folder / class | What it does | Usually points to |
| --- | --- | --- |
| `generation/profile/AgeProfile.kt` | Serializable schema for an Age: terrain, biome choices, colors, time, weather, state, curses, physics, stability, spawn and tuning. | Read by almost every server Age subsystem and serialized for persistence/sync. |
| `generation/profile/AgeProfileManager.kt` | Cache, deterministic generation, migration, load/save/unload, and root/derived profile inheritance. | `AgeCompiler`, `AgeSubdimensionManager`, world-save filesystem. |
| `generation/AgeCompiler.kt` | Parses ordered symbol ids into an intermediate authored result and calculates grammar-conflict instability. | `ModSymbols`, `AgeProfileManager`. |
| `generation/AgeBuilder.kt` | Turns an `AgeProfile` into a biome source and chunk generator. | Vanilla generators plus `BlankAgeChunkGenerator`, `BiosphereChunkGenerator`, and `LostCityChunkGenerator`. |
| `generation/AgeTerrainTuning.kt` | Converts editor values into generator-friendly scales and flags. | `AgeBuilder` and generator settings. |
| `generation/AgeFeatureTuning.kt` | Decides whether/how strongly optional placed features appear for a profile. | Individual `Feature` implementations. |
| `generation/AgeSubdimensionManager.kt` | Names, creates, finds, and synchronizes root/Nether/End members of an Age family. | portal mixins, lifecycle, profile manager. |
| `generation/AgeLifecycleManager.kt` | Sacrifice/death behavior, entry checks, exile, family synchronization, and dimension cleanup. | books, commands, ticks, profile manager. |
| `generation/AgePortalRouting.kt` | Common portal target lookup, coordinate scaling, safe exit placement, and entity transfer. | crystal, Nether, and End portal paths. |
| `generation/AgeTravelSafety.kt` | Safe spawn/exit search and fallback platform creation. | books and portal routing. |
| `generation/AgeWeatherController.kt` | Advances authored or natural weather stored in the profile. | main world tick and network sync. |
| `generation/instability/*` | Converts stability score into escalating player/world effects. | `AgeProfile.stability`, `DecayManager`, balance config. |
| `block/DecayManager.kt` | Global per-tick work budget for spreading decay. | black and white decay block logic. |
| `generation/*Feature.kt` | Places a specific landmark, resource, ambient event, or terrain decoration. | `ModFeatures` and configured/placed-feature JSON. |
| `generation/LostCity*` | Loads bundled Lost Cities assets, lays them out, and generates bounded city terrain. | city terrain selection in `AgeBuilder`. |
| `registry/*` | Registers runtime ids for symbols, features, codecs, sounds, and loot modifications. | called once from the common initializer. |
| `item/*` | Book/page behavior and the NBT formats that connect gameplay objects to Age data. | workstation handlers, travel, previews. |
| `block/*` | Workstations, displayed books, crystal portals, crystal painting, fissures, and decay blocks. | paired block entities and screen handlers/renderers. |
| `block/entity/*` | Persistent inventory/state for blocks that need more than a block state can store. | server screens and client renderers. |
| `gui/*` | Server-side inventories and validation for workstation containers. | matching classes under `client/gui`. |
| `client/gui/*` | Draws workstation, notebook, and book screens. | screen-handler state and packet requests. |
| `network/ModMessages.kt` | Registers client-to-server actions and sends profile/time state to players. | `client/network/ClientMessages.kt`. |
| `client/cache/*` | Small client-side snapshots of current Age state. | renderers and client mixins. |
| `client/render/*` | Books, block entities, sky, celestial objects, fog/colors, and ambient particles. | client cache data. |
| `mixin/*` | Narrow hooks where Fabric events cannot replace vanilla dimension, portal, spawn, time, weather, or generation behavior. | delegates to named Kotlin managers whenever possible. |
| `compat/*` | Optional integration that is safe when the other mod is absent. | Patchouli guide and Distant Horizons invalidation. |

### Blocks, items, and workstations

| Gameplay object | Main implementation | Purpose |
| --- | --- | --- |
| Symbol Page / Lost Page | `SymbolPageItem`, `LostPageItem` | Carries one authored symbol; lost pages reveal unidentified symbols. |
| Notebook | `NotebookItem`, `NotebookScreenHandler` | Stores and organizes discovered symbol pages. |
| Descriptive Book | `DescriptiveBookItem` | Stores an ordered description and points to the Age generated from it. |
| Linking Book | `LinkingBookItem` | Stores an existing dimension and exact return position. |
| Writing Desk | `WritingDeskBlock` + handler | Inspects and manages pages/books. |
| Editing Table | `EditingTableBlock` + handler | Edits book metadata and terrain tuning values. |
| Printing Table | `PrintingTableBlock` + handler | Copies known pages using ink and paper inputs. |
| Book Binder | `BookBinderBlock` + handler | Converts an ordered page sequence into a Descriptive Book. |
| Book Stand / Receptacle | paired block + block entity | Displays books; the receptacle supplies a target to crystal portals. |
| Crystal blocks / portal | `PaintableCrystalBlock`, `CrystalPortalBlock` | Builds colored, book-targeted interdimensional portals. |
| Star Fissure | block, block entity, feature | A generated Age landmark and travel-related world feature. |
| Black / White Decay | decay block implementations | Spreading instability damage, globally throttled by `DecayManager`. |

### World generation features

`ModFeatures` is the runtime registry. Each feature also needs matching files under:

```text
src/main/resources/data/mystcraft-reforged/worldgen/configured_feature/
src/main/resources/data/mystcraft-reforged/worldgen/placed_feature/
```

Feature families currently include:

- resources and landmarks: dense ores, archives, giant trees, crystal formations, obelisks, floating castles, ruins, observatories, aqueducts, gateways, ancient remains, and fissures;
- terrain/theme features: city grids, biospheres, exotic surfaces, tendrils, and sky spheres;
- ambient/instability features: page storms, memory blooms, sanctuaries, meteor showers, and cave glow lichen.

Adding a feature normally touches four places: its `Feature` class, `ModFeatures`, configured-feature JSON, and placed-feature JSON. If it should appear in every eligible Age, also add the placed feature to the correct generation step in `MystcraftReforged.onInitialize()`.

### Resources and data files

| Path | Purpose |
| --- | --- |
| `assets/mystcraft-reforged/` | client assets: language strings, textures, models, blockstates, sounds, GUI art, and the Patchouli guide. |
| `data/mystcraft-reforged/recipes/` | crafting recipes; special NBT-aware recipes also have Kotlin serializer classes. |
| `data/mystcraft-reforged/worldgen/` | configured and placed feature definitions. |
| `data/mystcraft-reforged/loot_tables/` | mod-owned loot; `ModLoot` also modifies selected vanilla tables. |
| `data/lostcities/` | bundled third-party-compatible building, palette, part, style, and world-style definitions used by city Ages. |
| `assets/mystcraft-reforged/patchouli_books/` | in-game player documentation. It explains gameplay; this README explains implementation. |

### Mixins: why they exist

Mixins should stay thin: capture a vanilla event or expose otherwise inaccessible state, then delegate to a named Kotlin subsystem.

| Mixin group | Reason |
| --- | --- |
| server and player mixins | Create dynamic worlds, keep Age spawns, and survive world changes/respawns. |
| portal mixins | Replace vanilla Nether/End routing while the player is inside an Age family. |
| world time/weather mixins | Show the authored values instead of the overworld's global values. |
| chunk/spawn/fire/item mixins | Apply profile-specific generation, mob, decay, gravity, or safety behavior. |
| client render/color mixins | Substitute the synced Age sky, fog, water, foliage, fire/lava, weather, and particles. |

When debugging a mixin, start at its injected method, then follow the first call into `generation`, `player`, `client/cache`, or `client/render`. The manager is intended to contain the actual policy.

## Developer setup

Requirements: Java 17 and the Gradle wrapper included in this repository.

```powershell
# Compile, run tests, and produce the remapped mod jar
.\gradlew.bat build

# Launch a development client
.\gradlew.bat runClient

# Launch a development server
.\gradlew.bat runServer
```

Useful verification commands:

```powershell
.\gradlew.bat test
.\gradlew.bat compileKotlin compileJava compileClientKotlin compileClientJava
```

Build output is written under `build/`; the distributable jar is under `build/libs/`.

## Reading and commenting conventions

- KDoc above a class or object explains its role, ownership, and important collaborators.
- Comments inside a function explain ordering constraints, invariants, compatibility workarounds, or surprising game-engine behavior.
- Straightforward registrations and property assignments are intentionally left self-explanatory; comments that merely repeat the code go stale quickly.
- `AgeProfileManager` is the persistence boundary, `AgeBuilder` is the world-construction boundary, `ModMessages` is the server/client boundary, and mixins are the vanilla-code boundary.
- If behavior crosses one of those boundaries, document both why it crosses and which side is authoritative.

## License and bundled assets

The mod source is licensed under the repository's [LICENSE](LICENSE). Bundled Lost Cities resources retain their separate notice in `src/main/resources/META-INF/lostcities-LICENSE.txt`.
