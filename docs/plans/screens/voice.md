# Voice log plan (0.2)

Say the meal instead of typing it. The mic in Describe it turns speech into
text on the device, the text fills the Describe field as it is heard, and from
there it is exactly the typed path: `FoodCatalog.describe` (`/v1/describe`),
Describe lines in the basket, the same policy-resolved rows, the same "Add to
{meal}". Read `logger.md` (Describe) and `shared.md` first.

## Source
- `docs/design/v3/logger-describe.png` (3.8): the round mic at the bottom
  right of the Describe box, beside "Say it the way you'd say it". The
  manifest note: "Type or speak, and it's split into editable lines."
- `logger.png`: the Describe chip already carries the mic icon. No change.
- `today-full.png`: v3 meal cards have no mic, so none is built.
- Handoff `README.md` 4.2 and `phone-logic.js` `mealNote`: the older meal
  sheet "Voice log" records audio and stores it on the meal as a voice note.
  Not built. v3 does not show it, and storing audio contradicts the rule this
  plan keeps (audio is never stored or sent by Opson). `shared.md` "0.1 scope
  gates" lists the meal sheet and meal card mic; they stay hidden.

## Entry points

| Where | What | Flag |
|---|---|---|
| Describe pane, the mic in the box | Start and stop listening | `release.voice` |

`VoiceLogEnabled` (`FlagConfigValue`, path `release.voice`, default false, in
the QA menu), read once when the logger opens like `release.barcode`. Off, the
Describe box draws exactly as in 0.1 (prompt only, no mic), no recognizer is
asked anything and no permission prompt is composed.

Nothing runs at launch. The recognizer and the prompt are bound in DI with
empty constructors; the first platform call is `availability()` when someone
taps the mic.

## Speech to text: `:libraries:speech` (new)

```kotlin
interface SpeechToText {
    fun availability(): SpeechAvailability          // Ready(onDevice) | Unavailable(why)
    fun permission(): MicPermission                 // Granted | NotAsked | Denied(canAskAgain)
    fun listen(): Flow<Heard>                       // Partial(text)* then one Final(text) or Failed(why); cancel to abandon
    fun finishListening()                           // stop the mic; the running listen() delivers Final
    fun openSettings()
}
interface MicPermissionPrompt { @Composable fun rememberPermissionRequest(onAnswer: (MicPermission) -> Unit): () -> Unit }
enum class SpeechFailure { NoSpeech, NoRecognizer, NoMicrophone, NotAllowed, NoModel, NeedsConnection, Busy, Unknown }
```

Same split as `:libraries:scanner`: the interface and types in the library,
platform bindings in `:libraries:speech:impl`, Kotlin/Native on iOS (no Swift).
The view model owns the flow; the prompt lives in composition because Android
asks through an activity result.

| | iOS | Android |
|---|---|---|
| Recognizer | `SFSpeechRecognizer` for the current locale; `SFSpeechAudioBufferRecognitionRequest` with partial results, `requiresOnDeviceRecognition = true` when `supportsOnDeviceRecognition` | `SpeechRecognizer.createOnDeviceSpeechRecognizer` on API 31+ when `isOnDeviceRecognitionAvailable`, else `createSpeechRecognizer` with `EXTRA_PREFER_OFFLINE`. An on-device recognizer that reports its language unavailable before hearing anything is swapped for the default one once, in the same session |
| Mic | `AVAudioEngine` input tap, `AVAudioSession` category record, deactivated (notifying others) when done | The recognizer's own capture |
| Permission | Speech recognition, then microphone (`AVAudioApplication`); either refused is `Denied(canAskAgain = false)` | `RECORD_AUDIO`; rationale state gives `Denied(canAskAgain = true)` |
| Unavailable | No recognizer for the locale, or no mic input (the simulator: a zero-rate input format would throw in `installTap`) | `isRecognitionAvailable` false |
| Threads | Built and torn down on main; recognizer callbacks hop to the main queue | Main only, as `SpeechRecognizer` requires |

**iOS session order (after a simulator abort, 2026-10-26).** Reading
`AVAudioEngine.inputNode` with no usable input aborts the process inside
AudioToolbox (`_ReportRPCTimeout` from `AURemoteIO::Initialize`), which nothing
can catch. So a session runs these steps in this order, in `MicSession` over an
`AudioSteps` seam (commonMain, unit tested), with the AVFoundation steps in
`AvAudioSteps`: recognizer exists; both permissions granted; record category,
measurement mode and `setActive(true)`, each error-checked; the session reports
`isInputAvailable`, a route input, channels and a sample rate; only then the
engine is created and `inputNode.outputFormatForBus(0)` read, where 0 Hz or 0
channels is "no microphone"; then the tap, the engine and the task. Any failure,
the final text, an error or a cancel tears down once: task cancelled, engine
stopped, tap removed from the node kept earlier (never `engine.inputNode`
again), `setActive(false)` notifying others. `OneSessionAtATime` answers Busy
to a second session while one is open, so a quick second tap never starts a
second engine. `availability()` also says "no microphone" when the session has
no input, before any prompt. The permission answer is delivered once the app
is active again, so the session is never activated while the alert is leaving.

**Second simulator report (same day).** After the yes the pane still looked
idle, and the app aborted 9 s later with "Initialize: RPC timeout. Apparently
deadlocked", after the engine logged "associating with audio session (0x0),
error -10879". The cause of the idle look: the yes did move the state to
Listening, but `listen()` ran its audio calls on the main thread straight
after, and the first touch of the engine's input sat in CoreAudio until its
own watchdog killed the app. Compose never got the thread back to draw
Listening, so the last frame (the prompt state, which looks idle) stayed up,
and the abort landing 9 s later read as "the next tap crashes". Fixes:
- Every audio session, engine and recognizer call now runs on one serial
  queue (`AudioQueue`, through `OneSessionAtATime`), never the main thread,
  so Listening is drawn whatever CoreAudio does.
- `setCategory(record)`, `setMode(measurement)` and `setActive(true)` are
  separate calls, each checked and logged at debug, and the engine is created
  only after all three succeeded, on the same queue.
- After activation, `isInputAvailable`, `availableInputs` and
  `currentRoute.inputs` (plus channels and rate) must all be non-empty;
  otherwise it is "no microphone". Each read is logged.
- The simulator reports no microphone, because CoreAudio there can deadlock
  and abort from any thread. Set `OPSON_VOICE_IN_SIMULATOR=1` in the scheme's
  environment to try it anyway. Devices take the full path.
- The iOS yes is passed on as given rather than read back (a read straight
  after the prompt could say "not determined"), and an answer that is neither
  yes nor no shows the Denied notice with Allow microphone, never idle.
- A platform that never answers still ends in a notice: 8 s with nothing
  heard stops, and 4 s later the session is dropped with "Didn't catch
  anything".

**What happens to the audio.** Opson never writes audio to disk, never keeps
it in memory past the session, and never sends it to Opson's servers. Only the
final text leaves the phone, to `/v1/describe`, as typed text does.
- iOS: on-device recognition when the phone supports it for the language, so
  the audio never leaves the phone. Where it does not, iOS sends the audio to
  Apple's speech service, under Apple's privacy terms, and returns text.
- Android: the on-device recognizer (API 31+) keeps audio on the phone.
  Otherwise the phone's default recognition service handles it; Opson asks
  it to prefer offline, and the service (usually Google's) decides whether
  audio goes to its servers.

## Describe integration

### Model and state
`LoggerChoices` gains `voiceEnabled` (the flag at open) and
`voice: VoiceCapture`:

```kotlin
sealed interface VoiceCapture {
    data class Idle(val afterSession: Boolean = false, val focusText: Boolean = false)
    data object Asking                                   // the system prompt is up
    data class Listening(val prefix: String, val heard: String)
    data class Transcribing(val prefix: String, val heard: String)
    data class Notice(val notice: VoiceNotice)
}
sealed interface VoiceNotice { Denied(canAskAgain); Unavailable; NeedsConnection; NoSpeech; Failed }
```

`DescribeUi.voice: VoiceUi?`, built by `VoiceStateBuilder.build(enabled,
capture)` (null when the flag is off):

```kotlin
data class VoiceUi(
    val phase: VoicePhase,               // Ready | Listening | Transcribing
    val notice: VoiceNotice? = null,
    val announcement: VoiceAnnouncement? // Listening | Stopped, for the live region
    val focusText: Boolean = false,      // after "Type instead"
)
```

`prefix` is what was already typed, so speaking after "2 eggs" appends. The
field shows `prefix + heard` live. Partials do not parse; the final text goes
through `describeTyped` with no debounce, which is the typed path from there.

### Actions (`VoiceAction : LoggerAction`)

| Action | Does |
|---|---|
| `MicTapped` | Ready or a notice: unavailable gives the Unavailable notice; `Granted` starts; `NotAsked` or `Denied(canAskAgain = true)` asks (`RequestMicPermission`); `Denied(false)` shows Denied. Listening: stop (as `WentQuiet`). Asking or Transcribing: nothing. "Try again" and "Allow microphone" send this too |
| `MicPermissionAnswered(permission)` | Granted starts (unless already listening, even if the capture moved on while the prompt was up); refused shows Denied |
| `SpeechArrived(heard)` | Partial: the field shows it, the silence timer restarts. Final: blank is NoSpeech, else parse. Failed: if something was heard, keep it as final; else the notice for the reason |
| `WentQuiet` | Silence timer (2 s after speech, 8 s before any) or the 55 s cap (iOS server recognition stops near a minute): `finishListening`, Transcribing |
| `TranscriptOverdue` | 4 s in Transcribing with no final: keep what was heard |
| `TypeInsteadTapped` | Notice gone, the field takes focus |
| `OpenSettingsTapped` | The app's Settings page; notice gone |
| `ScreenPaused` | App backgrounded or sheet covered while listening: stop and keep what was heard |

Session failures map to notices: NoRecognizer, NoModel and NoMicrophone to
Unavailable; NotAllowed to Denied (and `voice.permission_denied`); Busy and
Unknown to Failed; an exception from the flow is Unknown. Every way a session
ends without words shows a notice.

Also: typing while listening stops listening (the person took over), but a
field echoing its own value is not typing; Back off
Describe, Save the name, and leaving the sheet cancel it.

Event: `RequestMicPermission`; the entry point runs the prompt. `VoiceAction`
and `RequestMicPermission` extend the logger's sealed `LoggerAction` and
`LoggerEvent` from `DescribeVoice.kt`, so the logger's own files carry only
the seams (`DescribeUi.voice`, `LoggerChoices.voiceEnabled` / `voice`, one
`when` branch, the cancels in Back and Save the name). The recognizer,
the flag and the telemetry come in as one `DescribeVoice`.

The entry point composes the prompt and a `LifecycleResumeEffect` (pause sends
`ScreenPaused`) only while `describe.voice` is non-null.

## Components
Existing: `RoundIconButton` (the 56 dp mic, accent tint; filled with a stop
square while listening), `Banner`-style soft card, `ButtonSecondary`,
`ButtonGhost`, `TextArea`. Library additions: `AppIcons.Stop`.

## Visibility surfaces

The transcript and the voice copy carry no app numbers. The lines are the
Describe lines: the same `DescribedLine` built from the basket by
`LoggerStateBuilder`, `Metric.Calories` on `Surface.Logger`, bare context.

| Number | Metric | Show | Soften | Delay | Hide |
|---|---|---|---|---|---|
| Line energy after a spoken parse | calories | `156` | Hidden | Hidden | Hidden |

New leak surface `logger-describe-voice` (Logger): listening with partial
text over parsed lines, calories in Full yes.

## Telemetry (`VoiceTelemetry`)

| Event | Attributes | Fires |
|---|---|---|
| `voice.started` | `on_device`, `permission` (`granted` / `asked`) | Listening began |
| `voice.transcribed` | `words` (`1-3`, `4-7`, `8-15`, `16+`) | A non-blank final transcript |
| `voice.permission_denied` | `can_ask_again` | The Denied notice is shown |
| `voice.fallback` | `reason` (`no_recognizer`, `no_model`, `needs_connection`, `no_speech`, `busy`, `error`) | Any other notice offering typing |

Never the transcript, a word, or anything parsed from it.

## Copy (`strings_voice.xml`)

| Key | String |
|---|---|
| `voice_mic_cd` | Say it instead of typing |
| `voice_stop_cd` | Stop listening |
| `voice_listening` | Listening. Tap stop when you're done. |
| `voice_stopped` | Stopped listening |
| `voice_denied` | Opson needs the microphone to hear you. You can turn it on in Settings. |
| `voice_denied_ask` | Opson needs the microphone to hear you. |
| `voice_allow` | Allow microphone |
| `voice_open_settings` | Open Settings |
| `voice_type_instead` | Type instead |
| `voice_try_again` | Try again |
| `voice_unavailable` | Voice isn't available on this phone right now. Typing works just the same. |
| `voice_needs_connection` | Voice needs a connection on this phone right now. You can type it instead. |
| `voice_no_speech` | Didn't catch anything that time. |
| `voice_failed` | Voice stopped unexpectedly. |

Info.plist: `NSMicrophoneUsageDescription`, `NSSpeechRecognitionUsageDescription`.
Android manifest (speech impl): `RECORD_AUDIO`, and a `<queries>` entry for
`android.speech.RecognitionService` (without it API 30+ reports no recognizer).

## Edge cases
- Flag off: no mic, no prompt composed, previews and tests cover both.
- Offline: on-device recognition still works; the parse then says Describe
  needs a connection, as typed text does.
- Simulator / no mic input: Unavailable.
- Blind: the transcript shows, the lines' energy does not.
- Large text: the notice buttons wrap.

## Tests
- `VoiceStateBuilderTest`: flag off null, each capture to its phase, notice
  and announcement; Blind lines Hidden through `LoggerStateBuilder`.
- `LoggerViewModelTest` voice cases with `FakeSpeechToText`: each action,
  permission branches, partials into the field, final parses through
  `describe`, silence and overdue timers, typing and Back cancel, telemetry
  never carries text, flag off ignores the mic.
- `WordBucketTest`.
- Previews (`VoicePreviews.kt`): idle, listening with partial text,
  listening over lines, transcribing, denied, unavailable, no speech, Blind,
  and dark. Flag off is the existing `LoggerDescribePreview`.
- Leak test: `logger-describe-voice` in `LoggerLeakSurfaces` and
  `REQUIRED_SURFACES`.
