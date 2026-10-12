package dev.penned.app.data.db

import androidx.room.TypeConverter
import dev.penned.app.domain.AccountType
import dev.penned.app.domain.CategoryType
import dev.penned.app.domain.DebtDirection
import dev.penned.app.domain.RecurringPeriod
import dev.penned.app.domain.ReimburseState
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
}
