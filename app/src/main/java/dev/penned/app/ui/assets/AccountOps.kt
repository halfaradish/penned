package dev.penned.app.ui.assets

import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.db.dao.AccountWithBalance
import dev.penned.app.domain.AccountType

/**
 * 账户大类：资产页的分组标题、添加/编辑账户页的分段 tab 共用一套口径（M1 S4 的四类）。
 *
 * 大类不能直接用 [AccountType] 表达——现金与储蓄卡同属"资金账户"，
 * 而列表行又按 [AccountType] 上色，所以两套粒度分开：大类管分组与 tab，类型管配色与副标题。
 */
enum class AccountGroup(val tab: String, val title: String, val defaultType: AccountType) {
    FUNDS("资金", "资金账户", AccountType.DEBIT),
    CREDIT("信贷", "信贷账户", AccountType.CREDIT),
    INVEST("理财", "投资理财", AccountType.INVEST),
    DEBT("负债", "负债", AccountType.DEBT);

    companion object {
        fun of(type: AccountType): AccountGroup = when (type) {
            AccountType.CASH, AccountType.DEBIT -> FUNDS
            AccountType.CREDIT -> CREDIT
            AccountType.INVEST -> INVEST
            AccountType.DEBT -> DEBT
        }
    }
}

/** 添加账户的类型预设（对标一木：每组图标格 + 自定义）。 */
data class AccountPreset(val label: String, val type: AccountType)

val ACCOUNT_PRESETS: Map<AccountGroup, List<AccountPreset>> = linkedMapOf(
    AccountGroup.FUNDS to listOf(
        AccountPreset("现金", AccountType.CASH),
        AccountPreset("储蓄卡", AccountType.DEBIT),
        AccountPreset("支付宝", AccountType.DEBIT),
        AccountPreset("微信钱包", AccountType.DEBIT),
    ),
    AccountGroup.CREDIT to listOf(
        AccountPreset("信用卡", AccountType.CREDIT),
        AccountPreset("花呗", AccountType.CREDIT),
    ),
    AccountGroup.INVEST to listOf(AccountPreset("理财账户", AccountType.INVEST)),
    AccountGroup.DEBT to listOf(AccountPreset("欠款", AccountType.DEBT)),
)

/** 账户类型的固定颜料槽：信用卡/负债红系、现金赭石、储蓄卡石青、理财石绿。 */
internal fun accountPigmentSlot(type: AccountType): Int = when (type) {
    AccountType.DEBT -> 6
    AccountType.CREDIT -> 0
    AccountType.CASH -> 1
    AccountType.INVEST -> 3
    AccountType.DEBIT -> 4
}

/** 资产页分组标题：groupName 有值优先（留给以后的自定义分组），否则按账户大类推导。 */
internal fun accountGroupTitle(groupName: String?, type: AccountType): String =
    groupName ?: AccountGroup.of(type).title

/** 从「大类 + 预设名」得到落库类型：预设匹配不上（自定义格）就用该大类的默认类型。 */
internal fun accountTypeFor(group: AccountGroup, presetLabel: String?): AccountType =
    presetLabel
        ?.let { label -> ACCOUNT_PRESETS[group].orEmpty().firstOrNull { it.label == label }?.type }
        ?: group.defaultType

/** 资产页分组顺序：资金 → 信贷 → 理财 → 负债；groupName 自定义分组排在已知大类之后。 */
internal fun groupAccounts(
    accounts: List<AccountWithBalance>,
): List<Pair<String, List<AccountWithBalance>>> {
    val groups = accounts.groupBy { accountGroupTitle(it.groupName, it.type) }
    val known = AccountGroup.entries.map { it.title }.filter { it in groups }
    val custom = groups.keys.filterNot { key -> AccountGroup.entries.any { it.title == key } }
    return (known + custom).map { title -> title to groups.getValue(title) }
}

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
