package mystcraft.flood.entity

enum class ReaperCompanionCommand {
    FOLLOW,
    STAY,
    WANDER;

    fun next(): ReaperCompanionCommand = entries[(ordinal + 1) % entries.size]
}
