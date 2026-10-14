package dev.penned.app.data.db.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.penned.app.data.db.entity.AssetSnapshotEntity
import dev.penned.app.domain.InvestSnapshot
import kotlinx.coroutines.flow.Flow

/**
 * 理财快照（M3-10；表 M3-0 就建好）。
 * 口径在 `InvestMath`（总额 = 最近快照 / 无快照退回余额；成本 = 初始余额 + 净转入）；这里只管存取。
 * **软删快照不碰任何流水**（快照只是"某天的现值"记录）。
 */
@Dao
interface SnapshotDao {

    @Query("SELECT * FROM asset_snapshots WHERE deleted = 0 AND accountId = :accountId ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeByAccount(accountId: String): Flow<List<AssetSnapshotEntity>>

    /** 本账本可见账户的全部快照（资产页理财卡按账户取最近一条）。 */
    @Query(
        """
        SELECT s.* FROM asset_snapshots s
        INNER JOIN accounts a ON a.id = s.accountId
        WHERE s.deleted = 0 AND a.deleted = 0 AND (
            a.bookId = :bookId OR a.scopeAllBooks = 1
            OR EXISTS (SELECT 1 FROM account_books r WHERE r.accountId = a.id AND r.bookId = :bookId AND r.deleted = 0)
        )
        ORDER BY s.dateEpochDay DESC, s.createdAt DESC
        """
    )
    fun observeForBook(bookId: String): Flow<List<AssetSnapshotEntity>>

    @Query("SELECT * FROM asset_snapshots WHERE id = :id")
    suspend fun get(id: String): AssetSnapshotEntity?

    @Upsert
    suspend fun upsert(snapshot: AssetSnapshotEntity)

    @Query("UPDATE asset_snapshots SET deleted = 1, updatedAt = :now WHERE id = :id")
    suspend fun softDelete(id: String, now: Long)

    @Query("SELECT COUNT(*) FROM asset_snapshots WHERE deleted = 0 AND accountId = :accountId")
    suspend fun countForAccount(accountId: String): Int
}

/** 快照行 → 域层口径（最近一条按日期比）。 */
fun AssetSnapshotEntity.toInvestSnapshot(): InvestSnapshot = InvestSnapshot(dateEpochDay, valueCents)
