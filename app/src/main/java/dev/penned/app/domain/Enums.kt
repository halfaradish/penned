package dev.penned.app.domain

enum class TxType { EXPENSE, INCOME, TRANSFER }

enum class CategoryType { EXPENSE, INCOME }

enum class AccountType { CASH, DEBIT, CREDIT, INVEST, DEBT }

enum class ReimburseState { NONE, PENDING, DONE }

/** 记录方式（D021）：流水来源 */
enum class TxSource { MANUAL, VOICE, TEXT, SCREENSHOT, IMPORT, AUTO }

/** 关键词规则的目标（M4-0，D100）：命中后填分类还是标签（双目标共用同一张规则表）。 */
enum class RuleTargetType { CATEGORY, TAG }

/** 候选池的收支方向（M4-0）：识别不出方向就是 UNKNOWN（**不猜**，留给用户确认时定）。 */
enum class PendingType { EXPENSE, INCOME, TRANSFER, UNKNOWN }

/** 候选池的来源（M4-0）：截图识别 / 通知监听。 */
enum class PendingKind { SCREENSHOT, NOTIFICATION }

/** 候选池状态（M4-0）：待确认 / 已入账 / 已丢弃（「清空已处理」清后两者，D104）。 */
enum class PendingStatus { PENDING, ACCEPTED, DISCARDED }

/** 周期记账的期次单位（M3-6）。 */
enum class RecurringPeriod { DAILY, WEEKLY, MONTHLY, YEARLY }

/** 攒钱目标的状态（M3-15）：ACTIVE = 还在攒；DONE = 用户手动标记达成（与"金额够了"不是一回事）。 */
enum class WishlistStatus { ACTIVE, DONE }

/** 债务方向（D068）：BORROW = 我欠人（应付），LEND = 人欠我（应收）。 */
enum class DebtDirection { BORROW, LEND }

/**
 * 记一笔「债务」Tab 的两格（M3-4）：[OPEN] = 债务发生（我借进来 / 我借出去），
 * [SETTLE] = 债务清偿（我还款 / 对方把还款给我）。钱往哪边走由它与 [DebtDirection] 共同决定
 * （`DebtMath.flowType`）——同样是"借入"这一格，欠款与人欠我两面的资金方向正好相反。
 */
enum class DebtAction { OPEN, SETTLE }
