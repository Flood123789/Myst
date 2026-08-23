# Customizable structure templates

Put vanilla structure-block `.nbt` files in the matching subfolder. More than one file in a folder forms a deterministic random pool. These files become the mod's baked defaults.

Players and modpack authors get the same folder tree automatically at `config/mystcraft-reforged/structures/`. If that matching config folder contains at least one `.nbt`, its pool completely replaces this bundled pool and the procedural generator for that feature. Restart Minecraft after adding or changing files.

Templates are horizontally centered, rotated/mirrored from the Age seed, and anchored by their lowest block to the terrain (or to the sky for floating features). Keep structure voids where existing terrain should survive. Structure block exports from a world's `generated/<namespace>/structures/` directory are compatible.
