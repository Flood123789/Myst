# Paradox Reaper — implementation notes

Handoff document. The Reaper is a six-legged, procedurally animated hunter that clings to any
surface. Almost everything here was arrived at by fixing something that looked wrong in-game, so
the reasoning matters more than the values. **The constants are tuned, not derived — but the
structure is load-bearing, and several pieces look wrong until you know what they prevent.**

## Where things live

| File | Sourceset | Role |
|---|---|---|
| `entity/ParadoxReaperEntity.kt` | main | Locomotion, cling state, senses, summoning, jumping |
| `entity/SurfaceCling.kt` | main | Adhesion maths: support probes, plane projection, rotation |
| `entity/FabrikChain.kt` | main | Multi-joint IK solver |
| `entity/ReaperAwareness.kt` | main | Dormant → pursuit → hunting → settling state machine |
| `entity/ReaperSpeed.kt` | main | Pursuit speed arithmetic (kept separate so it is testable) |
| `entity/ParadoxReaperGoals.kt` | main | Hunt, investigate, prowl, roost, drone goals |
| `entity/ReaperSmoothing.kt` | main | Critically damped springs used by every pose filter |
| `entity/ReaperRoostScoring.kt` | main | Roost ranking: darkness, then enclosure, then height |
| `entity/ReaperMountSeating.kt` | main | Seam letting the entity read a client-only seat setting |
| `entity/ParadoxReaperBreakGlassGoal.kt` | main | Pane breaking |
| `generation/instability/ParadoxReaperSpawner.kt` | main | Instability + decay driven spawning |
| `client/render/ReaperLegRig.kt` | client | Gait, foot planting, body tilt |
| `client/render/ParadoxReaperRenderer.kt` | client | Draws the creature from solved IK |
| `client/render/ParadoxReaperModel.kt` | client | Cuboids only; carries no pose |
| `client/render/ReaperCameraRoll.kt` | client | Carries the mount roll between the two camera injections |
| `client/render/ReaperMountCamera.kt` | client | Rider eye filtering and the in-a-block fallback |
| `client/config/ReaperMountClientConfig.kt` | client | Per-client riding camera and seating options |
| `mixin/client/ReaperMountCameraMixin.java` | client | Moves the mounted eye onto the drawn body; measures roll |
| `mixin/client/ReaperViewRollMixin.java` | client | Applies that roll to the world view matrix |
| `mixin/client/ReaperMountLookMixin.java` | client | Turns the rider's aim in the creature's frame |
| `mixin/client/ReaperRiderOrientationMixin.java` | client | Seats and turns the rider on the drawn body |
| `mixin/client/ReaperRiderPoseMixin.java` | client | Straddling pose, and everything layered on it |
| `entity/ReaperRiderPose.kt` | main | The pose itself, as plain numbers |
| `client/compat/ReaperVrCompat.kt` | client | Reflective Vivecraft VR detection |

Config lives under `paradoxReaper` in `config/mystcraft-reforged-balance.json`.

Tests: 141 passing, including gait, pose smoothing, foot contact continuity, roost preference,
awareness, speed, familiar-rest, surface-routing, summoned-leash, spawn-cadence, taming, and
target-filter regressions.

## Traps — things that look like cruft but are not

Every one of these was a real bug that reached the player.

**Never apply a 1/16 scale in the renderer.** `ModelPart.Cuboid.renderCuboid` already divides
vertex positions by 16. Doing it again renders the creature at 1/16 size. Every `matrices.scale`
in the renderer is a plain multiplier on geometry that is *already in blocks*; the bone part is
authored exactly `UNITS_PER_BLOCK` tall so its Y scale reads directly as a length.

**Never hold two `VertexConsumer`s from an `Immediate` at once.** Neither
`getEntityTranslucent(tex)` nor `getEntityTranslucentEmissive(tex)` is preallocated in
`BufferBuilderStorage`, so both return the *same* fallback buffer and it flushes on every layer
change. Interleaving writes through two references pours one pass's geometry into the other's
batch. The renderer therefore runs two strictly separated passes: emissive first (colour-only
writes, so it survives under the depth-writing shell), then glass.

**FABRIK does not choose a shape.** It converges to whatever is nearest its starting pose, and a
three-segment leg spanning half its own length has enormous freedom — left alone it settles into
flat accordion folds. The chain is therefore reseeded from scratch every solve from an
anatomical `bowProfile` (`[0.0, 1.0, 0.22, 0.0]`: knee high, ankle low). Because that seed is a
pure function of the two endpoints, it is stable frame to frame *without* a warm start.

**The seed's bow must be perpendicular to the root→tip axis, never the raw pole.** If the tip
lies near the pole axis the seeded chain is exactly collinear, which is a symmetric stationary
point FABRIK can never fold out of. Measured worst-case miss with the raw pole: a full block.
Perpendicular: 0.002 (solver tolerance), zero failures over 4000 random targets.

**Check FABRIK convergence *after* a pass, never before.** A freshly seeded chain already has its
tip on the target, so testing first exits immediately and leaves the seed's segment lengths
uncorrected.

**Body orientation must come from motion, not yaw.** Yaw is a single horizontal angle and carries
almost nothing once the body is on a wall — a Reaper climbing straight up has a yaw pointing
*into* the wall, and projecting that onto the wall plane degenerates. That made the forward axis
flip arbitrarily and laid the legs out with one reaching ahead and the rest trailing.

**Body tilt and body lift must use planted feet only.** A swinging foot is lifted into its step
arc; a whole tripod swings at once and the tripods alternate, so including them rocks the fitted
plane in time with the gait. That is the "wiggle".

**Gait turn-taking must be explicit.** The rule "may step if the other tripod is idle", evaluated
in array order, starves one side *permanently*: by the time the first tripod lands, the body has
travelled far enough that its legs re-qualify immediately. Measured: 240 steps vs 0 over 400
ticks — three legs never lifted at all. There is now an explicit `swingGroup`.

**Advance swings before judging handover.** Checking first means a tripod landing this tick still
reads as busy, so the other cannot start until the next frame. That dead tick lands twice per
cycle and stretches stride by about half again.

**Never plant a foot without a raycast hit.** The old fallback returned the un-grounded rest point,
which put feet in mid-air — made much worse by look-ahead aiming the probe out over ledges.
`findFoothold` walks the stride back toward the body then sweeps a ring, and returns null rather
than inventing ground. A leg with no foothold does not step.

**A waiting foot owns its planned foothold.** Render interpolation makes the measured body
velocity change at tick boundaries. Re-raycasting and replacing a waiting leg's destination every
frame therefore makes it visibly reconsider the step before its tripod gets a turn. The first
overdue, supported point is reserved until the swing begins; it is discarded early only if a
tight probe proves that its supporting block disappeared. Body velocity is low-pass filtered, and
the active swing uses a six-tick, three-phase path so lift-off and touch-down have no velocity snap.
The waiting tripod predicts both the remaining active swing and its own, preventing a stable
reservation from becoming stale before its turn arrives. Each swing also retains its lift normal,
so transferring between a floor and wall cannot bend the foot's trajectory halfway through.
The reservation is reconsidered only when its *predicted landing region* moves substantially,
which corrects genuine turns without mistaking terrain-search offsets for animation noise.

**Rendered facing has motion hysteresis.** One-tick navigation displacement is too noisy to be a
pose by itself, especially while a pathfinder hesitates at an edge. Planar travel is filtered and
must clear a small dead band before it can steer the rendered body, which then turns at a bounded
angular rate. Stopping therefore holds the last committed heading instead of flickering between
tiny opposing motions.

**The final body pose is client-smoothed.** Vanilla entity interpolation is linear within a tick,
so velocity still changes abruptly at 20 Hz even when server movement is correct. Each rendered
Reaper filters its visual origin and surface normal at frame rate; equal prediction and smoothing
time constants avoid leaving the shell visibly behind its authoritative hitbox. Teleports reset
the filter and ordinary corrections are capped, so rendering cannot visually detach from combat.

**Feet rotate the rendered body.** Travel direction only plans upcoming footholds. The visible
forward axis is recovered from the labelled front/rear positions of the currently weight-bearing
feet, then approached at a bounded rate. This keeps the body subordinate to the planted IK
constraints instead of rotating the hip roots first and making the legs chase the shell. A foot
whose tight support probe misses is excluded from forward, tilt, and lift fitting even when it is
not swinging; dangling legs do not get a vote in the body's pose.

**A swing has three visible phases.** A sine added to a horizontal lerp is mathematically curved,
but it starts moving sideways before the foot has visibly left the surface and reads as snapping at
normal camera distance. `ReaperFootstepMotion` spends the first 28% lifting in place, crosses at
full clearance, then spends the last 28% lowering in place. Each phase is quintic-eased and the
complete swing lasts six ticks, so the foot visibly breaks contact and deliberately replants.

**The two Little Anomaly roles live in different mods.** The equipped floating familiar is
`familiar_friends:little_anomaly_companion`; Familiar Friends gives that cosmetic entity its loose
orbit, owner synchronization, and distance recovery. Its key ability spawns
`mystcraft-reforged:paradox_reaper` with the petty-owner NBT contract. The old
`mystcraft-reforged:little_anomaly` id is retained only to migrate mistakenly saved ability
summons into real petty crawler Reapers.

**`SurfaceCling.rotateToward` is not a lerp on purpose.** Componentwise interpolation between two
opposed vectors passes through the origin and renormalises back to the start — a Reaper going
floor → ceiling would be stuck rendering upside-down forever. Covered by a regression test.

**Convex and concave transitions are separate rules.** Walking into a wall is `blocked ahead →
new up = heading.opposite`. Walking off a cliff is `floor ends ahead → new up = heading`. Only
having the first meant Reapers could climb up but never down. The lip probe uses a *small* box
past the leading edge — shifting the whole 1.9-wide hitbox still overlaps the floor it stands on
and reports solid ground until it has already walked off.

## Design decisions worth preserving

- **Speed is blocks/tick, not the movement attribute**, because custom `travel()` never runs it
  through vanilla's acceleration curve. `0.2806` is exactly a sprinting player. Full speed is used
  only during visible pursuit and the committed trip to a last-known position. Casting around
  after arrival and finding a dark dormant roost use half that speed.
- **Pursuit speed is snapshotted at acquisition**, not read live. In a pack full of permanent
  movement buffs (Origins, LevelZ), tracking live means a player already at Speed II is simply
  chased at Speed II, so drinking a potion gains them nothing. Against a snapshot, permanent speed
  is priced in once and anything applied *after* the hunt starts opens a real gap.
  Known limit: reads the attribute only, so Pehkui size-scaling is invisible.
- **Sensing is the Warden's** (`Vibrations` + `GameEventTags.WARDEN_CAN_LISTEN`). Crouch-silence,
  wool occlusion, and distance delay are inherited, not reimplemented. The Warden's *behaviour*
  is deliberately not borrowed — it runs on brain memory modules, which do not mix with the goal
  system used here.
- **Target order is player → player faction → local disturber.** A player faction means a tamed
  creature owned by an eligible player or a living member of that player's scoreboard team.
  Ordinary mobs only wake a settling Reaper within ten blocks, but a mob that attacks or wakes it
  becomes the exact quarry and remains tracked once the chase begins.
- **Whackdolls are not quarry.** Both Whackdolls corpse entity types are ignored as vibration
  sources, and the hidden carrier player is excluded while `RagdollEntity.isRagdolled` is true.
  Reflection keeps this optional so Mystcraft does not require Whackdolls to launch.
- **The Little Anomaly ability's owner-bound Reaper seeks idle shelter.** If its owner remains
  still for six seconds, that tiny crawler enters a dark-biased surface wander, deliberately moves
  away, and latches dormant only at a covered dark spot or on a ceiling. It wakes only after the
  owner moves more than ten blocks from its roost. The floating Little Anomaly is separate.
- **Petty owner-following uses a bounded surface graph.** Direct same-face travel remains the cheap
  common case. Only when the owner lies off the crawler's current plane or it becomes blocked does
  it search floor, wall, and ceiling transitions together, capped at 384 visited nodes and
  staggered to one replan every 30–49 ticks. Node collision uses the creature's real dimensions;
  open single doors admit 1x1 forms, while Greater Reapers require open double doors.
- **A Greater's screech-summoned lessers use ordinary Reaper AI.** They hunt, search, settle, climb,
  and jump by the same rules as their Greater. Their only special rule is a hysteretic master
  leash: beyond 24 blocks they return to the Greater, then resume normal behavior within 18.
- **Reaper sight treats tagged glass as transparent.** Full and stained glass blocks, plus every
  pane in `reaper_transparent`, do not occlude sight. Only blocks in `reaper_breakable` can be
  shattered: lesser forms need one pane and greater forms require a genuine 2x2 pane opening.
  A completed breach retains a point on its far side for up to three seconds, making the Reaper
  cross the opening before its normal hunting goal is allowed to reconsider its route.
- **Settling prefers dark roosts.** Open sky is strongly disfavoured, lower combined light wins,
  wall travel biases upward, and a Reaper that reaches a ceiling anchors there until dormant.
  Roaming headings are held until the body genuinely stops making progress and are scored using
  full-hitbox clearance, preventing a Greater from spinning at ordinary surface contact or
  repeatedly choosing a passage it cannot fit through.
- **Age arrival is a warning window, not an ambush.** Entering or respawning in an Age grants
  45 seconds in which natural spawning and Reaper player-sensing ignore that player. Attacking a
  Reaper ends the grace immediately. Natural pressure defaults to one Greater per player, a
  90-second hard spawn cooldown, and a 24–42 block spawn shell.
- **Villagers are not cleanup targets.** Passive and unrelated living entities no longer become
  quarry merely by moving near a settling Reaper. A Reaper can still retaliate against a mob that
  actually damages it, and player pets/scoreboard allies remain valid extensions of the player.
- **A summoned lesser can be permanently bound.** Weaken a non-petty lesser to 35% health or less,
  then interact with it using an Echo Shard. Binding severs its Greater-master leash and gives it
  the owner-follow, shelter, surface-pathing, and defense behavior of the Little Anomaly crawler,
  with 24 health and persistence across owner death, logout, and chunk reloads. Bound lesser
  companions can also be ridden.
- **A weakened Greater can be permanently bound.** At 10% health or less, use four Echo Shards to
  bind it. The owner rides either size with an ordinary right-click and steers with WASD;
  movement remains tangent to floors, walls, and ceilings. Sneak-right-click with an empty hand
  cycles Follow, Stay, and Wander Nearby. Wander uses a held surface heading inside a nine-block
  leash, avoiding continuous path searches.
- **Bound companions sustain themselves.** Successful attacks return 45% of actual damage as
  life, they recover one health every five seconds after ten seconds without damage, and an owner
  can feed one Amethyst Shard for an emergency heal (10% max health, minimum four).
- **The riding camera follows the procedural body pose by default.** Players sensitive to camera
  roll can set `bodyTrackingCamera` to `false` in
  `config/mystcraft-reforged-reaper-mount.json`; mounting and steering remain available with the
  ordinary upright camera.
- **Alert visuals are state-readable.** The server's non-dormant states deploy the large cubes and
  emissive core. On entering dormant, the cubes retract over roughly two seconds and emissive
  brightness reaches zero; only the faint minimum-lit glass shell remains.
- **Jump planning is expressed against the clinging normal**, so one routine covers every
  orientation: landing candidates are offset along the creature's own up, and the launch angle is
  measured off its own surface plane.
- **Not using GeckoLib or AzureLib.** Both are keyframe-animation libraries; this is runtime IK.
  Zero new dependencies in a 271-mod pack.
- **ArachnoMod is source-available, NOT open source** — no commercial reuse, no ports, no
  re-releases. Do not copy code from it. FABRIK itself is a published algorithm (Aristidou &
  Lasenby 2011) and is fine to implement independently, which is what was done.

## Testing

```
/reaper drone          spawn a lure-following test subject 4 blocks ahead
/reaper lure           give the lure tool; right-click raycasts 160 blocks
/reaper spawn greater  normal greater form
/reaper spawn lesser   normal lesser form
/reaper spawn crawler  Little Anomaly's temporary owner-bound crawler (`petty` is an alias)
/reaper spawn bound_lesser
                       permanent lesser bound to the command player
/reaper spawn bound_greater
                       permanent rideable Greater bound to the command player
/reaper spawn drone    lure-following Greater (also available as `/reaper drone`)
/reaper creative       show whether creative players are included in targeting
/reaper creative include|exclude
                       persistently include/exclude creative players from sight, hearing, and spawning
/reaper clear          remove all Reapers in the dimension
```

Drones never acquire players, are persistent, and are held visibly alert so the cubes and legs
are on screen. They jump gaps too, so traversal can be exercised with the lure.

Full manual checklist: <https://claude.ai/code/artifact/cc1d5f24-d3f9-44bf-b966-9594b9f213ff>

**Verification loop that worked well:** `scratchpad/render_reaper.py` parses the real Kotlin
sources (cuboids, leg table, constants) and rasterises the creature offline with the shipped
PNGs. It caught leg proportions and the accordion folds without launching the game. Its blind
spot is that it renders a *stationary* creature — every motion bug (dragging, mid-air feet,
wiggle) got through it. For gait bugs, simulate the stepping logic in Python instead; that is how
the tripod starvation was proven (240 steps vs 0).

## Smoothness: what actually caused the snapping

Three separate things made the creature look keyframed-by-a-robot rather than animated, and all
three were in the *filters*, not in the gait.

1. **Bang-bang orientation.** `bodyUp`, `bodyForward`, the travel heading, and the client pose
   normal were each turned toward their target at a fixed maximum radians per tick. That is a
   constant-velocity move followed by a dead stop, with no ease at either end. Every one of them
   now runs on a critically damped spring (`ReaperSmoothing`), which is the fastest response that
   does not overshoot and has continuous velocity throughout. Tuned by half-life, in ticks.
2. **Binary foot participation.** `updateBodyTilt`, `updateBodyLift`, and `updateBodyForward`
   took only feet that were `footSupported && !isStepping`. A whole tripod leaves at once, so
   that set changed discontinuously twice per gait cycle and the fitted plane jumped with it,
   visible as a tick in the body no matter how smooth the filter downstream was. Each foot now
   contributes `ReaperFootstepMotion.contactWeight(stepProgress)`, which falls to zero as the
   foot lifts and returns as it lands, so the stance crossfades. The Newell normal weights each
   perimeter *edge* by both endpoints for the same reason.
3. **Fixed swing duration.** Stride length grows with speed but the swing was always six ticks,
   so at a sprint each foot sat still through a long drift and then crossed a large distance in
   the same time. `swingDuration` shortens the swing as the body speeds up, floored at 55% so a
   sprint still reads as three distinct phases. A swing already in flight keeps the duration it
   started with, stored per leg, because rescaling mid-swing jumps the foot along its own arc.

The integrator is implicit (backward Euler). Explicit integration of a stiff spring diverges once
`omega * delta` nears 1, and delta is a render-frame interval that spikes on chunk loads.

## Cliff descent

`descendCandidate` used to be dead code in practice. Both of its probes were run against one box
at the body's *centre*, so "is there floor ahead" sampled a metre of open air and "is there a wall
to grab" never reached the face below the lip. Nothing ever fired, and a Reaper reaching a cliff
simply strolled off and fell, which is the reported "jumps down instead of climbing down".

Probes are now placed where their answers live, measured from the contact plane rather than the
body centre (`edgeProbe`), so the same call means the same thing on a floor, wall, or ceiling.

Detecting the lip is only half of it. `findSupport` cannot confirm the new face until the body is
physically alongside it, and a Reaper carrying full speed over an edge throws itself clear of the
face it is trying to catch. `wrapFace`/`wrapTicks` hold the intent across those ticks while
`travel` cuts forward speed to `WRAP_FORWARD_FACTOR` and adds `WRAP_DESCENT_PULL` downward, so
the body comes around the corner against the surface instead of away from it.

## Mounted camera

Rolling `Camera.rotation` does nothing to the picture. In 1.20.1 `GameRenderer.renderWorld`
rebuilds the view as `M * Rx(camera.getPitch()) * Ry(camera.getYaw() + 180)`; the camera's
quaternion only ever reaches particle and nameplate billboarding. This is why the mount camera
appeared to do nothing for so long: the code looked correct and was injecting into a value the
world view never reads.

The matrix stack only post-multiplies, so no later call can add a rotation to the *left* of that
pair. `ReaperViewRollMixin` folds the roll into the pitch argument instead: replacing it with
`Rz(roll) * Rx(pitch)` yields `M * Rz * Rx * Ry`, which applies `Rz` to coordinates already in
view space, a true camera roll. It lands before `setupFrustum`, so culling agrees with it.

`ReaperMountCameraMixin` still measures the angle, because that is where the drawn pose and the
camera basis are both available, and publishes it through `ReaperCameraRoll`. The roll is cleared
at the head of every frame so a dismount cannot leave the world tilted.

**Accessibility.** `ReaperMountClientConfig` is exposed in Mod Menu under *Reaper Mount*: a master
toggle for the surface-aligned camera, a 0-1 roll strength for a partial lean, and a toggle for
drawn-body seating. Anyone prone to motion sickness can ride with a level horizon.

## Rider seating

The drawn shell sits at `contactPoint + bodyLift * up`, and `bodyLift` is solved from where the
legs actually landed, so it moves with the terrain. The old seat was `pos + height * 0.78`, a
fixed distance from a point the body is not at, which put the rider above the shell and bobbing
independently of it. `updatePassengerPosition` now uses `ReaperMounting.seatOnBody` against the
published visual pose on the client, falling back to the analytic offset on the server, where the
value feeds ride logic rather than anything anyone looks at.

## Roosting

Darkness is the whole criterion, and it is priced so that one light level outranks the entire
combined range of every other term (enclosure 36 + height 32 + open sky 24 = 92, against 128 per
level). A dark corner behind a desk beats a ceiling next to glowstone, and it beats it outright
rather than on points. `ReaperRoostPreferenceTest` pins that ordering down.

The old prowl goal anchored on *any* ceiling it happened to touch, which ended the search at a lit
one while the dark corner two blocks away was never scored. Solved limbs mean the creature fits
wherever its body fits, so the anchor condition is now darkness plus a valid cling face, and
`enclosingFaces` rewards being tucked in.

`ParadoxReaperRoostGoal` runs only while dormant and walks the creature back to `roostAnchor`
after anything drags it off, dropping the perch if it has since been lit, filled, or become
unreachable. Without it a Reaper that stood down simply stopped wherever its last search ended.

**Nearby noise.** The vibration listener used to return `isPlayerAlly(source)` for any living
source, so a villager wandering into a cave was inaudible *by rule*. `isAudibleNeighbour` accepts
any living thing within `neighbourHearingRadius` (default 10 blocks), deliberately much shorter
than the 24-block hearing range, so a Reaper does not abandon its perch for distant livestock.

## Look controls and the roll singularity

Rolling the view is only half a mount. Vanilla adds the mouse delta straight onto world yaw and
pitch, so once the picture is rolled the controls no longer match it: on a wall, dragging sideways
swings the view up the screen, and on a ceiling it moves the wrong way outright.

`ReaperMountLookMixin` replaces `Entity.changeLookDirection` while riding. It rotates the look
*direction* about the axes the player can actually see — yaw about the screen's up, then pitch
about the screen's horizontal recomputed after that turn — and converts the result back to world
yaw and pitch, which everything downstream still reads (steering projects it onto the surface, the
server receives it, reach is cast along it). It must also reproduce vanilla's two side effects:
advancing `prevYaw`/`prevPitch` by the same step, and calling `vehicle.onPassengerLookAround`.
Angles are applied as deltas, not assigned, so yaw stays continuous across the wrap point.

The more important effect is on the roll itself. Roll is measured about the view's forward axis,
and that angle is **undefined** when forward aligns with the body's up — which, on a wall, is just
looking straight at it. Approaching that direction made the roll swing hard and then snap to zero
as the measurement gave out. Clamping pitch against the *creature's* horizon means forward can
never reach that axis, so the singularity becomes unreachable rather than merely guarded.

WASD needed no change: `ReaperMountSteering` already projects the look vector onto the surface, so
once aim is in the creature's frame the two agree by construction.

## Vivecraft

Detected reflectively through Vivecraft's published `VRAPI.instance().isVRPlayer(player)`, cached
per tick. Nothing is linked at compile time and no code is borrowed — Vivecraft is LGPLv3 and this
mod is MIT, so calling the published API is the only appropriate contact between them. Vivecraft's
own class and method names are not remapped between dev and production, so a name lookup is stable
in both.

In VR, two things switch off:

- **The artificial roll.** Rolling the horizon of a headset that is not physically rolling is one
  of the most reliable ways to make someone ill, and Vivecraft composes its own per-eye view
  matrices in any case, so the flat-pipeline injection is not the authority there.
- **The surface-relative look frame.** On a monitor the aim is a pair of numbers this mod may
  redefine; in VR it is where the player's head physically is.

Drawn-body seating stays on — that is just placement, and it is correct in a headset too.

## Frame cost

The rig runs once per *rendered frame*, not once per tick, and confirming six footholds plus six
reserved plans is twelve raycasts. At 144fps that is roughly 1,700 world queries a second for one
creature, multiplied by however many are on screen — frame time that presents as exactly the
stutter the smoothing exists to remove. Support is now measured on a `SUPPORT_PROBE_INTERVAL_TICKS`
interval (0.5 ticks, ten times a second) and reused in between, which is visually identical because
a block's ability to hold a foot changes only when somebody mines it. A leg that has just landed
seeds its cache entry directly, since its destination was raycast when the plan was made.

## Inside corners, and the judder that came with them

Reported together and caused together. A Reaper walking into the corner where two walls meet
would press its face against the second wall, refuse to put its legs on it, and vibrate.

`climbCandidate` decided whether to transition by sampling a small box a fixed distance ahead of
the *body centre*. Against a thin wall, an irregular face, or a corner the creature was already
flush with, that box can sit past the very block being leant on and report nothing. The transition
never fired; the hunt goal counted the creature as stalled after `BLOCKED_BEFORE_ESCAPE` ticks and
steered it sideways; the sideways heading removed the intent that would have produced a climb
candidate; and the cycle repeated several times a second. Asking the *real hitbox* whether it is
already in contact with the candidate face — which is both cheaper and the question actually worth
asking — makes the transition fire on contact, and the oscillation disappears with it.

Two more things fed the same judder:

- **Adhesion was a switch, not a ramp.** `grip` tripled the instant a support probe missed and
  dropped back the instant one landed. On broken ground that probe flickers tick to tick, so the
  body was being yanked into the surface at 3x strength every other tick. It now ramps with
  `regripFraction()` across the grace window.
- **`stepHeight` was 1.0 on every surface.** Vanilla's step-up is hardcoded to world Y. On a floor
  that is the stair climbing this creature wants; on a wall it is a shove along the surface and on
  a ceiling it is a shove into it, applied by the collision pass behind the cling system's back.
  It is now only set on `Direction.UP`.

## Letting the feet decide which way the body points

The creature's own premise is that the feet are what touch the world and the body follows them.
`updateBodyTilt` always fitted a plane through the planted feet, but only gave it `TILT_WEIGHT`
authority, with the rest going to the discrete clinging normal.

On open ground that is right: the clinging normal is a useful stabiliser when a foot is
momentarily on something odd. In a corner it is actively wrong. Half the feet are on one wall and
half on the next, and the clinging normal is one of six axis directions that by definition cannot
describe a body halfway between two of them — so it dragged the creature back to axis-aligned and
turned the corner into a snap between two poses rather than a rotation through them.

Its authority is now spent in proportion to its disagreement with the feet. Flat ground keeps the
old blend; past `CORNER_HANDOVER_RADIANS` of disagreement the feet decide alone. Combined with
`probeOutward` letting those feet reach a perpendicular wall in the first place, a Reaper crossing
an inside corner now rotates with its stance as each leg switches over.

## Inside corners: the mechanical half

Feet explain the *look* of a corner; the cling face still decides collision, and that had its own
oscillation. Inside a corner both walls are within gripping range at once, and the face the
creature wants is chosen from a heading that itself flips as soon as the face does — so it
transitions to the second wall, immediately reads the first as the way onward, transitions back,
and buzzes between the two several times a second. Making the transition eager (contact rather
than lookahead) made this *faster*, not better.

`previousFace`/`faceHoldTicks` refuse the face just left for ten ticks, which is long enough to
commit, walk, and settle.

## Outside corners: reaching down over a lip

A screenshot of a Reaper crossing the edge of a roof showed the failure exactly: body out past the
lip in open air, every leg trailing back to the surface behind it, nothing on the vertical face
below. The creature was not walking around the corner at all — it was being shoved off the edge
and dragging its legs until they tore free.

Both existing probes are blind to that face, for opposite reasons:

- `probeDown` casts along the creature's own down axis. Over a lip that axis is **parallel** to
  the wall and offset outside it, so the ray runs down through open air alongside the face
  forever and never touches it.
- `probeOutward` casts horizontally from the body to where the foot belongs. Over a lip that ray
  passes straight **over the top** of the same face.

The one direction that meets it is *back inward*, from a point already past the edge and already
below it — the motion a climber makes reaching a hand down over a ledge. `LEDGE_DROPS` samples
several depths and casts back toward the body by `LEDGE_REACH_BACK`.

Ordering matters as much as the probe. It runs **before** the ring sweep, because the sweep would
otherwise find floor a little way behind the foot and plant there — which is what cramped every
leading leg back onto the roof rather than letting it take the wall, and is visible in that
screenshot as the trailing-leg pose.

With feet on both surfaces, the adaptive handover in `updateBodyTilt` fits a plane through them
and angles the body into the corner, which is the pose the creature should hold there.

## Why the body got there first

`descendCandidate` was detecting the lip too late and too narrowly. Its probe sat a full
`LIP_LOOKAHEAD` past the body's leading edge — clear of the very wall it was hunting for — leaving
only a sliver of the box overlapping once the back-offset was applied. The descent was therefore
detected in a narrow band as the body approached the edge and missed entirely whenever the body
arrived at speed, which is when it matters. Without it, `wrapFace` never engaged, so the throttle
was never cut, so the body sailed over the lip at full speed.

The probe now sits just past the edge (`LIP_DESCENT_LOOKAHEAD`) and tries several depths
(`LIP_DESCENT_DROPS`), which puts the same geometry solidly inside the face.

## Feet on perpendicular surfaces

Every foothold probe cast along the creature's own down axis, which can only find the surface it
is already standing on. In an inside corner the surface a foot wants is not beneath it but in
front of it, so the legs on that side searched, found nothing, and folded away — the "won't put
its legs on the wall it is rubbing its face against" half of the report.

`probeOutward` reaches from the body to where the foot belongs and takes whatever is in the way,
whichever direction it faces. It is the counterpart to `probeDown`, and it runs last so it only
governs cases the ordinary search could not answer; plain floor walking is untouched.

`isFootholdSupported` had the matching flaw and had to change with it. Casting along the down axis
asks "is this foot on something *beneath* it", so a foot gripping a wall failed and was hauled
back in, undoing the placement the outward search had just made. It now tests for any collision
geometry within a tight box, which is orientation-agnostic and cheaper than a raycast.

## Which side simulates a ridden Reaper

`Entity.isLogicalSideForUpdatingMovement()` returns `player.isMainPlayer()` whenever a player is
the controlling passenger. No server ever satisfies that, so **a ridden Reaper is simulated on the
rider's own client**, and the server takes the result via `VehicleMoveC2SPacket`. An unridden one
falls through to `canMoveVoluntarily()` and is simulated server-side as usual.

This is worth knowing before touching anything that sets velocity on this entity, because the two
cases run on opposite sides. The first rider-jump implementation set velocity inside
`startJumping`, guarded with `if (world.isClient) return` — correct-looking, and completely inert:
the sound played and the legs tucked, because both of those are server-authoritative and
synchronised, while the launch was applied to a body the server was not moving and was overwritten
by the next position the client reported. AI lunges were unaffected the whole time, which is
exactly the asymmetry to expect.

The fix is vanilla's own shape. The charge is *recorded* by whichever side hears about it —
`setJumpStrength` on the client as the key is released, `startJumping` on the server as the packet
lands — and *spent* at the top of `travel`, which by definition runs on the simulating side.
`jumpDetachTicks` is checked there alongside the tracked cling face, since that face is
synchronised and can still read as attached for a tick or two on the side that did not originate
the jump; without it, surface locomotion overwrites the launch before the creature leaves.

## Rider jumping

`ParadoxReaperEntity` implements `JumpingMount`, so riders get the vanilla charge bar and the
horse's hold-and-release. Riders previously had no jump at all, which removed the one manoeuvre
the creature is built around — leaving one surface for another — and left them unable to cross
gaps, take a wall from the floor, drop off a ceiling, or escape the corner above.

The arc is built around the surface normal rather than world up, so one key throws the creature
off a floor, out from a wall, or down from a ceiling. With no directional input it is a straight
push off the surface; with input it tips toward the rider's heading by `MOUNT_JUMP_LEAN`.

`jumpDetachTicks` is what makes any of it work. `reachForSurface` pulls toward any face within
three blocks at 0.07 a tick against a gravity of 0.055, so without a detach window a Reaper that
drops off a ceiling is hauled straight back up to it and hangs there. A deliberate jump suppresses
both the reach and the cling search for six ticks; ordinary falling still reaches for a hold as it
always did.

## Rider eye: filtering without adding lag

The drawn body is carried by six legs taking turns, so it wobbles at gait frequency. That is
pleasant to watch from outside and unpleasant to have your head bolted to — but filtering the
camera position outright would put the same lag on steering, which is the one thing that has to
stay immediate.

The split that resolves it: the eye anchor is the creature's raw interpolated position plus a
procedural offset, and **only the offset is filtered**. That offset *is* the gait wobble and
nothing else, so smoothing it leaves the creature's real travel — the part answering to player
input — completely untouched. `ReaperMountCamera.resolve` takes both the drawn anchor and the raw
reference for exactly this reason.

## Rider eye: when the seat is inside a wall

A creature that walks on ceilings routinely puts its back, and its rider's eye, inside a block.
Vanilla never has to solve this in first person, because a player's eye lives inside their own
hitbox and that is never in a wall.

When the anchor is buried, the view moves out in front of the creature's face and is then clipped
back to whatever is actually reachable, so the fallback cannot bury itself in the next surface.

`HOLD_TICKS` is the part that matters. Whether a point a hand's width from a wall is *inside* that
wall changes with every footfall, so a view that switched the instant the test flipped would strobe
between two positions several times a second — considerably worse than either one on its own. The
displaced view is held for half a second after the last obstruction, so the creature has to
genuinely leave the tight spot before the view returns.

## Seating the rider on the drawn back

`updatePassengerPosition` places the rider on the drawn shell, but it runs once a tick and the
result is then interpolated, so the model arrives a tick late and smoothed. Against a body that
rises over terrain and crouches as it settles, that lag is the difference between sitting on the
creature and floating near it.

`ReaperRiderOrientationMixin` corrects the position again at render time, against the pose the
renderer is drawing *this frame*, so the rider goes up when the creature rises and down when it
crouches exactly in step. The tick-rate seat remains the authority for collision, interaction and
dismounting; the render correction only removes the visual lag on top of it, and is skipped
entirely if it would exceed `MYSTCRAFT$MAX_CORRECTION` — a gap that large means the drawn pose
belongs to some other creature, most likely one that has left the view.

## Drawing the rider in the creature's frame

Seat position was correct from the drawn-body work, but a mounted player is still drawn from world
axes like any other entity, so a rider crossing a ceiling stayed bolted upright and appeared to
float alongside the creature rather than sit on it.

`ReaperRiderOrientationMixin` pre-multiplies the creature's orientation in
`LivingEntityRenderer.setupTransforms`. That seam is chosen deliberately: it runs with the matrix
already at the entity's origin and is where vanilla applies body yaw, so rotating there puts that
yaw — and every transform after it, including the model's own offset — into the creature's frame.
Rotating any later would leave the yaw turning about the world's vertical while the body pointed
somewhere else.

It targets `LivingEntityRenderer` rather than the player renderer, so anything a Reaper can carry
is drawn the same way. The exactly-inverted case is picked deterministically rather than left to a
degenerate cross product, because hanging from a ceiling reaches it on the nose.

## The rider's pose, and everything hanging off it

Vanilla's riding pose was authored for a saddle: knees high, thighs together, hands forward on
reins. A Reaper has neither, and a rider sat on one that way looks perched on a chair that happens
to be moving. `ReaperRiderPose` straddles the shell instead — knees out, feet back, torso folded
down, hands on the lattice — and deepens the fold with the creature's pace, which is a readable
difference between it strolling and it hunting.

**The injection point is the whole design.** Everything attached to a player is positioned from
the same `ModelPart`s, so posing the parts is what makes the rest follow rather than detach. The
tail of `BipedEntityModel.setAngles` is deliberately chosen:

- `PlayerEntityModel.setAngles` calls `super.setAngles` **first**, then copies the transforms onto
  the skin's second layer — sleeves, trousers, jacket, hat. Injecting at that tail is upstream of
  the copy, so the overlay follows.
- Armour models never have `setAngles` called on them at all; `ArmorFeatureRenderer` copies the
  context model's angles across with `setAttributes`. Armour therefore inherits the pose for free,
  and so does any modded layer following the same convention.
- Held items are placed by `setArmAngle`, which reads the arm part directly.
- Capes and elytra are positioned from the torso.

A mod that replaces the player with a model that is not a biped at all is out of reach — there are
no arms and legs to pose — but it keeps whatever pose it would otherwise have had, and the seat
and orientation still apply to it, because those are matrix transforms rather than model edits.

Two things are left alone on purpose. The head keeps the aim vanilla gave it: head and body are
siblings in the biped model rather than parent and child, so leaning the torso does not drag the
head with it, and the neck join is closed by moving pivots on the same ratios vanilla uses for
sneaking. And the arms are only taken over when `ArmPose` is `EMPTY` or `ITEM` — drawing a bow,
levelling a crossbow, raising a shield or a spyglass all say something the player needs to see,
and that is worth more than a tidy grip.

## Known open items

- **Movement snagging needs in-game regression coverage.** The direct causes found so far were
  target sight being treated as lost on non-scan ticks, and target steering projecting to zero
  after a floor-to-wall transfer. Pursuit now preserves the last real sight result, transports
  its heading through surface corners, jumps valid gaps, and chooses a short lateral detour after
  eight stalled ticks. Keep testing unusual modded collision shapes and sub-two-block passages.
- **Translucency sorting.** Nested translucent boxes have no intra-entity depth sort; the core is
  drawn first so the shell blends over it, but odd angles may still misresolve.
- **Vibration listener data is not persisted to NBT** (the Warden does persist it). An in-flight
  vibration is lost if the chunk unloads mid-flight. Harmless, but it is a real difference.
- **Look-ahead follows the scheduled landing time.** The active tripod predicts one
  `swingDuration`; the waiting tripod also includes the active swing's remaining time. This lets
  targets stay fixed without becoming stale. Keep that horizon tied to the swing schedule.
- **Decay spawn multiplier is 2x, not 4x.** `decayChanceMultiplier` defaults to `2.0f`, while the
  written design calls for decay being four times likelier than clean ground to produce a Reaper.
  The guidebook is worded to stay true either way. Change the default if the design is the intent.
- **Locomotion is still body-led, not foot-led.** The feet follow the body, and the body is moved
  by the entity's own velocity; a foot that cannot reach anything folds away rather than stopping
  or redirecting the body. Everything above narrows the gap — the body throttles back at a lip and
  the feet can now reach a face below or beside them — but a genuinely foot-led creature would
  derive body motion *from* the stance rather than the other way round. That is a much larger
  change and worth considering only if the current approximation keeps showing through.
- **`updateBodyLift` still measures along the cling normal.** With half the feet on a perpendicular
  wall their offset along that axis is large and negative, so the mean drags the ride height down
  to its clamp mid-corner. Harmless but worth revisiting if the body looks like it sinks.
- **Residual stutter, if any, is next in the position stream.** The corner oscillation, the grip
  switch, and the world-Y step were the three mechanical sources found. If the body still judders
  after those, the remaining suspects are `smoothVisualOrigin`'s 1.15-tick lead — which amplifies
  a noisy input rather than smoothing it — and vanilla's entity position interpolation.
- **VR is untested on actual hardware.** The detection path and the two things it disables are
  written against Vivecraft's published API, but nobody has ridden a Reaper in a headset. Confirm
  that the seat lands sensibly and that no roll leaks through Vivecraft's own camera path.
- **The mount camera roll is unverified against shader packs.** It modifies the view matrix
  before `setupFrustum`, so vanilla and most pipelines should agree, but Iris and Optifine
  rebuild their own matrices in places and may need checking.
- **The rider's pose is authored, not solved.** It is a fixed straddle that deepens with speed,
  not inverse kinematics against the actual lattice, so hands and feet sit where the shell
  generally is rather than on any particular part of it. Solving them onto real geometry would
  need the rider's limbs run through the same FABRIK the creature's own legs use.
- **Non-biped replacement player models keep their own pose.** Seat and orientation still apply,
  since those are matrix transforms, but there are no arms and legs for the pose pass to set.
- **Patchouli `entity` pages for the Reaper are unverified in-game.** The bestiary entries use
  them; the renderer raycasts against the world during its IK solve, which is well-defined in the
  book GUI but was not tested. If they misbehave, replace those two pages with `text`.
- **Kotlin incremental compile cache corrupts regularly** in this project
  (`Storage ... is already registered`), producing *fake* cascades of "unresolved reference"
  errors across whole packages, and sometimes a Kotlin compile that silently emits no classes so
  the Java mixins fail with "package does not exist". Fix: `rm -rf build/kotlin`. Consider
  disabling incremental Kotlin compilation.

## Deploying

```
cd "D:/MC/mystcraft reforged" && ./gradlew build --offline
cp build/libs/mystcraft-reforged-1.0.0.jar "C:/Users/brand/AppData/Roaming/ModrinthApp/profiles/Worlds Lost (Beta)v2 1.0.0 (1)/mods/"
```

**Exit Minecraft completely before replacing the jar.** Windows does not reliably reject the
overwrite. If replacement succeeds while Fabric still has the old ZIP open, a later lazy class
load can soft-crash the integrated server with `ZipFile invalid LOC header (bad signature)` even
though the newly written file is valid on disk. `mystcraft-reforged-1.0.0.jar.bak` in that folder
is the pre-Reaper build.
