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
