package dev.penned.app.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 理财账户快照（M3-10）：某个理财账户在某一日的现值。
 * 口径（D004 的推广）：理财「总额」= 最近一次快照值（无快照则退回余额重算），「盈亏」= 快照值 − 成本（纯函数）。
 */
@Entity(tableName = "asset_snapshots", indices = [Index("accountId")])
data class AssetSnapshotEntity(
    @PrimaryKey val id: String,
    val accountId: String,
    /** 快照日（epochDay，本地墙钟）。 */
    val dateEpochDay: Long,
    val valueCents: Long,
    val note: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val deleted: Boolean = false,
    val deviceId: String,
)
