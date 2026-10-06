package dev.penned.app.ui.assets

import dev.penned.app.data.db.PennedDatabase

/**
 * 账户删除的唯一入口（资产页左滑、账户编辑页删除共用）。
 *
 * 名下有流水的账户不给删：删了流水外键会悬空，净资产凭空少掉这笔钱，而流水还留在列表和统计里。
 * 返回 null 表示已软删；返回文案表示被拦下，由调用方提示（提示里带流水条数，别只说"不能删"）。
 */
internal suspend fun deleteAccountIfEmpty(
    db: PennedDatabase,
    accountId: String,
    accountName: String,
    now: Long = System.currentTimeMillis(),
): String? {
    val pending = db.transactionDao().countByAccount(accountId)
    if (pending > 0) return "「$accountName」还有 $pending 笔流水，先处理流水再删除"
    db.accountDao().softDelete(accountId, now)
    return null
}
