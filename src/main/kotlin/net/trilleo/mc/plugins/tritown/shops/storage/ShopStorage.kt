package net.trilleo.mc.plugins.tritown.shops.storage

/**
 * Where shop definitions are kept between restarts.
 *
 * Only JSON ships today, but the shops themselves never name a file, so a
 * database-backed store can be dropped in without touching the feature.
 */
interface ShopStorage {

    /** The on-disk layout version this store writes. */
    val schemaVersion: Int

    /**
     * Every stored shop.
     *
     * @throws ShopStorageException when the data cannot be read at all, which
     *   stops the feature rather than letting an empty list overwrite it
     */
    fun loadAll(): List<StoredShop>

    /** Replaces everything on disk with [shops]. */
    fun saveAll(shops: List<StoredShop>)
}

/** Raised when stored shops cannot be read, and must not be silently replaced. */
class ShopStorageException(message: String, cause: Throwable? = null) : Exception(message, cause)
