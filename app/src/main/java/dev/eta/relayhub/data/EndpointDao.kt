package dev.eta.relayhub.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface EndpointDao {

    @Query("SELECT * FROM endpoints ORDER BY favorite DESC, updatedAt DESC")
    fun observeAll(): Flow<List<Endpoint>>

    @Query("SELECT * FROM endpoints WHERE id = :id")
    suspend fun findById(id: Long): Endpoint?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: Endpoint): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<Endpoint>): List<Long>

    @Update
    suspend fun update(item: Endpoint)

    @Delete
    suspend fun delete(item: Endpoint)

    @Query("DELETE FROM endpoints WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("UPDATE endpoints SET favorite = :favorite, updatedAt = :now WHERE id = :id")
    suspend fun setFavorite(id: Long, favorite: Boolean, now: Long = System.currentTimeMillis())

    @Query(
        "UPDATE endpoints SET lastCheckAt = :at, lastCheckOk = :ok, lastCheckDetail = :detail " +
            "WHERE id = :id"
    )
    suspend fun setCheckResult(id: Long, at: Long, ok: Boolean, detail: String)

    @Query(
        "UPDATE endpoints SET balanceText = :text, balanceAt = :at, balanceError = :error " +
            "WHERE id = :id"
    )
    suspend fun setBalance(id: Long, text: String, at: Long, error: String)

    @Query(
        "UPDATE endpoints SET balanceText = :text, balanceAt = :at, balanceError = :error, " +
            "authToken = :token, tokenExpiresAt = :expiresAt WHERE id = :id"
    )
    suspend fun setBalanceAndToken(
        id: Long,
        text: String,
        at: Long,
        error: String,
        token: String,
        expiresAt: Long,
    )

    /**
     * 写入余额结果，同时保存刷新后的登录 Cookie。
     *
     * new-api 一类的站点每次用 Cookie 换令牌时都会轮换 Cookie，
     * 必须把新的值写回，否则下次查询就会失效。
     */
    @Query(
        "UPDATE endpoints SET balanceText = :text, balanceAt = :at, balanceError = :error, " +
            "authToken = :token, tokenExpiresAt = :expiresAt, siteCookie = :cookie, " +
            "siteUserId = :userId WHERE id = :id"
    )
    suspend fun setBalanceAndCookie(
        id: Long,
        text: String,
        at: Long,
        error: String,
        token: String,
        expiresAt: Long,
        cookie: String,
        userId: String,
    )

    @Query("SELECT COUNT(*) FROM endpoints")
    fun observeCount(): Flow<Int>
}
