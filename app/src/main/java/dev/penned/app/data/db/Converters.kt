package dev.penned.app.data.db

import androidx.room.TypeConverter
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.PendingKind
import dev.penned.app.domain.PendingStatus
import dev.penned.app.domain.PendingType
import dev.penned.app.domain.RecurringPeriod
import dev.penned.app.domain.ReimburseState
import dev.penned.app.domain.RuleTargetType
import dev.penned.app.domain.TxSource
import dev.penned.app.domain.TxType

class Converters {
    @TypeConverter fun txTypeToString(v: TxType): String = v.name
    @TypeConverter fun stringToTxType(s: String): TxType = TxType.valueOf(s)

    @TypeConverter fun categoryTypeToString(v: CategoryType): String = v.name
    @TypeConverter fun stringToCategoryType(s: String): CategoryType = CategoryType.valueOf(s)

    @TypeConverter fun accountTypeToString(v: AccountType): String = v.name
    @TypeConverter fun stringToAccountType(s: String): AccountType = AccountType.valueOf(s)

    @TypeConverter fun reimburseStateToString(v: ReimburseState): String = v.name
    @TypeConverter fun stringToReimburseState(s: String): ReimburseState = ReimburseState.valueOf(s)

    @TypeConverter fun txSourceToString(v: TxSource): String = v.name
    @TypeConverter fun stringToTxSource(s: String): TxSource = TxSource.valueOf(s)

    @TypeConverter fun recurringPeriodToString(v: RecurringPeriod): String = v.name
    @TypeConverter fun stringToRecurringPeriod(s: String): RecurringPeriod = RecurringPeriod.valueOf(s)

    @TypeConverter fun debtDirectionToString(v: DebtDirection): String = v.name
    @TypeConverter fun stringToDebtDirection(s: String): DebtDirection = DebtDirection.valueOf(s)

    // M4-0（D100）：关键词规则与候选池的三个枚举。**加值不改 schema**（一律 TEXT + `.name`），
    // 所以 `TxSource.NOTIFICATION`（M4-8）与这里的枚举日后扩值都不需要迁移。
    @TypeConverter fun ruleTargetTypeToString(v: RuleTargetType): String = v.name
    @TypeConverter fun stringToRuleTargetType(s: String): RuleTargetType = RuleTargetType.valueOf(s)

    @TypeConverter fun pendingTypeToString(v: PendingType): String = v.name
    @TypeConverter fun stringToPendingType(s: String): PendingType = PendingType.valueOf(s)

    @TypeConverter fun pendingKindToString(v: PendingKind): String = v.name
    @TypeConverter fun stringToPendingKind(s: String): PendingKind = PendingKind.valueOf(s)

    @TypeConverter fun pendingStatusToString(v: PendingStatus): String = v.name
    @TypeConverter fun stringToPendingStatus(s: String): PendingStatus = PendingStatus.valueOf(s)
}
