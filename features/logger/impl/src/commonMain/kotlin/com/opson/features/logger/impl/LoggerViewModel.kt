package com.opson.features.logger.impl

import androidx.lifecycle.viewModelScope
import com.opson.features.logger.LoggerRoute
import com.opson.features.logger.LoggerStart
import com.opson.libraries.core.AppState
import com.opson.libraries.diary.CustomFoodRepository
import com.opson.libraries.diary.DiaryRepository
import com.opson.libraries.diary.FastingRelease
import com.opson.libraries.health.ExerciseRelease
import com.opson.libraries.diary.LocalCalendar
import com.opson.libraries.diary.LogItem
import com.opson.libraries.diary.LogMethod
import com.opson.libraries.diary.MealSlot
import com.opson.libraries.diary.MealSlotPicker
import com.opson.libraries.diary.SavedMealRepository
import com.opson.libraries.diary.SelectedDay
import com.opson.libraries.diary.SettingsRepository
import com.opson.libraries.diary.ui.sentenceName
import com.opson.libraries.flowroutines.SEAViewModel
import com.opson.libraries.foods.FoodCatalog
import com.opson.libraries.foods.foodRef
import com.opson.libraries.nutrition.Metric
import com.opson.libraries.nutrition.Visibility
import com.opson.libraries.nutrition.VisibilityPolicy
import com.opson.libraries.nutrition.VisibilityPolicySource
import com.opson.libraries.speech.Heard
import com.opson.libraries.speech.MicPermission
import com.opson.libraries.speech.SpeechAvailability
import com.opson.libraries.speech.SpeechFailure
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import me.tatarka.inject.annotations.Assisted
import me.tatarka.inject.annotations.Inject
import opson.libraries.resources.generated.resources.Res
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The add sheet (design sync 4, 13.4a and 13.4b): the grid of ways to add, Recent with a
 * one-tap +, and the Describe it and Saved meals panes. Whatever is logged here is logged at
 * once through [FoodLogger]; there is no pick list. Search, the scanner, the photo and the
 * rest open in the sheet's place.
 *
 * The state is rebuilt from [model] by [LoggerStateBuilder] after every action; [model] holds
 * what was read and what the person chose, and only [handleAction] changes it.
 */
@Inject
@Suppress("TooManyFunctions", "LongParameterList")
class LoggerViewModel(
    @Assisted route: LoggerRoute,
    private val diary: DiaryRepository,
    private val customFoods: CustomFoodRepository,
    private val savedMeals: SavedMealRepository,
    private val foodCatalog: FoodCatalog,
    private val settingsRepository: SettingsRepository,
    policySource: VisibilityPolicySource,
    private val logger: FoodLogger,
    calendar: LocalCalendar,
    selectedDay: SelectedDay,
    clock: Clock,
    private val appState: AppState,
    private val telemetry: LoggerTelemetry,
    scanEnabled: BarcodeScanEnabled,
    photoEnabled: PhotoEstimateEnabled,
    private val voice: DescribeVoice,
    fastingRelease: FastingRelease,
    exerciseRelease: ExerciseRelease,
) : SEAViewModel<LoggerState, LoggerEvent, LoggerAction>(
    initialStateArg = LoggerState(pane = route.openingPane, height = route.openingHeight),
) {
    private val day: LocalDate = route.dayIso?.let(LocalDate::parse) ?: selectedDay.date.value

    private var model = Model(
        choices = LoggerChoices(
            slot = route.slot ?: MealSlot.defaultFor(
                time = clock.now().toLocalDateTime(calendar.timeZone()).time,
                mealGroups = settingsRepository.settings.value.mealGroups,
            ),
            pane = route.openingPane,
            height = route.openingHeight,
            // The nav + names no meal: the picker chooses from this person's logging times.
            slotChosen = route.slot != null,
            scanAvailable = scanEnabled(),
            photoAvailable = photoEnabled(),
            voiceEnabled = voice.enabled(),
            fastingReleased = fastingRelease.isOn(),
            exerciseReleased = exerciseRelease.isOn(),
        ),
        policy = policySource.policy.value,
    )

    private var describeJob: Job? = null
    private var findJob: Job? = null
    private var listenJob: Job? = null
    private var voiceTimer: Job? = null
    private var voiceCap: Job? = null

    /** Whether the running session recognises on the phone, for `voice.started`. */
    private var onDevice = false

    /**
     * Something was written and the sheet is on its way down. A second tap on the same button
     * while it animates is still queued behind the first, and would write it all again.
     */
    private var written = false

    private data class Model(
        val choices: LoggerChoices,
        val policy: VisibilityPolicy,
        val inputs: LoggerInputs? = null,
    )

    init {
        viewModelScope.launch {
            inputs(calendar, clock).collect { takeAction(LoggerAction.InputsArrived(it)) }
        }
        viewModelScope.launch {
            policySource.policy.collect { takeAction(LoggerAction.PolicyArrived(it)) }
        }
        // Search's "Describe it" on a miss: the term is read as soon as the pane opens.
        route.describeText?.takeIf { it.isNotBlank() }?.let { takeAction(LoggerAction.DescribeTyped(it)) }
    }

    @Suppress("CyclomaticComplexMethod")
    override suspend fun handleAction(action: LoggerAction) {
        when (action) {
            is LoggerAction.InputsArrived -> action.rebuild {
                copy(inputs = action.inputs, choices = choices.withPickedSlot(action.inputs).withSeen(action.inputs))
            }
            is LoggerAction.PolicyArrived -> action.rebuild { copy(policy = action.policy) }
            is LoggerAction.SlotPicked -> action.choose { copy(slot = action.slot, slotChosen = true) }
            LoggerAction.SheetExpanded -> if (model.choices.height != SheetHeight.Full) action.choose { copy(height = SheetHeight.Full) }
            LoggerAction.SearchFoodTapped -> sendEvent(LoggerEvent.OpenSearch(day, slot()))
            LoggerAction.BarcodeTapped -> if (model.choices.scanAvailable) sendEvent(LoggerEvent.OpenScanner(day, slot()))
            LoggerAction.PhotoTapped -> if (model.choices.photoAvailable) sendEvent(LoggerEvent.OpenPhoto(day, slot()))
            LoggerAction.DescribeItTapped -> {
                action.choose { copy(pane = LoggerPane.Describe, height = SheetHeight.Full) }
                // With voice on the tile is "Say or type it" under a mic, so the tap already asked to listen.
                if (model.choices.voiceEnabled && !model.choices.voice.busy) micTapped(action)
            }
            LoggerAction.SavedMealsTapped -> action.choose { copy(pane = LoggerPane.Saved, height = SheetHeight.Full) }
            is LoggerAction.SavedMealPicked -> logSavedMeal(action.id)
            LoggerAction.CopyMealTapped -> copyYesterday()
            LoggerAction.WaterTapped -> addWater()
            LoggerAction.WeighInTapped -> sendEvent(LoggerEvent.OpenWeighIn)
            LoggerAction.NoteTapped -> {
                telemetry.methodPicked(LogVia.Note, items = 0)
                sendEvent(LoggerEvent.OpenMealNote(day, slot()))
            }
            LoggerAction.FastingTapped -> sendEvent(LoggerEvent.OpenFasting)
            LoggerAction.ExerciseTapped -> sendEvent(LoggerEvent.OpenExercise(day))
            is LoggerAction.RecentAddTapped -> recentAdd(action.row)
            is LoggerAction.RecentNameTapped -> sendEvent(LoggerEvent.OpenFoodDetail(action.row.ref, action.row.source, day, slot()))
            is LoggerAction.DescribeTyped -> {
                // A field can echo its own value back (iOS does around system prompts); only a real edit counts as typing.
                val edited = action.text != model.choices.describeText
                if (edited) typedOverVoice(action)
                if (edited || !model.choices.voice.busy) describeTyped(action, action.text)
            }
            is LoggerAction.DescribeArrived -> describeArrived(action)
            is LoggerAction.DescribeFailed -> {
                if (action.text == model.choices.describeText.trim()) {
                    val status = if (appState.isOffline.value) DescribeStatus.Offline else DescribeStatus.Failed
                    reviewed(action, DescribeRows.cleared(model.choices.review)) { copy(describeStatus = status) }
                }
            }
            is DescribeReviewAction -> reviewAction(action)
            LoggerAction.SaveNameTapped -> saveName()
            is QuickAddAction -> quickAddAction(action)
            LoggerAction.AddToMealTapped -> logDescribed()
            LoggerAction.BackTapped -> back(action)
            LoggerAction.CloseTapped -> sendEvent(LoggerEvent.Dismiss)
            is VoiceAction -> voiceAction(action)
        }
    }

    /** The slot writes go to: the one picked, or Uncategorized on a day with meal groups off. */
    private fun slot(): MealSlot =
        model.inputs?.let { LoggerStateBuilder.effectiveSlot(it, model.choices.slot) } ?: model.choices.slot

    private suspend fun LoggerAction.rebuild(change: Model.() -> Model) {
        model = model.change()
        val current = model
        val inputs = current.inputs
        if (inputs == null) {
            // Nothing to build from yet, but the fields still have to echo what was typed.
            updateState { it.copy(pane = current.choices.pane, describe = it.describe.copy(text = current.choices.describeText)) }
            return
        }
        updateState { LoggerStateBuilder.build(inputs, current.policy, current.choices) }
    }

    private suspend fun LoggerAction.choose(change: LoggerChoices.() -> LoggerChoices) = rebuild { copy(choices = choices.change()) }

    /** Recent's +: logged at its last amount, staying open. A ✓ takes what was added since the sheet opened back out. */
    private suspend fun recentAdd(row: FoodRowUi) {
        val inputs = model.inputs ?: return
        val slot = slot()
        val added = FoodPicks.addedSince(inputs.day, slot, model.choices.seen)[row.key]
        if (added != null) {
            added.forEach { diary.removeItem(it) }
            return
        }
        val pick = LoggerStateBuilder.recentPicks(inputs).firstOrNull { it.key == row.key } ?: return
        logger.log(day, slot, listOf(pick.toLogPick()))
    }

    private suspend fun describeTyped(action: LoggerAction, text: String, debounce: Duration = DESCRIBE_DEBOUNCE) {
        val parsing = text.isNotBlank()
        action.choose { copy(describeText = text, describeStatus = if (parsing) DescribeStatus.Parsing else DescribeStatus.Idle) }
        describeJob?.cancel()
        if (!parsing) {
            reviewed(action, DescribeRows.cleared(model.choices.review))
            return
        }
        val trimmed = text.trim()
        describeJob = viewModelScope.launch {
            delay(debounce)
            val items = foodCatalog.describe(trimmed).getOrNull()
            takeAction(if (items == null) LoggerAction.DescribeFailed(trimmed) else LoggerAction.DescribeArrived(trimmed, items))
        }
    }

    private suspend fun describeArrived(action: LoggerAction.DescribeArrived) {
        if (action.text != model.choices.describeText.trim()) return
        // A new read replaces the last one's lines, except those the person changed or added.
        val review = DescribeRows.read(model.choices.review, action.items, householdOnly())
        reviewed(action, review) { copy(describeStatus = if (action.items.isEmpty()) DescribeStatus.Failed else DescribeStatus.Parsed) }
    }

    /** Calories hidden: a line's amount is a household measure, never grams. */
    private fun householdOnly(): Boolean = model.policy.level(Metric.Calories) == Visibility.Hide

    private suspend fun reviewed(action: LoggerAction, review: DescribeReview, also: LoggerChoices.() -> LoggerChoices = { this }) {
        action.choose { copy(review = review).also() }
    }

    // Describe's review (docs/plans/screens/describe.md): every change is to one line, and none touches the others.

    private suspend fun reviewAction(action: DescribeReviewAction) {
        val review = model.choices.review
        val householdOnly = householdOnly()
        fun change(key: String, edit: (DescribeRow) -> DescribeRow) =
            review.copy(rows = review.rows.map { if (it.key == key) edit(it) else it })
        when (action) {
            is DescribeReviewAction.LessTapped -> reviewed(action, change(action.key, DescribeRows::less))
            is DescribeReviewAction.MoreTapped -> reviewed(action, change(action.key, DescribeRows::more))
            is DescribeReviewAction.UnitPicked -> reviewed(action, change(action.key) { DescribeRows.unitPicked(it, action.index, householdOnly) })
            is DescribeReviewAction.AlternativePicked -> {
                val alternative = review.rows.firstOrNull { it.key == action.key }?.alternatives?.getOrNull(action.index) ?: return
                reviewed(action, change(action.key) { DescribeRows.swapped(it, alternative, householdOnly) })
            }
            is DescribeReviewAction.RemoveTapped -> {
                val row = review.rows.firstOrNull { it.key == action.key } ?: return
                val dismissed = if (row.added) review.dismissed else review.dismissed + row.said
                reviewed(action, review.copy(rows = review.rows - row, dismissed = dismissed))
            }
            is DescribeReviewAction.FindTapped -> {
                findJob?.cancel()
                // Swapping a line the catalog didn't know starts from its words.
                val words = action.replacing?.let { key -> review.rows.firstOrNull { it.key == key }?.takeIf { it.food == null }?.name }.orEmpty()
                action.choose { copy(review = review.copy(find = DescribeFind(replacing = action.replacing, query = words))) }
                if (words.isNotBlank()) findFoods(words)
            }
            is DescribeReviewAction.FindTyped -> {
                val find = review.find ?: return
                action.choose { copy(review = review.copy(find = find.copy(query = action.text))) }
                findFoods(action.text)
            }
            is DescribeReviewAction.FindResultsArrived -> {
                val find = review.find?.takeIf { it.query.trim() == action.query } ?: return
                action.choose { copy(review = review.copy(find = find.copy(answer = SwapAnswer(action.query, action.foods)))) }
            }
            is DescribeReviewAction.FindPicked -> {
                val find = review.find ?: return
                val food = find.answer?.foods?.firstOrNull { it.id == action.id } ?: return
                findJob?.cancel()
                val replacing = find.replacing
                val next = if (replacing != null) {
                    change(replacing) { DescribeRows.swapped(it, food, householdOnly) }
                } else {
                    val row = DescribeRows.added("describe:${review.nextKey}", find.query.trim(), food, householdOnly)
                    review.copy(rows = review.rows + row, nextKey = review.nextKey + 1)
                }
                reviewed(action, next.copy(find = null))
            }
            DescribeReviewAction.FindClosed -> {
                findJob?.cancel()
                action.choose { copy(review = review.copy(find = null)) }
            }
        }
    }

    private fun findFoods(text: String) {
        findJob?.cancel()
        val query = text.trim()
        if (query.isEmpty()) return
        findJob = viewModelScope.launch {
            delay(SEARCH_DEBOUNCE)
            val foods = foodCatalog.search(query, FIND_LIMIT).getOrNull()?.foods.orEmpty()
            takeAction(DescribeReviewAction.FindResultsArrived(query, foods))
        }
    }

    private suspend fun back(action: LoggerAction) {
        when (model.choices.pane) {
            LoggerPane.Actions -> sendEvent(LoggerEvent.Dismiss)
            LoggerPane.Describe -> {
                describeJob?.cancel()
                findJob?.cancel()
                endVoice()
                action.choose {
                    copy(
                        pane = LoggerPane.Actions,
                        describeText = "",
                        describeStatus = DescribeStatus.Idle,
                        review = DescribeReview(),
                        quickAdd = QuickAddForm(),
                        voice = VoiceCapture.Idle(),
                    )
                }
            }
            LoggerPane.Saved -> action.choose { copy(pane = LoggerPane.Actions) }
        }
    }

    /** Describe's Add: exactly its lines, then the sheet closes. */
    private suspend fun logDescribed() {
        if (model.inputs == null || written) return
        val picks = model.choices.review.rows.map { it.toLogPick() }
        logger.log(day, slot(), picks) ?: return
        finish()
    }

    /** Describe's "Just save the name": a name-only food made (or reused) and logged. */
    private suspend fun saveName() {
        if (written) return
        val name = model.choices.describeText.trim().ifEmpty { return }
        val pick = nameOnlyPick(name, model.inputs?.customFoods.orEmpty(), customFoods)
        endVoice()
        describeJob?.cancel()
        findJob?.cancel()
        logger.log(day, slot(), listOf(pick))
        finish()
    }

    private suspend fun logSavedMeal(id: String) {
        if (written) return
        val meal = model.inputs?.savedMeals?.firstOrNull { it.id == id } ?: savedMeals.get(id) ?: return
        val picks = meal.items.mapIndexed { index, draft ->
            val ref = draft.foodRef()
            LogPick(key = ref?.key ?: "saved:$id:$index", ref = ref, draft = draft.copy(method = LogMethod.Saved), source = PickSource.Saved)
        }
        logger.log(day, slot(), picks) ?: return
        finish()
    }

    private suspend fun copyYesterday() {
        if (model.inputs == null || written) return
        val slot = slot()
        val copied = diary.copySlot(day.minus(1, DateTimeUnit.DAY), day, slot)
        telemetry.methodPicked(LogVia.Yesterday, copied.size)
        logger.announce(day, slot, copied)
        finish()
    }

    private suspend fun addWater() {
        if (written) return
        written = true
        val ml = settingsRepository.settings.value.glassMl
        diary.addWater(day, ml)
        sendEvent(LoggerEvent.WaterAdded(day, ml))
        sendEvent(LoggerEvent.Dismiss)
    }

    private suspend fun quickAddAction(action: QuickAddAction) {
        val form = model.choices.quickAdd
        val policy = model.policy
        when (action) {
            is QuickAddAction.NameTyped -> action.choose { copy(quickAdd = form.copy(name = action.text)) }
            is QuickAddAction.NumberTyped -> action.choose {
                copy(quickAdd = form.copy(numbers = form.numbers + (action.metric to QuickAdd.sanitize(action.metric, action.text))))
            }
            QuickAddAction.UseMacroEnergyTapped -> action.choose { copy(quickAdd = QuickAdd.macroEnergyUsed(form, policy)) }
            QuickAddAction.FitMacrosTapped -> action.choose { copy(quickAdd = QuickAdd.macrosFitted(form, policy)) }
            QuickAddAction.AddTapped -> addQuick()
        }
    }

    /** Quick add: one item, the numbers as this mode takes them (the policy has the last word, even over a field still on screen). */
    private suspend fun addQuick() {
        if (model.inputs == null || written) return
        val draft = QuickAdd.draft(model.choices.quickAdd, model.policy) ?: return
        val slot = slot()
        val items: List<LogItem> = diary.addItems(day, slot, listOf(draft), LogMethod.QuickNumber)
        telemetry.methodPicked(LogVia.QuickNumber, items.size)
        logger.announce(day, slot, items)
        finish()
    }

    /** Logged: the sheet closes and Today takes it from `RecentLogEvents`. */
    private fun finish() {
        written = true
        sendEvent(LoggerEvent.Dismiss)
    }

    /**
     * The meal as this person's logging times pick it, while nobody has chosen one: the nav +
     * opened the sheet and the dropdown has not been touched.
     */
    private fun LoggerChoices.withPickedSlot(inputs: LoggerInputs): LoggerChoices {
        if (slotChosen) return this
        val picked = MealSlotPicker.pick(inputs.recentDays, inputs.now, inputs.timeZone, inputs.day.mealGroupsOn(inputs.settings))
        return copy(slot = picked)
    }

    /** The day as it stood when first read: what Recent's ✓ compares against. */
    private fun LoggerChoices.withSeen(inputs: LoggerInputs): LoggerChoices =
        if (seen != null) this else copy(seen = inputs.day.allItems.mapTo(HashSet()) { it.id })

    private fun inputs(calendar: LocalCalendar, clock: Clock): Flow<LoggerInputs> {
        val days = combine(diary.observeDay(day), diary.observeDay(day.minus(1, DateTimeUnit.DAY))) { d, before -> d to before }
        val today = calendar.today()
        val history = diary.observeDays(today.minus(MealSlotPicker.HISTORY_DAYS, DateTimeUnit.DAY), today)
        return combine(
            days,
            settingsRepository.settings,
            diary.recentFoods(limit = RECENT_LIMIT),
            combine(savedMeals.observeAll(), customFoods.observeAll(), ::Pair),
            history,
        ) { (d, before), settings, recent, (saved, custom), recentDays ->
            val zone = calendar.timeZone()
            LoggerInputs(
                today = calendar.today(),
                day = d,
                dayBefore = before,
                settings = settings,
                recent = recent,
                savedMeals = saved,
                customFoods = custom,
                recentDays = recentDays,
                now = clock.now().toLocalDateTime(zone),
                timeZone = zone,
            )
        }
    }

    // Voice log (docs/plans/screens/voice.md). Speech only ever fills describeText;
    // from the final transcript on it is the typed path, through describeTyped.

    private suspend fun voiceAction(action: VoiceAction) {
        if (!model.choices.voiceEnabled) return
        val capture = model.choices.voice
        when (action) {
            VoiceAction.MicTapped -> when (capture) {
                is VoiceCapture.Listening -> stopListening(action, capture)
                VoiceCapture.Asking, is VoiceCapture.Transcribing -> Unit
                is VoiceCapture.Idle, is VoiceCapture.Notice -> micTapped(action)
            }
            // A yes starts listening even if something moved the capture on while the prompt was up: the person asked.
            is VoiceAction.MicPermissionAnswered -> if (capture !is VoiceCapture.Listening && capture !is VoiceCapture.Transcribing) {
                when (val answer = action.permission) {
                    MicPermission.Granted -> startListening(action, asked = true)
                    is MicPermission.Denied -> denied(action, answer.canAskAgain)
                    // Neither yes nor no: say what voice needs and offer to ask again, never a silent return to idle.
                    MicPermission.NotAsked -> denied(action, canAskAgain = true)
                }
            }
            is VoiceAction.SpeechArrived -> speechArrived(action, capture, action.heard)
            VoiceAction.WentQuiet -> if (capture is VoiceCapture.Listening) stopListening(action, capture)
            VoiceAction.TranscriptOverdue -> if (capture is VoiceCapture.Transcribing) transcribed(action, capture.prefix, capture.heard)
            VoiceAction.TypeInsteadTapped -> action.choose { copy(voice = VoiceCapture.Idle(focusText = true)) }
            VoiceAction.OpenSettingsTapped -> {
                voice.speech.openSettings()
                action.choose { copy(voice = VoiceCapture.Idle()) }
            }
            // Keep what was heard: the mic cannot run in the background, and the words are the person's.
            VoiceAction.ScreenPaused -> when (capture) {
                is VoiceCapture.Listening -> transcribed(action, capture.prefix, capture.heard)
                is VoiceCapture.Transcribing -> transcribed(action, capture.prefix, capture.heard)
                // The permission prompt itself pauses the screen on Android.
                is VoiceCapture.Idle, VoiceCapture.Asking, is VoiceCapture.Notice -> Unit
            }
        }
    }

    private suspend fun micTapped(action: LoggerAction) {
        when (val availability = voice.speech.availability()) {
            is SpeechAvailability.Unavailable -> fallBack(action, availability.why, restore = null)
            is SpeechAvailability.Ready -> {
                onDevice = availability.onDevice
                when (val permission = voice.speech.permission()) {
                    MicPermission.Granted -> startListening(action, asked = false)
                    MicPermission.NotAsked -> askForMic(action)
                    // A tap on the mic is the person asking, so a prompt Android will still show is shown.
                    is MicPermission.Denied -> if (permission.canAskAgain) askForMic(action) else denied(action, canAskAgain = false)
                }
            }
        }
    }

    private suspend fun askForMic(action: LoggerAction) {
        action.choose { copy(voice = VoiceCapture.Asking) }
        sendEvent(RequestMicPermission)
    }

    private suspend fun startListening(action: LoggerAction, asked: Boolean) {
        // A parse of typed text still waiting would be overtaken by what is said; the final text parses it all.
        describeJob?.cancel()
        val typed = model.choices.describeText.trim()
        val prefix = if (typed.isEmpty()) "" else "$typed "
        action.choose {
            copy(
                voice = VoiceCapture.Listening(prefix, heard = ""),
                describeStatus = if (describeStatus == DescribeStatus.Parsing) DescribeStatus.Idle else describeStatus,
            )
        }
        voice.telemetry.started(onDevice, asked)
        listenJob?.cancel()
        listenJob = viewModelScope.launch {
            voice.speech.listen()
                // However the platform fails, the person sees why and can type instead.
                .catch { emit(Heard.Failed(SpeechFailure.Unknown)) }
                .collect { takeAction(VoiceAction.SpeechArrived(it)) }
        }
        armQuiet(NOTHING_SAID)
        voiceCap?.cancel()
        voiceCap = viewModelScope.launch {
            delay(LISTEN_LIMIT)
            takeAction(VoiceAction.WentQuiet)
        }
    }

    private suspend fun speechArrived(action: LoggerAction, capture: VoiceCapture, heard: Heard) {
        val (prefix, sofar) = when (capture) {
            is VoiceCapture.Listening -> capture.prefix to capture.heard
            is VoiceCapture.Transcribing -> capture.prefix to capture.heard
            // A late answer from a session already ended.
            is VoiceCapture.Idle, VoiceCapture.Asking, is VoiceCapture.Notice -> return
        }
        when (heard) {
            is Heard.Partial -> {
                val next = when (capture) {
                    is VoiceCapture.Listening -> capture.copy(heard = heard.text)
                    else -> VoiceCapture.Transcribing(prefix, heard.text)
                }
                action.choose { copy(voice = next, describeText = prefix + heard.text) }
                if (capture is VoiceCapture.Listening) armQuiet(QUIET_AFTER_SPEECH)
            }
            is Heard.Final -> transcribed(action, prefix, heard.text)
            // Words already heard are worth more than the error that ended the session.
            is Heard.Failed -> if (sofar.isNotBlank()) {
                transcribed(action, prefix, sofar)
            } else {
                fallBack(action, heard.why, restore = prefix)
            }
        }
    }

    private suspend fun stopListening(action: LoggerAction, capture: VoiceCapture.Listening) {
        voice.speech.finishListening()
        voiceCap?.cancel()
        action.choose { copy(voice = VoiceCapture.Transcribing(capture.prefix, capture.heard)) }
        voiceTimer?.cancel()
        voiceTimer = viewModelScope.launch {
            delay(TRANSCRIBE_TIMEOUT)
            takeAction(VoiceAction.TranscriptOverdue)
        }
    }

    /** The session is over with [text] heard after [prefix]: from here it is exactly the typed path. */
    private suspend fun transcribed(action: LoggerAction, prefix: String, text: String) {
        endVoice()
        if (text.isBlank()) {
            fallBack(action, SpeechFailure.NoSpeech, restore = prefix)
            return
        }
        voice.telemetry.transcribed(WordBucket.of(text))
        action.choose { copy(voice = VoiceCapture.Idle(afterSession = true)) }
        describeTyped(action, prefix + text.trim(), debounce = Duration.ZERO)
    }

    /** Voice cannot help this time; typing can. [restore] puts back what was typed before the session, if there was one. */
    private suspend fun fallBack(action: LoggerAction, why: SpeechFailure, restore: String?) {
        endVoice()
        // Turned off in Settings while the sheet was open: the same notice, and the same event, as a refusal.
        if (why == SpeechFailure.NotAllowed) {
            voice.telemetry.permissionDenied(canAskAgain = false)
        } else {
            voice.telemetry.fallback(VoiceFallback.of(why))
        }
        action.choose {
            copy(voice = VoiceCapture.Notice(why.toNotice()), describeText = restore?.trimEnd() ?: describeText)
        }
    }

    private suspend fun denied(action: LoggerAction, canAskAgain: Boolean) {
        voice.telemetry.permissionDenied(canAskAgain)
        action.choose { copy(voice = VoiceCapture.Notice(VoiceNotice.Denied(canAskAgain))) }
    }

    /**
     * Typing while the mic is on means the person took over: stop listening and keep what is in
     * the field. Typing is also the way past any notice, so that goes too.
     */
    private suspend fun typedOverVoice(action: LoggerAction) {
        val next = when (model.choices.voice) {
            is VoiceCapture.Listening, is VoiceCapture.Transcribing -> VoiceCapture.Idle(afterSession = true)
            is VoiceCapture.Idle, is VoiceCapture.Notice -> VoiceCapture.Idle()
            VoiceCapture.Asking -> return
        }
        endVoice()
        if (next != model.choices.voice) action.choose { copy(voice = next) }
    }

    private fun armQuiet(after: Duration) {
        voiceTimer?.cancel()
        voiceTimer = viewModelScope.launch {
            delay(after)
            takeAction(VoiceAction.WentQuiet)
        }
    }

    /** Stops the mic without waiting for a transcript. */
    private fun endVoice() {
        listenJob?.cancel()
        listenJob = null
        voiceTimer?.cancel()
        voiceCap?.cancel()
    }

    companion object {
        val SEARCH_DEBOUNCE = 250.milliseconds

        /** Longer than search: a parse is a heavier call, and people pause mid-sentence. */
        val DESCRIBE_DEBOUNCE = 700.milliseconds

        /** A pause this long after speech means the person is done. */
        val QUIET_AFTER_SPEECH = 2.seconds

        /** Nothing said at all for this long after the mic opens: stop rather than listen to the room. */
        val NOTHING_SAID = 8.seconds

        /** iOS stops server recognition at about a minute; a meal never needs that long. */
        val LISTEN_LIMIT = 55.seconds

        /** How long to wait for the recognizer to settle after the mic stops before keeping what was heard. */
        val TRANSCRIBE_TIMEOUT = 4.seconds

        /** Describe's find-a-food search: a short list, it is looking for one food. */
        const val FIND_LIMIT = 15

        /** Recent at full height (13.4b). */
        const val RECENT_LIMIT = 6
    }
}

/**
 * How tall the sheet opens. From a + it opens at 60% so Today stays in view; opened on Describe
 * or Saved it opens full.
 */
private val LoggerRoute.openingHeight: SheetHeight
    get() = if (openingPane != LoggerPane.Actions) SheetHeight.Full else SheetHeight.Partial

/** A saved meal chosen on the Foods tab opens the sheet on Saved meals, where Add logs it. */
private val LoggerRoute.openingPane: LoggerPane
    get() = when {
        prePickSavedMealId != null -> LoggerPane.Saved
        else -> when (start) {
            LoggerStart.Actions -> LoggerPane.Actions
            LoggerStart.Describe -> LoggerPane.Describe
            LoggerStart.Saved -> LoggerPane.Saved
        }
    }
