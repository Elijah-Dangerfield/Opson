package com.opson.integration.helpers

import com.opson.features.home.impl.HomeViewModel
import com.opson.features.upgrade.AppGuard
import com.opson.features.upgrade.AppGuardState
import com.opson.features.upgrade.impl.AppGuardImpl
import com.opson.features.upgrade.impl.AppGuardTelemetry
import com.opson.libraries.config.AppConfigFlow
import com.opson.libraries.config.AppConfigRepository
import com.opson.libraries.config.impl.data.ConfigCacheImpl
import com.opson.libraries.config.impl.data.ConfigOverrideRepositoryImpl
import com.opson.libraries.config.impl.data.RemoteConfigRemoteDataSource
import com.opson.libraries.config.impl.model.FallbackConfigMap
import com.opson.libraries.config.impl.repository.OfflineFirstAppConfigRepository
import com.opson.libraries.config.impl.serialization.ConfigJsonConverter
import com.opson.libraries.core.AppState
import com.opson.libraries.core.AuthGate
import com.opson.libraries.core.AuthRequirement
import com.opson.libraries.core.AuthVerdict
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.entitlements.impl.BetaMembership
import com.opson.libraries.diary.DataExport
import com.opson.libraries.diary.DayCheckIns
import com.opson.libraries.diary.DayWrite
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.ExerciseRepository
import com.opson.libraries.diary.FastingReleaseFlag
import com.opson.libraries.diary.FastingRepository
import com.opson.libraries.diary.LogDraft
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.Precision
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.WeightRepository
import com.opson.libraries.diary.impl.AuthActiveUserSource
import com.opson.libraries.diary.impl.CustomFoodRepositoryImpl
import com.opson.libraries.diary.impl.DiaryRepositoryImpl
import com.opson.libraries.diary.impl.DiarySyncApi
import com.opson.libraries.diary.impl.DiarySyncer
import com.opson.libraries.diary.impl.DiaryTables
import com.opson.libraries.diary.impl.DiaryWriter
import com.opson.libraries.diary.impl.ExerciseRepositoryImpl
import com.opson.libraries.diary.impl.FastingRepositoryImpl
import com.opson.libraries.diary.impl.HttpDataExport
import com.opson.libraries.diary.impl.HttpDiarySyncApi
import com.opson.libraries.diary.impl.SavedMealRepositoryImpl
import com.opson.libraries.diary.impl.SettingsRepositoryImpl
import com.opson.libraries.diary.impl.SettingsStore
import com.opson.libraries.diary.impl.SystemLocalCalendar
import com.opson.libraries.diary.impl.WeightRepositoryImpl
import com.opson.libraries.flowroutines.AppCoroutineScope
import com.opson.libraries.flowroutines.DefaultDispatcherProvider
import com.opson.libraries.foods.BarcodeLinkReporter
import com.opson.libraries.foods.BarcodeMissReporter
import com.opson.libraries.foods.FoodLabels
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.MealPhotoEstimator
import com.opson.libraries.foods.impl.FoodCatalogImpl
import com.opson.libraries.foods.impl.HttpBarcodeLinkReporter
import com.opson.libraries.foods.impl.HttpBarcodeMissReporter
import com.opson.libraries.foods.impl.HttpFoodLabels
import com.opson.libraries.foods.impl.HttpFoodApi
import com.opson.libraries.foods.impl.HttpMealPhotoEstimator
import com.opson.libraries.foods.impl.SubmittedFoodsStore
import com.opson.libraries.identity.auth.AuthRepository
import com.opson.libraries.identity.impl.HttpProfileApi
import com.opson.libraries.identity.impl.auth.GatewayAuthTokenProvider
import com.opson.libraries.identity.impl.auth.ExpiredAccountStore
import com.opson.libraries.identity.impl.auth.FallbackDisplayNameStore
import com.opson.libraries.identity.impl.auth.SupabaseAuthRepositoryImpl
import com.opson.libraries.identity.impl.profile.PendingProfileEditStore
import com.opson.libraries.identity.impl.profile.ProfileCache
import com.opson.libraries.identity.impl.profile.ProfileRepositoryImpl
import com.opson.libraries.identity.impl.profile.UserScopedProfileCacheCleaner
import com.opson.libraries.identity.profile.ProfileRepository
import com.opson.libraries.networking.AccessDeniedBus
import com.opson.libraries.networking.ClientHeaders
import com.opson.libraries.networking.ClientHeadersProvider
import com.opson.libraries.networking.InstallIdProvider
import com.opson.libraries.networking.NetworkConfig
import com.opson.libraries.networking.SessionIdProvider
import com.opson.libraries.networking.impl.AccessDeniedBusImpl
import com.opson.libraries.networking.impl.BearerTokenInvalidator
import com.opson.libraries.networking.impl.DefaultClientHeadersProvider
import com.opson.libraries.networking.impl.NetworkClientImpl
import com.opson.libraries.networking.impl.NetworkReachabilityImpl
import com.opson.libraries.networking.impl.SessionRejectionBusImpl
import com.opson.libraries.opson.AppEvents
import com.opson.libraries.opson.impl.AppEventDispatcher
import com.opson.libraries.opson.impl.DefaultUserScopedDataReset
import com.opson.libraries.storage.impl.UserScopedDaoCleaner
import com.opson.libraries.storage.impl.db.AppDatabase
import com.opson.libraries.storage.impl.db.AppDatabaseProvider
import com.opson.libraries.storage.impl.db.RealAppDatabaseProvider
import com.opson.libraries.storage.impl.db.RoomDatabaseTransactor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import java.util.UUID

/**
 * One phone: the app's real code from the repositories down — the diary and its
 * outbox, the sync engine, settings, fasting, the food catalog, photo estimates,
 * pack labels and food submissions,
 * auth and the user-change reset, remote config, the network client and its
 * DTOs — over the app's real [AppDatabase] (in memory) and pointed at
 * [serverUrl]. Each part is built the way the app's DI graph builds it;
 * TestClient only does the graph's job by hand.
 *
 * The seams that stay fake are the ones the phone itself owns (see
 * DeviceSeams.kt): the Supabase SDK, on-disk caches, the lifecycle and the
 * clock. A new TestClient is a fresh install. [signedInAs] null is an install
 * nobody has signed in on yet; otherwise the Supabase SDK already holds a
 * session for that user, as after a sign-in.
 */
class TestClient(
    serverUrl: String,
    signedInAs: String?,
    newAccount: () -> String,
    val clock: TestClock = TestClock(),
    requestTimeoutMillis: Long = 30_000,
    /** Wraps the `/v1/sync` client, to make this device speak an older wire format. */
    wrapSync: (DiarySyncApi) -> DiarySyncApi = { it },
    /**
     * Rewrites what this device says about itself on every request: another platform, an
     * older build. The JVM has one [com.opson.libraries.core.BuildInfo], so a phone that is
     * an iPhone, or last month's build, is this.
     */
    device: (ClientHeaders) -> ClientHeaders = { it },
    /**
     * Runs on the Supabase stand-in before anything can use it, for a session that starts with
     * an expired or foreign token, or whose refresh Supabase refuses. Later is too late: the
     * network layer loads the token once and keeps it until a 401.
     */
    setUpSession: (StubSupabaseGateway) -> Unit = {},
) {
    // One app-lifetime scope per client. Every background collector lives here
    // so [close] can quiesce a client's work before the server stops.
    private val dispatchers = DefaultDispatcherProvider()
    private val appScope = AppCoroutineScope(dispatchers)
    private val cacheFactory = InMemoryCacheFactory()
    val lifecycle = TestLifecycle()

    // ── Network ──────────────────────────────────────────────────────────────
    val gateway = StubSupabaseGateway(signedInAs, newAccount).also(setUpSession)
    private val sessionRejectionBus = SessionRejectionBusImpl()

    /** Where a ban's 403 lands; the app shell routes from it to the access-denied screen. */
    val accessDenied: AccessDeniedBus = AccessDeniedBusImpl()
    private val tokenProvider = GatewayAuthTokenProvider(gateway, sessionRejectionBus)
    private val networkClient = NetworkClientImpl(
        config = object : NetworkConfig {
            override val baseUrl: String = serverUrl
            override val requestTimeoutMillis: Long = requestTimeoutMillis
        },
        tokenProvider = tokenProvider,
        headersProvider = DefaultClientHeadersProvider(InstallId(), SessionId()).let { real ->
            object : ClientHeadersProvider {
                override fun current(): ClientHeaders = device(real.current())
            }
        },
        reachability = NetworkReachabilityImpl(appScope),
        accessDeniedBus = accessDenied,
        sessionRejectionBus = sessionRejectionBus,
        authGate = { AlwaysReadyAuthGate },
    )

    // ── Device database ──────────────────────────────────────────────────────
    val database: AppDatabase = RealAppDatabaseProvider(InMemoryAppDatabase, dispatchers).database
    private val transactor = RoomDatabaseTransactor(
        object : AppDatabaseProvider {
            override val database: AppDatabase = this@TestClient.database
        },
    )
    val tables = DiaryTables(
        entries = database.logEntryDao(),
        days = database.dayRecordDao(),
        feelings = database.mealFeelingDao(),
        weighIns = database.weighInDao(),
        customFoods = database.customFoodDao(),
        savedMeals = database.savedMealDao(),
        fasts = database.fastDao(),
        exercises = database.exerciseDao(),
        outbox = database.outboxDao(),
        syncState = database.syncStateDao(),
        transactor = transactor,
        repeats = database.repeatRuleDao(),
        repeatLogs = database.repeatLogDao(),
    )

    // ── Auth and who is signed in ────────────────────────────────────────────
    // The app hands AuthActiveUserSource a provider because the auth repository
    // depends (through the user-change reset) on stores that depend on it. The
    // provider is first called from a coroutine that can start before this
    // constructor reaches the repository, so it waits for it.
    private val authReady = CompletableDeferred<AuthRepository>()
    private val activeUser = AuthActiveUserSource({ runBlocking { authReady.await() } }, appScope)

    // ── Remote config ────────────────────────────────────────────────────────
    private val configJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
    private val configConverter = ConfigJsonConverter(configJson)
    private val configCache = ConfigCacheImpl(cacheFactory)
    private val appEventBus = AppEventDispatcher(listeners = emptySet(), appLifecycle = lifecycle)
    private val configRepository = OfflineFirstAppConfigRepository(
        dispatcherProvider = dispatchers,
        remoteConfigDataSource = RemoteConfigRemoteDataSource(dispatchers, networkClient, tokenProvider, configConverter),
        configCache = configCache,
        converter = configConverter,
        fallbackConfig = BundledFallback(configConverter),
        configOverrideRepository = ConfigOverrideRepositoryImpl(configCache, configConverter, appScope),
        appEvents = AppEvents(appEventBus),
        clock = clock,
        appScope = appScope,
    )
    val appConfig: AppConfigRepository = configRepository
    val appConfigFlow = AppConfigFlow(appConfig)
    val fastingRelease = FastingReleaseFlag(appConfig.config())

    // ── The app guard ────────────────────────────────────────────────────────
    /** Whether the phone believes it is offline, which the guard reads. A device seam: tests flip it. */
    val offline = MutableStateFlow(false)
    val guard: AppGuard = AppGuardImpl(
        appConfigFlow = appConfigFlow,
        appState = object : AppState {
            override val isOffline: StateFlow<Boolean> = offline
        },
        appEvents = AppEvents(appEventBus),
        refresher = configRepository,
        telemetry = object : AppGuardTelemetry {
            override fun shown(state: AppGuardState) = Unit
            override fun storeOpened(from: AppGuardState) = Unit
            override fun updateDismissed() = Unit
        },
        appScope = appScope,
    )

    // ── Diary, settings, sync ────────────────────────────────────────────────
    private val calendar = SystemLocalCalendar(clock)
    private val settingsStore = SettingsStore(cacheFactory, tables.outbox, transactor, clock, activeUser, appScope) { null }
    val syncApi: DiarySyncApi = wrapSync(HttpDiarySyncApi(networkClient))
    val sync = DiarySyncer(syncApi, tables, settingsStore, activeUser, appScope, clock, cacheFactory)
    private val writer = DiaryWriter(tables, clock, sync)
    val settings: SettingsRepository = SettingsRepositoryImpl(settingsStore, sync)
    private val fastingImpl = FastingRepositoryImpl(tables, writer, settings, fastingRelease, calendar, clock)
    val fasting: FastingRepository = fastingImpl
    private val diaryImpl = DiaryRepositoryImpl(tables, writer, settings, calendar, fastEnder = fastingImpl)
    val diary: DiaryRepository = diaryImpl
    val checkIns: DayCheckIns = diaryImpl
    val weights: WeightRepository = WeightRepositoryImpl(tables, writer)
    val customFoods: CustomFoodRepository = CustomFoodRepositoryImpl(tables, writer)
    val savedMeals: SavedMealRepository = SavedMealRepositoryImpl(tables, writer)
    val exercise: ExerciseRepository = ExerciseRepositoryImpl(tables, writer)
    val export: DataExport = HttpDataExport(networkClient, calendar)

    // ── Foods ────────────────────────────────────────────────────────────────
    val foods: FoodCatalog = FoodCatalogImpl(HttpFoodApi(networkClient), database.foodCacheDao(), clock)
    val photoEstimator: MealPhotoEstimator = HttpMealPhotoEstimator(networkClient)
    val barcodeMisses: BarcodeMissReporter = HttpBarcodeMissReporter(networkClient, appScope)
    val foodLabels: FoodLabels = HttpFoodLabels(networkClient)
    val submittedFoods = SubmittedFoodsStore(cacheFactory, networkClient, appScope)
    val barcodeLinks: BarcodeLinkReporter = HttpBarcodeLinkReporter(networkClient, appScope)

    // ── Account ──────────────────────────────────────────────────────────────
    /** `release.betaNotice` as this phone reads it. Off, as in a store build until the owner turns it on. */
    var betaOn: Boolean = false
    val betaMembership = BetaMembership(cacheFactory, networkClient) { betaOn }

    private val profileApi = HttpProfileApi(networkClient)
    private val profileCache = ProfileCache(cacheFactory)
    val auth: AuthRepository = SupabaseAuthRepositoryImpl(
        gateway = gateway,
        profileApi = profileApi,
        appEventBus = appEventBus,
        userScopedDataReset = DefaultUserScopedDataReset(
            clearers = setOf(
                UserScopedDaoCleaner(
                    setOf(
                        database.exampleUserDataDao(), database.logEntryDao(), database.dayRecordDao(),
                        database.mealFeelingDao(), database.weighInDao(), database.customFoodDao(),
                        database.savedMealDao(), database.outboxDao(), database.syncStateDao(),
                        database.foodCacheDao(), database.fastDao(), database.exerciseDao(),
                    ),
                ),
                settingsStore,
                UserScopedProfileCacheCleaner(profileCache),
            ),
            stoppers = setOf(sync),
        ),
        tokenInvalidator = BearerTokenInvalidator(networkClient),
        sessionRejectionBus = sessionRejectionBus,
        expiredAccounts = ExpiredAccountStore(cacheFactory),
        appScope = appScope,
    ).also { authReady.complete(it) }

    /**
     * The real repository over the real API — its init collector resolves the
     * profile from `/v1/me` as soon as auth emits Authenticated.
     */
    val profileRepository: ProfileRepository = ProfileRepositoryImpl(
        authRepository = auth,
        profileApi = profileApi,
        profileCache = profileCache,
        fallbackDisplayNameStore = FallbackDisplayNameStore(cacheFactory),
        pendingProfileEditStore = PendingProfileEditStore(cacheFactory),
        appScope = appScope,
    )

    /** The signed-in user's id, or null. */
    val userId: String? get() = gateway.currentSession()?.userId

    /** Build the real home VM for this user. Mirrors how the entry point constructs it. */
    fun homeVm(): HomeViewModel = HomeViewModel(profileRepository)

    fun today(): LocalDate = calendar.today()

    /**
     * Push and pull now, the way pull-to-refresh does. Waits for auth to have
     * resolved first, as the app's sync coordinator does: a phone never syncs
     * before it knows who is signed in.
     */
    suspend fun syncNow(): Result<Unit> {
        auth.current()
        return sync.syncNow()
    }

    /** Logs one food the way the logger's amount sheet does. */
    suspend fun log(
        name: String,
        kcal: Double,
        day: LocalDate = today(),
        slot: MealSlot = MealSlot.Lunch,
        foodId: String? = null,
        method: LogMethod = LogMethod.Search,
    ): LogItem = diary.addItems(
        day = day,
        slot = slot,
        items = listOf(LogDraft(name = name, precision = Precision.Estimate, foodId = foodId, amountText = "1 bowl", grams = 300.0, kcal = kcal)),
        method = method,
    ).written().single()

    /** Changes waiting in the outbox. */
    suspend fun pendingChanges(): Int = tables.outbox.count()

    /** Cancel this client's background work and close its database. */
    fun close() {
        appScope.cancel()
        database.close()
    }

    /** The shipped fallback is `{}`; read from compose resources on a phone, which the host JVM has none of. */
    private class BundledFallback(converter: ConfigJsonConverter) : FallbackConfigMap(converter) {
        override val map: Map<String, *> = emptyMap<String, Any?>()
    }

    private class InstallId : InstallIdProvider {
        private val id = UUID.randomUUID().toString()
        override fun current(): String = id
    }

    private class SessionId : SessionIdProvider {
        private val id = UUID.randomUUID().toString()
        override fun current(): String = id
    }
}

internal fun randomUserId(): String = UUID.randomUUID().toString()

/** The suite's default: never gates — every authed call fires against the real server. */
internal object AlwaysReadyAuthGate : AuthGate {
    override fun verdict(requirement: AuthRequirement): AuthVerdict = AuthVerdict.Ready
    override suspend fun awaitVerdict(requirement: AuthRequirement): AuthVerdict = AuthVerdict.Ready
}

/** What a write to an open day wrote. A closed day here is a test that went wrong. */
fun <T> DayWrite<T>.written(): T = when (this) {
    is DayWrite.Written -> value
    is DayWrite.DayClosed -> error("$day is closed")
}
