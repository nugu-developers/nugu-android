Change Log
==========

Version 0.9.15 *(2020-08-07)*
-----------------------------
* Fix: Send missing StopRecognize event (#1037)
    * When ASR canceled before speech start, send missing StopRecognizer event.
* Improve: Improve focus management (#1032)
    * Even if the request channel has higher priority than foreground channel, get background focus due to another higher priority channels exist.
* Improve: Update DialogUX's state immediately (#1034)
* Improve: Apply the policy for Sound Layer's priority (#1039)
* Improve: Prevent calls when seek is not possible (#1041)
    * If duration is null, do not seek.
* Improve: Improve focus & playSync management on TTS stop/finish (#1043)
    * Release playSync on TTS stop/finish immediately.
    * Release focus afterTTS stop/finish event.
* New: Apply Interaction Mode (#1046)

Version 0.9.14 *(2020-07-31)*
-----------------------------
* Fix: Disconnect is not detected at intermittent timing (#1005)
* Fix: Fixed issue where epd does not stop (#1010)
* Fix: Fix an issue that returns 0 when reading for SDS buffer after close (#1015)
* Fix: Prevent memory leaks at AttachmentManager (#1019)
* Fix: Missing a call to shutdown() on scheduler of Transport (#1026)
* Improve: Send PhoneCall.CallArrived event with full context (#1003)
* Improve: Add feature noAck at Call (#1006)
* Improve: Update webview interface to handle withdrawn user (#1008)
* Improve: Update the priority of focus (#1012)
* Improve: Synchronize  ASR.ExpectSpeech as Play (#1017)
* Improve: Provide session status at onDialogUXStateChanged (#1022)
* Improve: Notify EPD status with more info (#1029)

Version 0.9.13 *(2020-07-23)*
-----------------------------
* Fix: Notify valid chips on dialog ux state change (#996)
* Fix: Fix not release focus when tts finish/stop (#999)
* Improve: Replace webbrowser with Inappbrowser in loginKit (#906)
    * Replace webBrowser with InAppBrowser
    * Replace LoadingActivity with LoginActivity
    * Add accountByInAppBrowser to NuguOAuthInterface
* Improve: Change server initiated setting during runtime (#952)
* Improve: Simplify dialogUX notify logic (#994)
    * Notify only at setState if something changed.

Version 0.9.12 *(2020-07-22)*
-----------------------------
* Fix: Fix invalid ASR.Recognize failure handling (#983)
* Improve: Add callback to notify when text request is created. (#987)
* New: Manage focus for PhoneCall (#989)

Version 0.9.11 *(2020-07-21)*
-----------------------------
* Improve: Apply url compare logic on resume (#980)

Version 0.9.10 *(2020-07-21)*
-----------------------------
* Fix: Fix side effect for PlaySync based on playServiceId (#972)
    * Prevent invalid release sync of play
    * Stop display timer if play sync start again.
* Fix: Deactivate session when expect speech finished (#977)
* Improve: Improve focus managing with external focus. (#973)

Version 0.9.9 *(2020-07-20)*
-----------------------------
* Fix: Parameters missing in AccountInfoIntent (#961)
* Fix: Fix invalid asr result (#965)
* Fix: Fix invalid out-dated event handling (#968)
* Improve: Improve a focus release timing at TTS stop or finish. (#936)

Version 0.9.8 *(2020-07-17)*
-----------------------------
* Fix: Notify the chips for a recent session (#922)
* Fix: Crash issue  (#925)
* Fix: Fix incorrect cancel of SpeechRecognizer (#926)
* Fix: MaxLength is wrong in Chips of VoiceWindow (#930)
* Fix: Fix wrong directive name (#931)
    *  BlockingIncomingCall -> BlockIncomingCall
* Fix: Not receiving server-initiative directive (#933)
* Fix: Improve Call Impl classes (#942)
    * Callback immediately when cancel() is called
    * Fix wrong update callback if already executed
* Fix: Fix wrong release request (#948)
* Fix: Send a valid playServiceId (#950)
* Fix: Fix an invalid persistent of playContext when tts finished (#954)
* Improve: Improve PlaySynchronizer (#841)
    * Implementation: Async -> Sync
    * Remove existOtherSyncObject() API
    * Allow nullable listener
* Improve: Update the policy for the synchronization of play (#914)
    *  Finish the play sync based on: dialogRequestId -> dialogRequestId and playServiceId
* Improve: Publish getAgent() through a NuguAndroidClient (#937)
* Improve: Create UUID when a event occur immediately. (#940)
* Improve: Apply new display context policy. (#941)
    * Prevent the display from disappearing during ASR.
* Improve: Update SilverTray 4.2.2 (#944)
* New: Support System Interface v1.3 (#426)
    * Add the System.ResetConnection directive
    * Add the System.Noop directive for Http2
* New: Add ReadMessage's callback (#915)
* New: Add the default duration for display to set (#917)
* New: Support Message Interface v1.1 (#920)
    * add template.info at context

Version 0.9.7 *(2020-07-10)*
-----------------------------
* Fix: Fix incorrect state processing (v1) (#876)
* Fix: Fix reverse playStack ordering (#889)
* Fix: Fix not resume audioPlayer on foreground (#893)
* Fix: Fix wrong acquire channel for TTSScenarioPlayer (#897)
* Fix: Update VoiceChromeView (#902)
    * Fix stt according to DialogUXState
    * Apply chips Texview to ellipsize
* Fix: Fix not stop listening on working (#904)
* Fix: Fix missing notify of dialogMode changes (#907)
* Improve: Improve Request & Response management (#713)
* Improve: Apply blocking policy at Chips.Render directive (#877)
* Improve: Send PhoneCall.CandidatesListed event with full context. (#880)
* Improve: Rename shuffle/favorite/repeat API (#881)
* Improve: Update Keensense v0.2.2 (#883)
* Improve: Change API of SendCandidatesDirectiveHandler.Controller (#885)
    * getCandidateList -> sendCandidates (sync -> async)
* Improve: Update NuguServiceKit (#888)
    * Declaration SERVICE_SETTING_URL
    * Add "Oauth-Redirect-Uri" Cookie
    * Add closeWindow interface
    * apply sampleApp
* Improve: Allow nullable location (#891)
* Improve: Apply blocking policy at PhoneCall.MakeCall directive (#895)
* Improve: Open message observer (#909)
* New: Support Speaker Interface v1.2 (#874)
    * add defaultVolumeLevel, group field.

Version 0.9.6 *(2020-07-03)*
-----------------------------
* Fix: Wrong interaction when swiping on ChromeWindow (#861)
* Fix: Grpc(v1) not initialized before sending message (#871)
* Improve: Allow nullable(optional) field of volume context for Speaker (#863)
    * get(Min/Max)Volume
    * getDefaultVolumeStep
    * SpeakerSettings's field(volume, mute)
* Improve: Develop PhoneCall Interface v1.0 (#865)
    * change callType: CALL -> NORMAL
    * update address field of Person Object
    * add businessHours.info field
* Improve: Provide helper interface for Context (#867)
    * add ClientContextState interface
    * add WakeupWordContextProvider class

Version 0.9.5 *(2020-06-26)*
-----------------------------
* Fix: Remove unnecessary code from sampleApp (#856)
* Improve: Update SilverTray v4.2.1 (#853)
* Improve: Update NuguChipsView (#855)
* Improve: Update Chips Interface v1.0 (#857)
* New: Support AudioPlayer Interface v1.4 (#845)
    * add playServiceId at Context

Version 0.9.4 *(2020-06-19)*
-----------------------------
* Fix: Update Streaming & Focus state for BT (#825)
* Fix: Fix synchronization issue for LoopThread (#833)
* Fix: Fix the session not deactivated (#835)
    * Deactivate a session on cancelling ASR.EXPECT_SPEECH.
* Fix: Fix invalid session timeout (#838)
    * If the session activated before set, do not schedule timeout.
* Fix: Fix GsonUtils.deepMerge crash (#847)
* Fix: Fix JSONObject.get() to optString() in NuguLoginKit (#848)
* New: Develop NuguServiceKit (#824)
* Improve: Support Chips Interface v1.0 (#812)
    * Prototype Chips Interface v1.0
	* Provide helper API to enable/disable chipsAgent
	* Provide helper API to mapping chips with it's DM
* Improve: Remove reference on cancelDirective.(#822)
    * AbstractDirectiveHandler: remove reference on cancelDirective.
	* remove all usage of cancelDirective method.
* Improve: Develop Message Interface v1.0 (#827)
    * Add messageType at Contact Object
* Improve: Develop PhoneCall Interface v1.0 (#828)
    * Update PersonObject changes
	* Update CallArrived event changes
	* Add token at CallerObject
* Improve: Improve audioPlayer's stop (#832)
    * Handle as stop if stop called even if player finished.
* Improve: Update SilverTray v4.2.0 (#842)

Version 0.9.3 *(2020-06-12)*
-----------------------------
* Fix: Fix java.util.ConcurrentModificationException
* Fix: Fix UI thread exception when handling visibility of lyrics
* New: Develop Message Interface v1.0
    * Add numInMessageHistory, label field at Contact
    * Support TTS Scenaio (ReadMessage Directive/Event/Context)
* New: Develop PhoneCall Interface v1.0
    * Change API: The Controller of SendCandidatesDirectiveHandler
* New: Support Display Interface v1.4
    * Support postback field
    * Support Dummy directive
* New: Support Speaker Interface v1.1
* New: Support Bluetooth Interface v1.1
    * Add profiles to context
* Improve: Improve AbstractDirectiveHandler's map mangement
* Improve: Add code to NuguOAuthError
* Improve: DNS lookup cache

Version 0.9.2 *(2020-06-09)*
-----------------------------
* Fix: Missing protobuffer-javalite libs

Version 0.9.1 *(2020-06-09)*
-----------------------------
* New: Session Interface v1.0 (#728)
* New: ASR interface v1.2 (#769)
* Improve: Return getOffset in milliseconds (#750)
* Improve: Add Client OS Context (#747)
* Improve: Improve logic for context update (#752)
* Improve: Update AudioPlayer v1.3
* Improve: Update SystemAgent v1.2
* Improve: Update tts v1.2 (#742)
    * Append currentToken at context
* Improve: Adds templateId to sync player & template (#762)
* Improve: Apply New PlayStack(Layer) Policy (#758)
* Improve: Manage audio focus for bluetooth streaming (#781)
* Improve: Add new APIs to OAuth (#744)
* Improve: Update Message Interface v1.0 (#789)
* Improve: Update gRPC v1.29.0
* Improve: Add history.callType at PersonObject (#745)
* Fix: PhoneCall Agent (#760)
    * Update PhoneCall Interface v1.0
    * Use enum CallType at MakeCallPayload
    * Only contain it's self context
    * Add state at Context
    * Send CandidatesListed when null candidates
* Fix: Missing connectionTimeout of Policy (#776)

Version 0.9.0 *(2020-05-28)*
-----------------------------
* New: Support mediaplayer interface v1.0 (#695)
* New: Support message interface v1.0 (#696)
* New: Support phonecall interface v1.0 (#698)
* New: Support AudioPlayer v1.3 (#709)
* Fix: Apply timeout for attachment (#714)
* Fix: Add WITHDRAWN_USER reason at System.Revoke directive(#723)
* Fix: Fix incorrect state processing (#731)
    * Disconnect method not working When backoff is running
* Fix: Remove SynchronizeState event from CONNECTED state (#734)
* Improve: Add Events and Directives methods to GrpcTransport (#607)
* Improve: Update keensense v0.2.1

Version 0.8.20 *(2020-05-26)*
-----------------------------
* Fix: Cancel a item fetched but not playing yet (#721)
* Fix: Tts not playing
    * TTS stop when unintended case.
* Fix: TTS.Stop not working correctly.
    * when TTS implicit stopped, TTS.Stop directive should stop the play sync.

Version 0.8.19 *(2020-05-13)*
-----------------------------
* Fix: return value for read API of Attachment's Reader

Version 0.8.18 *(2020-05-13)*
-----------------------------
* Fix: Add missing asrContext for ASR.Recognize event

Version 0.8.17 *(2020-05-12)*
-----------------------------
* Fix: Ensure execution order of focus request (#703)
* Fix: Resume not work after explicit pause (#707).

Version 0.8.16 *(2020-05-08)*
-----------------------------
* New: Add dialogRequestId at ASR result listener.
* Fix: Add clientVersion to user-agent header
* Fix: AudioPlayer agent issue
    * handle fetch's failure
* Fix: ASR agent issue
    * close previous session if new open.
    * handle ExpectSpeech's cancel correctly.
    * wrong recognizer's state changes.
    * missing result directive
* Fix: Alway update tts's context.
* Fix: Wrong referrerDialogRequestId for ASR's event.
* Fix: Wrong update call of Display.
* Improve: Attachment access performance
* Improve: Optimize memory management
    * minimize GC
* Improve: Deprecate timeoutInMillis at ExpectSpeech
* Improve: detemine behavior for audioplayer's resume request.
* Improve: Prevent calling stop when alread stop or finished for TTS's Player
* Improve: Update battery context
* Improve: Use context even if timeout.
* Improve: Component Design Updated.
    * NuguButton
    * VoiceChrome


Version 0.8.15 *(2020-04-14)*
-----------------------------
* New: DisplayAgent - Add render failed notify (#522)
* New: AudioPlayerAgent - Apply RDRID at Request{XXX}event (#526)
* New: ASRAgent - Send power of wakeup (#546)
* New: ASRAgent - Apply CancelRecognize directive (#545)
* New: Add listener for directive handling (#543)
* New: Implement sound agent
* New: Add trigger callback (#584)
* Fix: AudioPlayerAgent -Not stopped for already fetched item (#520)
* Fix: ASRAgent - Back to idle state only request failed (#532)
* Fix: TTSAgent - Carefully release focus of TTS (#529)
* Fix: Auth - expiresIn correctly
* Fix: Exception for URI.create (#555)
* Fix: Add User-Agent header for Registry
* Fix: Display not cleared after back/forward (AudioPlayer's resume) (#562)
* Fix: Remove Start/FinishDiscoverableEvent
* Fix: Fix wrong namespace for control events (#609)
    * ControlFocus/Scroll's Succeeded or Failed
* Improve: GrpcTransport - Parsing of policy for Registrty
* Improve: AudioPlaeyrAgent - Add playServiceId comparison (#535)
    * To decide whether to resume or not, add playServiceId comparison.
* Improve: Apply new context policy (#550)
    * when filter context, include version only.
* Improve: Update AudioPlayer context always (#553)
* Improve: Include full context for ElementSelected (#564)
* Improve: Focus managemnt - Apply focus holder manager (#567)
* Improve: Change beepName from string to enum
* Improve: Apply blocking policy per dialogRequestId (#566)
* Improve: Update silverTray v4.1.5
* Improve: Update built-in agent version
    * ASR: 1.1
	* Delegation : 1.1
	* TTS : 1.1
	* Text : 1.1
	* System : 1.1

Version 0.8.14 *(2020-03-25)*
-----------------------------
* New: Support FullText3 for display (#507)
* New: Support Timer for display v1.3 (#507)
* New: Apply asrContext payload (#516)
* Fix: Disable connectionPool
* Improve: Update silvertray v4.1.13 (#509)
* Improve: Nullable playServiceId for requestTTS (#511)
* Improve: Handle tts error case (#518)


Version 0.8.13 *(2020-03-23)*
-----------------------------
* New: Aplly PCM power measure for KeywordDetector (#392)
    * (Caution) SpeechRecognizerAggregator's constructor changed.
* New: Apply context layer for Display (#427)
* New: Add System.Revoke Directrive
* New: Apply defaultVolumeStep for Speaker (#430) 
* New: Enable AudioPlayer v1.2 (#373)
* Fix: ExpectSpeech's payload not included at ListenTimeout/ListenFailed event (#404)
* Fix: Close directive not completed (#408)
* Fix: ASR recognition started twice at same time (#410)
* Fix: Not working prev/next command for AudioPlayer (#433)
* Fix: Wrong state value at Screen's context (#435)
* Fix: Manage sourceId for IntegratedMediaPlayer correctly (#442)
* Fix: Missing token for TTS's playback event (#481)
* Fix: Crash at sample application (#501)
* Fix: The credential have been cleared (#432)
* Improve: Request & Response mapping
    * at Extension, Text, TTS, AudioPlayer agetns
* Improve: strictly check token for render directives (#449)
* Improve: Apply updated referrerDialogRequestId (#451)
    * AudioPlayer, TTS, Agent
* Improve: Provide way to create UUID from dialogRequestId (#453)
* Improve: Add flag to enable or disable for Display (#460)
* Improve: Support duration and offset for attachment (#440)
* Improve: Manage playStack using timestamp (#458)
* Improve: Stop media player when request stop by agent (#471)
* Improve: Remove property field at ExpectSpeech (#483)
* Improve: Support nullable keyword detector for SpeechRecognizerAggregator (#473)
* Improve: Update silvertray v4.1.12
* Improve: Apply update for AudioPlayer display. (#490)
* Improve: Deprecated address of serverPolicies in registry (#505)


Version 0.8.12 *(2020-03-09)*
-----------------------------
* New: Provide a way to map request & response 
    * for TextAgent (#381)
    * for ASRAgent (#383)
* New: Support Request{XXX}Command for AudioPlayer v1.2
* Fix: Not playing audio player for attachment (#367)
* Fix: Not cleared audio player's display after stop on finished (#374)
* Fix: Display's context not updated when enter DM (#376)
* Fix: Tts player not working using two or more at same time (#384)
* Improve: Add onStop at OnPlaybackListener (#369)
* Improve: PlayContext's interface changed (set -> gathering) (#393)


Version 0.8.11 *(2020-03-03)*
-----------------------------
* New: Add listener for received directives (#359)
* New: Add OnSendMessageListener (#361)
* New: Add handler for text source directive (#365)
* Fix: Screen's context not updated (#356)
* Fix: Support Call3 Directive (Fix type)
* Improve: Allow any speaker nullable (#349)
* Improve: Change display timer management policy (#332)

Version 0.8.10 *(2020-02-27)*
-----------------------------
* New: Modify access to the attahcment manager (#344)
* New: Support CallX directives for Display v1.2 (#341)
* Fix: Prevent focus loss between DM and TTS (#346)


Version 0.8.9 *(2020-02-26)*
----------------------------
* New: Implement the OAuth2 Device Authorization Grant
* Fix: Wrong resume issue on focus change (improved) (#266)
* Fix: Audio player context not updated (#333)
    * Side issue for #297
* Improve: Add missing field(parentMessageId, mediaType) at AttachmentMessage (#335)
* Improve: Provide thread factory used at executor in SpeechRecognizerAggregator (#339)

Version 0.8.8 *(2020-02-20)*
----------------------------
* New: Support display v1.2 (#232)
    * Support Update directive (#326)
	* Support CommerceXXX directives (#326)
* Fix: Audioplayer stopped after 10s pausing (#320)
* Fix: Wrong event name for setVolume/setMute (#322)
* Fix: Wrong resume issue on focus change (#266)
* Fix: Send pause event correctly (#297)

Version 0.8.7 *(2020-02-19)*
----------------------------
* Fix: Blocked some directives after setMute received (#310)
* Fix: An error when the stream was closed
* Fix: Not working UpdateMetadata directive (#313)
* Fix: Voice chrome not dismissed in some cases(#317)

Version 0.8.6 *(2020-02-17)*
----------------------------
* New: Apply referrerDialogRequestId at (#19)
    * TTS, Text, Speaker, Screen, Mic, Extension, Display Interface.	
* Fix: Apply rate param at setVolume (#303)
* Fix: Close display immediately (side-effect for #270) (#306)
* Improve: Movement Interface Removal (#306)

Version 0.8.5 *(2020-02-14)*
----------------------------
* Fix: audio player not working (#288)
* Fix: Can't play after animation stop (#209)
* Fix: Incorrect delivery of hasPairedDevices (#58)
* Improve: Apply blocking policy for speaker directive (#293)
* Improve: Add poc status in oAuthClient (#248)
* Improve: Remove local api for SpeakerAgent's control (#295)

Version 0.8.4 *(2020-02-13)*
----------------------------
* New: Allow to detail control for ASR options (#282)
* New: Add flag at stopListening() to indicate cancel or finish ASR process (#284)
* Fix: Add missing payload at Delegate's Request event (#286)
* Improve: Add wakeup word for KeywordResource (#279)

Version 0.8.3 *(2020-02-11)*
----------------------------
* New: Implement battery agent (#249)
    * Apply battery charing status (#238)
* New: Add NONE,LONGEST duration type for display(#232)
* New: Prototype screen interface v1.0 (#242)
* New: Update display agent version to 1.2 (#232)
    * support CONTROL_FOCUS, CONTROL_SCROLL, SCORE_1, SCORE_2, SEARCH_LIST_1, SEARCH_LIST_2 directives.
    * Added Controller interface.
* New: Support playing attahcment source at AudioPlayerAgent (#236)
* New: Implement bluetooth agent(#58)
* Fix: Apply duration of display when restart timer(#263)
* Fix: pause not work at AudioPlayer (#258)
* Fix: Deliver display type correctly (#267)
* Fix: Handle AudioPlayer.Stop directive on finish (#270)
* Fix: Handle notify result error (#272)
* Fix: Fix TimeUUID v2 spec does not apply (#255) 
* Improve: Update SilverTray v4.1.9 (#246)
* Improve: Move some classes
 
Version 0.8.2 *(2020-01-30)*
----------------------------
 * New: Support AudioPlayer v1.1
     * Support lyrics spec (#191, #192, #224)
 * Fix: Crash when create SpeechRecognizerAggregator (#229)
 * Improve: discard management for display (#217)
     * In application, Renderer's render() will be called only once per templateId.

Version 0.8.1 *(2020-01-29)*
----------------------------
 * New: Support CommandIssued event for extension interface (#186)
 * New: Prototype speaker interface v1.0 (218)
 * New: Support AudioPlayer v1.1
     * UpdateMetadata directive (#190)
     * (Favorite/Repeat/Shffle) directive (#193)
 * Fix: not notify audio player state changes sometimes (#194)
 * Fix: use http v1.1 protocol to connect Registry
 * Fix: not work voice chrome animation somethimes
 * Fix: blocking issue when read at SDS'reader (#211)
 * Fix: add missed mic directive handler (#215)
 * Fix: refactor SpeechRecognizerAggregator (#200)
     * multi thread issue
     * release resource before state noti
     * notify state through handler
 * Improve: Apply timeUUID v2 spec
 * Improve: Send referrerDialogRequestId on SynchronizeState event (#198)

Version 0.8.0 *(2020-01-16)*
----------------------------
 * Fix: not work play synchronization properly (#157) (side effect for #164)
 * Fix: Allow plaback button efvent at any state (#183)
 * Improve: Core module independent of capability agent (#168)
     * implementaion of agents are separated into new nugu-agent module.
     * (Caution) Many components have been relocated. Check import carefully on update.
	 
Version 0.7.3 *(2020-01-08)*
----------------------------
 * New: Support plugin agent (#164)
 * Fix: Invalid opus player status changes (#157)
 * Improve: Replacable 'AudioFocusInteractor' (#162)
 * Improve: Update keensense v0.1.4
 * Improve: Update jademarble v0.1.4

Version 0.7.2 *(2019-12-26)*
----------------------------
 * Fix: StartListening not work after stopTrigger (#152)
 * Fix: Fix invalid transit to IDLE state of DialogUXStateAggregator (#154)
 
Version 0.7.1 *(2019-12-23)*
----------------------------
 * Improve: Mapping errors using ChangedReason (#147)

Version 0.7.0 *(2019-12-20)*
----------------------------
 * Fix: issue where reason of authentication error not delivered in OAuth (#128)
 * Improve: Reimplement(Refactor) ASRAgent (#144)
 * Improve: Change network management and logic
     * (Caution) Previously, when the auth was refreshed, the connection was attempted automatically at SDK, but not now.

Version 0.6.11 *(2019-12-12)*
----------------------------
 * Improve: Return id for setElementSelected's request.
 (Caution) now, setElementSelected throw IllegalStateException.

Version 0.6.10 *(2019-12-11)*
----------------------------
 * Fix: TTS not stopped after stop called. (#137) (Update silverTray to v4.1.8) 
 * Fix: Not stop asr on busy (#133)
 * Improve: Send correct error type for ASRAgentInterface.OnResultListener.onError()
 * Improve: Set null as default for setElementSelected's callback.


Version 0.6.9 *(2019-12-10)*
----------------------------
 * New: Add callback for setElementSelected (#129)
 * Fix: missing call for reader's close (#120)
 * Fix: leak at inputProcessorManager (#111)
 * Fix: Remove sound interface (#101)
 * Fix: Remove unused resources from VoiceChromeView
 * Fix: Remove unused dependencies (#115)
 * Improve: Update Keensense to v0.1.3
 * Improve: Update SilverTray to v4.1.7
 * Improve: Improve shared circulr buffer's thread wait (#119)

Version 0.6.8 *(2019-12-03)*
----------------------------
 * New: Implement request event of delegation (#91)
 * New: Add listener to notify system exception (#104)
 * New: Open system agent interface (#104)
 * Fix: Missing authentication status in onAuthFailure
 * Fix: Fix reason deliver issue in Disconnected (#107)
 * Improve: Connection handling
 * Improve: Add proguard rules for @SerializedName annotation
 
 
Version 0.6.7 *(2019-11-28)*
----------------------------
 * Fix: Revert "Grpc transport Rewrite (#74)" (revert applied at v0.6.6)
 * Improve: Use static context when send asr event (#64)

Version 0.6.6 *(2019-11-27)*
----------------------------
 * New: Grpc transport Rewrite (#74)
 * New: Send delegation context (#91)
 * Fix: issue - force clear display (#86)
 * Fix: issue - not working stopRenderingTimer (#89)
