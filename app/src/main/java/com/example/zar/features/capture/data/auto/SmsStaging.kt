package com.example.zar.features.capture.data.auto

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import com.example.zar.core.domain.model.UserId
import com.example.zar.features.capture.domain.auto.SmsStagingRepository
import com.example.zar.features.capture.domain.auto.StagedMessage
import com.example.zar.features.capture.domain.auto.StagingQueue
import java.security.KeyStore
import java.time.Duration
import java.time.Instant
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Encrypts staged message text with an AES-GCM key kept in the Android Keystore. The key never
 * leaves the phone, so the database is unreadable if copied off it.
 */
@Singleton
class SmsCipher @Inject constructor() {

    fun encrypt(text: String): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return cipher.iv + cipher.doFinal(text.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(bytes: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        }
        return String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES), Charsets.UTF_8)
    }

    @Synchronized
    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE).apply {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
        }.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "zar_sms_staging"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}

/** One staged message. [ciphertext] is null once the message expired: only the notice is left. */
@Entity(tableName = "staged_sms")
data class StagedSmsEntity(
    @PrimaryKey val id: String,
    val owner: String,
    val ciphertext: ByteArray?,
    @ColumnInfo(name = "received_at") val receivedAt: Long,
)

data class QueueRow(
    @ColumnInfo(name = "received_at") val receivedAt: Long,
    val expired: Boolean,
)

@Dao
interface StagedSmsDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: StagedSmsEntity): Long

    @Query("SELECT * FROM staged_sms WHERE owner = :owner AND ciphertext IS NOT NULL ORDER BY received_at")
    suspend fun waiting(owner: String): List<StagedSmsEntity>

    @Query("DELETE FROM staged_sms WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE staged_sms SET ciphertext = NULL WHERE ciphertext IS NOT NULL AND received_at < :cutoff")
    suspend fun expire(cutoff: Long): Int

    @Query("UPDATE staged_sms SET ciphertext = NULL WHERE id = :id")
    suspend fun expireOne(id: String)

    @Query("DELETE FROM staged_sms WHERE ciphertext IS NULL AND received_at < :cutoff")
    suspend fun dropOldNotices(cutoff: Long)

    @Query("DELETE FROM staged_sms WHERE owner = :owner AND ciphertext IS NOT NULL")
    suspend fun discardWaiting(owner: String)

    @Query("DELETE FROM staged_sms WHERE owner = :owner AND ciphertext IS NULL")
    suspend fun dismissExpired(owner: String)

    @Query("SELECT received_at, ciphertext IS NULL AS expired FROM staged_sms WHERE owner = :owner ORDER BY received_at")
    fun observe(owner: String): Flow<List<QueueRow>>
}

@Database(entities = [StagedSmsEntity::class], version = 1, exportSchema = false)
abstract class CaptureStagingDatabase : RoomDatabase() {
    abstract fun stagedSms(): StagedSmsDao

    companion object {
        /** Excluded from backups in res/xml: the key can't be restored, so a copy is useless anyway. */
        const val NAME = "capture_staging.db"
    }
}

@Singleton
class RoomSmsStagingRepository @Inject constructor(
    private val dao: StagedSmsDao,
    private val cipher: SmsCipher,
) : SmsStagingRepository {

    override suspend fun stage(message: StagedMessage): Boolean = dao.insert(
        StagedSmsEntity(message.id, message.owner.value, cipher.encrypt(message.text), message.receivedAt.toEpochMilli()),
    ) != -1L

    override suspend fun waiting(owner: UserId): List<StagedMessage> = dao.waiting(owner.value).mapNotNull { row ->
        val text = runCatching { cipher.decrypt(requireNotNull(row.ciphertext)) }.getOrNull()
        if (text == null) {
            // The key is gone (e.g. app data restored to another phone): it can never be read, so say so.
            dao.expireOne(row.id)
            null
        } else {
            StagedMessage(row.id, UserId(row.owner), text, Instant.ofEpochMilli(row.receivedAt))
        }
    }

    override suspend fun clear(id: String) = dao.delete(id)

    override suspend fun expire(cutoff: Instant): Int {
        // Notices are kept a few weeks after expiry, then dropped too.
        dao.dropOldNotices(cutoff.minus(NOTICE_KEPT).toEpochMilli())
        return dao.expire(cutoff.toEpochMilli())
    }

    override suspend fun discardWaiting(owner: UserId) = dao.discardWaiting(owner.value)

    override suspend fun dismissExpired(owner: UserId) = dao.dismissExpired(owner.value)

    override fun observe(owner: UserId): Flow<StagingQueue> = dao.observe(owner.value).map { rows ->
        StagingQueue(
            waiting = rows.count { !it.expired },
            expired = rows.filter { it.expired }.map { Instant.ofEpochMilli(it.receivedAt) },
        )
    }

    private companion object {
        val NOTICE_KEPT: Duration = Duration.ofDays(30)
    }
}
