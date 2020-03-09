Change Log
==========

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
