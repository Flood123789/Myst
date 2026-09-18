package mystcraft.flood.entity

/**
 * Whether a rider is placed on the Reaper's drawn shell rather than on its hitbox.
 *
 * The preference itself is a client setting, but the code that acts on it lives in the entity,
 * which is common to both sides and so cannot see the client source set at all. This holder is
 * the seam: the client installs a supplier during its own initialisation, and the entity asks
 * through it. On a dedicated server nothing ever installs one, and the default keeps the
 * analytic seat that side has always used.
 */
object ReaperMountSeating {

    @Volatile
    private var supplier: (() -> Boolean)? = null

    fun install(source: () -> Boolean) {
        supplier = source
    }

    fun enabled(): Boolean = supplier?.invoke() ?: false
}
