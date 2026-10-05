package dev.penned.app.domain

enum class TxType { EXPENSE, INCOME, TRANSFER }

enum class CategoryType { EXPENSE, INCOME }

enum class AccountType { CASH, DEBIT, CREDIT, INVEST, DEBT }

enum class ReimburseState { NONE, PENDING, DONE }
