package com.opson.libraries.storage.impl.db

import com.opson.libraries.diary.db.CustomFoodDao
import com.opson.libraries.diary.db.DayRecordDao
import com.opson.libraries.diary.db.LogEntryDao
import com.opson.libraries.diary.db.MealFeelingDao
import com.opson.libraries.diary.db.OutboxDao
import com.opson.libraries.diary.db.SavedMealDao
import com.opson.libraries.diary.db.SyncStateDao
import com.opson.libraries.diary.db.WeighInDao
import com.opson.libraries.foods.db.FoodCacheDao
import com.opson.libraries.opson.storage.db.ClearableDao
import com.opson.libraries.opson.storage.db.ExampleUserDataDao
import me.tatarka.inject.annotations.Inject
import software.amazon.lastmile.kotlin.inject.anvil.AppScope
import software.amazon.lastmile.kotlin.inject.anvil.ContributesBinding
import software.amazon.lastmile.kotlin.inject.anvil.SingleIn

/**
 * The pattern for exposing a DAO to the DI graph: delegate to the database
 * provider AND contribute the DAO into the [ClearableDao] multibinding set so
 * `UserScopedDaoCleaner` wipes it on user change. Copy this pair of
 * annotations for every user-scoped DAO you add.
 */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = ExampleUserDataDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideExampleUserDataDao @Inject constructor(
    provider: AppDatabaseProvider
) : ExampleUserDataDao by provider.database.exampleUserDataDao()

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = LogEntryDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideLogEntryDao @Inject constructor(
    provider: AppDatabaseProvider
) : LogEntryDao by provider.database.logEntryDao()

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = DayRecordDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideDayRecordDao @Inject constructor(
    provider: AppDatabaseProvider
) : DayRecordDao by provider.database.dayRecordDao()

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = MealFeelingDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideMealFeelingDao @Inject constructor(
    provider: AppDatabaseProvider
) : MealFeelingDao by provider.database.mealFeelingDao()

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = WeighInDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideWeighInDao @Inject constructor(
    provider: AppDatabaseProvider
) : WeighInDao by provider.database.weighInDao()

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = CustomFoodDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideCustomFoodDao @Inject constructor(
    provider: AppDatabaseProvider
) : CustomFoodDao by provider.database.customFoodDao()

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = SavedMealDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideSavedMealDao @Inject constructor(
    provider: AppDatabaseProvider
) : SavedMealDao by provider.database.savedMealDao()

/** The outbox is account-scoped by definition: a user change must never push one user's changes as another's. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = OutboxDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideOutboxDao @Inject constructor(
    provider: AppDatabaseProvider
) : OutboxDao by provider.database.outboxDao()

/** Cleared with the rest so the next user's first sync pulls from the start. */
@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = SyncStateDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideSyncStateDao @Inject constructor(
    provider: AppDatabaseProvider
) : SyncStateDao by provider.database.syncStateDao()

@SingleIn(AppScope::class)
@ContributesBinding(AppScope::class, boundType = FoodCacheDao::class)
@ContributesBinding(AppScope::class, boundType = ClearableDao::class, multibinding = true)
class ProvideFoodCacheDao @Inject constructor(
    provider: AppDatabaseProvider
) : FoodCacheDao by provider.database.foodCacheDao()
