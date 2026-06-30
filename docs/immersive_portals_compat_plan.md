# Immersive Portals Compatibility Plan

## Goal

Add an optional compatibility layer for Immersive Portals so Mystcraft Ages remain stable when dynamically created and, when Immersive Portals is installed, valid Mystcraft portal frames can become see-through portals to their resolved Age destinations instead of relying only on vanilla portal collision teleportation.

## Current Mystcraft Touch Points

- Mystcraft currently creates or replaces Age worlds at runtime through `DimensionInjector` and `MinecraftServerMixin`, registering `DimensionOptions`, constructing a `ServerWorld`, adding it to the server world map, firing Fabric world-load events, and syncing the dimension profile to connected players.
- Portal ignition and travel are currently routed through `AgePortalRouting` from `AbstractFireBlockMixin`, `NetherPortalBlockMixin`, and `EndPortalBlockMixin`.
- Immersive Portals compatibility should therefore be isolated behind a small optional service and called from the existing dynamic-dimension creation and portal-routing paths.

## External API Notes

- Immersive Portals is split into `imm_ptl_core` for see-through portal entities and `q_misc_util` for its dimension API; the dimension API supports dynamically adding/removing dimensions without server restart.
- Immersive Portals' Nether portals support non-rectangular and horizontal shapes with a max side length/area limit depending on version, while mirrors are rectangular-only. The implementation should prefer arbitrary frame outlines if the 1.20.1 API exposes the same portal-shape support, and fall back to rectangular bounds only if the API path requires it.

## Architecture

1. **Detection and isolation**
   - Add a `mystcraft.flood.compat.immersiveportal` package.
   - Detect Immersive Portals with Fabric Loader (`isModLoaded("immersive_portals")`, plus defensive checks for `imm_ptl_core`/`q_misc_util` if needed).
   - Keep all direct Immersive Portals class references inside this package so the base mod can run without the dependency.
   - Use a thin facade such as `ImmersivePortalsCompat` with no-op behavior when the mod is absent.

2. **Build configuration**
   - Add Immersive Portals as an optional compile-time dependency (`modCompileOnly` or an equivalent Loom configuration) and add its Maven repository only if needed.
   - Do not add Immersive Portals to `fabric.mod.json.depends`; add it to `suggests` or document it as optional.
   - If the published API artifact is unstable, prefer reflection or small adapter classes loaded only when present.

3. **Dynamic dimension registration handoff**
   - After Mystcraft registers a new Age and constructs the `ServerWorld`, notify the compat facade with the Age id, dimension options/profile, and new world instance.
   - Investigate replacing or supplementing Mystcraft's manual registry mutation with the `q_misc_util` dynamic-dimension API when Immersive Portals is installed, because crash reports during new-dimension creation may be caused by Immersive Portals maintaining its own synchronized dimension registry/cache.
   - Ensure existing Mystcraft `ServerWorldEvents.LOAD` and profile sync behavior remain unchanged.

4. **Portal creation flow**
   - Extend `AgePortalRouting.tryCreateNetherPortal` (or a new shape scanner used by it) to return a structured portal creation result: source world, source frame positions, source plane, destination dimension, destination position, orientation, and whether creation succeeded.
   - If Immersive Portals is installed and the result is valid, create an Immersive Portals portal entity/cluster instead of placing or using a vanilla-style portal block field.
   - For bidirectional travel, create the paired reverse portal when destination chunks and frame/anchor are available; otherwise create a one-way portal only if that behavior is acceptable and clearly configurable.

5. **Shape support strategy**
   - Reuse Mystcraft's existing arbitrary-shape validation as the source of truth.
   - Adapter output should include both:
     - the exact portal interior mask for APIs that support special/non-rectangular portal shapes; and
     - the minimal rectangle bounds and plane axes for APIs that only accept rectangular entities.
   - First milestone: rectangular Immersive Portals portal for any valid Mystcraft shape by using the bounding box and refusing shapes with holes/ambiguous normals if rectangular fallback would be misleading.
   - Second milestone: exact mask/non-rectangular portal if the 1.20.1 Immersive Portals API exposes a supported way to set special shape data.

6. **Lifecycle cleanup**
   - Tag created portal entities with stable Mystcraft metadata (Age id, source frame hash, destination id, owner/link id).
   - Remove or invalidate Immersive Portals entities when their source frame is broken, the linked book/destination becomes invalid, or an Age is reloaded/replaced.
   - On server start/world load, reconcile existing tagged entities with current Mystcraft data to avoid duplicate portal clusters.

7. **Client sync and chunk loading**
   - Ensure the destination Age exists before creating a portal entity and before letting the client see through it.
   - Preload or ticket the destination chunk around the portal destination before spawning the portal entity if Immersive Portals requires the other side to be ready.
   - Reuse Mystcraft's `ModMessages` dimension sync after Age creation so clients know the Age profile before receiving portal-entity data.

8. **Configuration and fallback**
   - Add config options:
     - `enableImmersivePortalsCompat` (default `true` when installed),
     - `immersivePortalShapeMode` (`exact`, `rectangle_fallback`, `disabled`),
     - `createReverseImmersivePortals` (default `true`),
     - `fallbackToVanillaTeleportOnFailure` (default `true`).
   - If any Immersive Portals API call fails, log a clear warning and use existing Mystcraft teleport behavior instead of crashing.

## Implementation Milestones

1. **Research spike**
   - Pin the Immersive Portals version that matches Minecraft 1.20.1.
   - Confirm Maven coordinates, mod ids, API package names, and whether special/non-rectangular shapes are public API.
   - Create a tiny local proof of concept that spawns a rectangle portal between the Overworld and a test Age.

2. **Optional compat scaffold**
   - Add the compile-only dependency and no-op facade.
   - Add runtime detection and logging.
   - Add unit-free smoke checks that the mod still launches without Immersive Portals on the classpath.

3. **Dimension-registration bridge**
   - Notify the compat layer after Age world creation.
   - If required, register Age dimensions through `q_misc_util`'s dimension API when Immersive Portals is installed.
   - Regression-test creating a new Age with and without Immersive Portals installed.

4. **Rectangle portal MVP**
   - Convert valid Mystcraft portal results into Immersive Portals rectangle portal entities.
   - Support orientation and destination dimension/position.
   - Preserve existing portal collision behavior when Immersive Portals is absent or creation fails.

5. **Exact-shape portal support**
   - Add exact non-rectangular shape data if supported by the API.
   - Otherwise document the rectangle fallback limitation and reject unsafe non-rectangular cases.

6. **Lifecycle hardening**
   - Add metadata, cleanup on frame break, reverse portal creation, duplicate prevention, and Age reload cleanup.
   - Add server/client logging around failed portal creation and destination readiness.

7. **Compatibility testing**
   - Test Age creation from a fresh world and an existing save.
   - Test portal creation for rectangle, horizontal, non-rectangular solid, and invalid/hollow shapes.
   - Test missing destination, unloaded destination chunks, Age reload, server restart, and Immersive Portals absent.

## Open Questions

- Which exact Immersive Portals release should be targeted for Minecraft 1.20.1, and does it publish a stable API jar?
- Does the 1.20.1 API expose non-rectangular/special portal shape creation to third-party mods, or is that internal to Nether portal handling?
- Should Mystcraft portals become visible see-through portals immediately on ignition, or only after the destination Age has finished initial generation and sync?
- Should non-rectangular shapes fall back to rectangles, reject with player feedback, or use exact shape only when API support is confirmed?
