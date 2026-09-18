package net.trilleo.mc.plugins.tritown.economy.storage

/**
 * Versioning for the economy's on-disk format.
 *
 * Every stored file records the version it was written with, so that a future
 * change to the layout can be detected rather than silently misread.
 */
object StorageSchema {

    /** The version this build writes. */
    const val CURRENT: Int = 1

    /** The oldest version this build can still read. */
    const val OLDEST_SUPPORTED: Int = 1

    /**
     * Checks that data written at [version] can be read by this build.
     *
     * A newer version is refused rather than read on a best-effort basis: an
     * older build opening a newer file would drop whatever it did not
     * understand, and the next flush would write that loss back to disk.
     *
     * When the format does change, raise [CURRENT], leave [OLDEST_SUPPORTED]
     * alone, and upgrade the older shape as it is read in the storage
     * implementation.
     *
     * @throws EconomyStorageException when [version] cannot be read
     */
    fun checkReadable(version: Int, source: String) {
        if (version > CURRENT) {
            throw EconomyStorageException(
                "$source was written by a newer version of TriTown (schema $version, this build reads up to " +
                        "$CURRENT). Update TriTown rather than letting an older build overwrite it."
            )
        }
        if (version < OLDEST_SUPPORTED) {
            throw EconomyStorageException(
                "$source uses schema $version, which this build can no longer read (oldest supported is " +
                        "$OLDEST_SUPPORTED)."
            )
        }
    }
}
