package dev.penned.app.domain

enum class TxType { EXPENSE, INCOME, TRANSFER }

enum class CategoryType { EXPENSE, INCOME }

enum class AccountType { CASH, DEBIT, CREDIT, INVEST, DEBT }

enum class ReimburseState { NONE, PENDING, DONE }

/** 记录方式（D021）：流水来源 */
enum class TxSource { MANUAL, VOICE, TEXT, SCREENSHOT, IMPORT, AUTO }

/** 周期记账的期次单位（M3-6）。 */
enum class RecurringPeriod { DAILY, WEEKLY, MONTHLY, YEARLY }

/** 债务方向（D068）：BORROW = 我欠人（应付），LEND = 人欠我（应收）。 */
enum class DebtDirection { BORROW, LEND }

/**
 * 记一笔「债务」Tab 的两格（M3-4）：[OPEN] = 债务发生（我借进来 / 我借出去），
 * [SETTLE] = 债务清偿（我还款 / 对方把还款给我）。钱往哪边走由它与 [DebtDirection] 共同决定
 * （`DebtMath.flowType`）——同样是"借入"这一格，欠款与人欠我两面的资金方向正好相反。
 */
enum class DebtAction { OPEN, SETTLE }
