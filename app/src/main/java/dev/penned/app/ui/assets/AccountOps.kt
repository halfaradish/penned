package dev.penned.app.ui.assets

import dev.penned.app.data.db.PennedDatabase
import dev.penned.app.data.db.dao.AccountWithBalance
import dev.penned.app.data.db.entity.AccountEntity
import dev.penned.app.domain.AccountType

/**
 * 账户大类：资产页的分组标题、添加/编辑账户页的分段 tab 共用一套口径（M1 S4 的四类）。
 *
 * 大类不能直接用 [AccountType] 表达——现金与储蓄卡同属"资金账户"，
 * 所以两套粒度分开：大类管分组与 tab，类型管副标题与落库默认值（图标不再按类型上色，AS 轮）。
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

/** 资产页「已停用」段标题（M3-9）：停用账户收在这里、默认折叠。 */
const val ARCHIVED_SECTION_TITLE = "已停用"

/** 资产页把账户分成「在用 / 已停用」两拨（净资产两边都算，只是显示分开）。 */
internal fun partitionArchived(
    accounts: List<AccountWithBalance>,
): Pair<List<AccountWithBalance>, List<AccountWithBalance>> =
    accounts.filterNot { it.archived } to accounts.filter { it.archived }

/**
 * 选择器里可见的账户（M3-9）：**停用的不列**，但**当前已选中的那个要留着**——
 * 编辑一条用停用账户记的流水时，选择器里看不到它就只能改成别的账户（UI-13 同型坑）。
 */
internal fun selectableAccounts(
    accounts: List<AccountEntity>,
    keepIds: Collection<String?>,
): List<AccountEntity> = accounts.filter { !it.archived || it.id in keepIds }

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
