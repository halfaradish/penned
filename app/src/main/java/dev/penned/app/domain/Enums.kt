package dev.penned.app.domain

enum class TxType { EXPENSE, INCOME, TRANSFER }

enum class CategoryType { EXPENSE, INCOME }

enum class AccountType { CASH, DEBIT, CREDIT, INVEST, DEBT }

enum class ReimburseState { NONE, PENDING, DONE }

/** 记录方式（D021）：流水来源 */
enum class TxSource { MANUAL, VOICE, TEXT, SCREENSHOT, IMPORT, AUTO }
