Change Log
==========

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
