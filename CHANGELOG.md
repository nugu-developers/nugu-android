Change Log
==========

Version 1.7.8 *(2022-07-11)*
-----------------------------
* Fix: Fix ProgressTimer's onProgressReportDelay not called rarely. (#2246)
* Fix: Fix lottie crash (#2252)
* Improve: Reduce audio focus request  (#2244)
* Improve: Manage an audio player's offset at agent (#2250)
* New: ASR Interface v1.7 (#2248)

Version 1.7.7 *(2022-04-11)*
-----------------------------
* Fix: Fix wrong update source audio info (#2223)
* Fix: Notify media progress when duration retrieved (#2225)
* New: Support Text Interface v1.7 (#2230)
	*  Add source for TextInput event (#2227)

Version 1.7.6 *(2022-04-06)*
-----------------------------
* Fix: Fix PhoneCall's Contact Serialization (#2220)

Version 1.7.5 *(2022-02-22)*
-----------------------------
* New: Update PhoneCall v1.3 (#2217)

Version 1.7.4 *(2022-02-10)*
-----------------------------
* Improve: Improve media template Information update fast (#2207)
* New: Update PhoneCall Interface v1.3 (#2208)
* New: Update InteractionControl Policy (#2211)
* New: Support Permission v1.1 (#2214)

Version 1.7.3 *(2022-01-21)*
-----------------------------
* Fix: Fix ConcurrentModificationException at DirectiveRouter (#2186)
* Fix: NPE issue at DirectiveGroupHandlingListener (#2200)
* Improve: Add feature to select thread when request android audio focus at AndroidAudioFocusInteractor (#2189)
* Improve: Optional referrerDialogRequestId field (#2196)
* Improve: Improve parent&child display check logic (#2202)

Version 1.7.2 *(2021-12-23)*
-----------------------------
* Fix: Fix not reconnect when canceled in backoff (#2126)
* Fix: Fix call only with ActivityResult in fallback webview (#2128)
* Fix: Public EndCallPayload's member (#2152)
* Fix: Update Text Interface v1.6 (#2175)
* Fix: Crash issue : Circular dependencies cannot exist in AnimatorSet (#2177)
* Improve: Expanding the factors of customize template (#2124)
* Improve: Expand DisplayAudioPlayer  (#2144)
* Improve: When issuing authorization_code, key/value needs to be added to data field (#2155)
* Improve: Add Basic Authorization header to .well-known/oauth-authorization-server (#2156)
* New: Customizable focus channel's priority (#2134)
* New: Support Text Interface v1.6 (#2159)
* New: Support PhoneCall Interface v1.3 (#2169)
* Improve: DirectiveHandler Interface change (See https://github.com/nugu-developers/nugu-android/wiki/Update-Guide#v172)

Version 1.7.0 *(2021-11-05)*
-----------------------------
* Fix: Fix condition to determine parent template (#2121)
* New: Add parentTemplateId param at render() (#2117)

Version 1.7.0 *(2021-11-03)*  
Version 1.6.4 *(2021-10-29)*
-----------------------------
* Fix: Fix onChipsClicked behavior (#2111)
    * When onChipsClicked event occur, just propagate event to callback if ChromeWindow's OnChromeWindowCallback was set.

Version 1.6.3 *(2021-10-25)*
-----------------------------
* Fix: Fix Media Template touch event handling issues. (#2105)

Version 1.6.2 *(2021-10-15)*
-----------------------------
* Fix: Declared android:exported explicitly for components with intent-filter. (Android 12 requirement) (#2094)
* New: Support DisplayInterface on Template #2097 (#2099)
* New: Support Display Interface v1.9 (#2082)

Version 1.6.1 *(2021-10-08)*
-----------------------------
* Fix: Fix crash bytebuffer.position(int) API at Android 8.1 (#2091)

Version 1.6.0 *(2021-10-01)*  
Version 1.5.4 *(2021-09-28)*
-----------------------------
* Fix: Change minSDK 19

Version 1.5.3 *(2021-09-24)*
-----------------------------
* Fix: Fix lint warnings in nugu-login-kit (#2059)
* Fix: Fix side effect for #2001 (#2077)
    * (AudioPlayer) Handling stop after finished.

Version 1.5.2 *(2021-09-10)*
-----------------------------
* Improve: Add format type at requestTTS (#2036)
* Improve: Update gRPC v1.40.1 (#2040)
* Improve: Change complete timing for Display.Close directive. (#2044)
* Improve: Re request focus if bt playing(active) (#2052)
* Improve: Remove Attachment's unused API (#2056)
    * remove hasCreatedReader()
    * remove hasCreatedWriter()
* Improve: ChromeWindow Refactoring (#2055)
    * Handle OutSideTouch and ChipsClick post process in UX-Kit
    * Making capable All ViewGroup as parent
* New: Re-add Ogg-Opus encoder (#2038)

Version 1.5.1 *(2021-09-07)*
-----------------------------
* Fix: Fix ConcurrentModificationException (#2050)

Version 1.5.0 *(2021-08-30)*
-----------------------------
* Fix: ExoMediaPlayer does not caching music streams by default (#2029)
* Improve: Remove unused jna.jar (#2031)
* New: Support Message Interface v1.5 (#2033)

Version 1.4.5 *(2021-08-30)*
-----------------------------
* Fix: Fix ExoMediaPlayer(Sample) cacheKey update order (#2022)
* Fix: Fix to synchronize during reconnecting state (#2026)

Version 1.4.4 *(2021-08-20)*
-----------------------------
* Fix: Fix show controller when receiving pause directive (#2014)
* Fix: Fix TemplateWebView when receiving UpdateMetadata directive (#2016)
* New: Remove ogg-opus encoder from NUGU-SDK (temporary) (#2018)
    * Remove temporarily,  to resolve sdk size issue.

Version 1.4.3 *(2021-08-13)*
-----------------------------
* Improve: Improve NetworkManager for better usability (#1970)
* New: Add interface to control handling for routine's start and continue directive (#2008)

Version 1.4.2 *(2021-08-06)*
-----------------------------
* Fix: Reset tts player before setSource(preapre) if READY or STARTED (#2001)
* Improve: Pause AudioPlayer explicitly if the other player is playing (#1996)

Version 1.4.1 *(2021-08-05)*
-----------------------------
* Fix: Revert "Fix to stop ServerInitiatedDirective when Disconnected #1963" (#2003)

Version 1.4.0 *(2021-07-30)*
-----------------------------

Version 1.3.4 *(2021-07-23)*
-----------------------------
* Fix: Connection leak issue in lower android-os (#1984)
* Improve: Update gRPC v1.39.0 (#1986)
* Improve: No error handling if ListenTimeout is expected (#1988)
* New: Add AudioPlayer's listener to provide more infos (#1973)

Version 1.3.3 *(2021-07-19)*
-----------------------------
* Improve: Update libs (#1974)
    * kotlin 1.4.32 -> 1.5.20
    * silvertray 4.3.6 -> 4.3.7
* New: Add NuguButton Color setting on template interface  (#1978)

Version 1.3.2 *(2021-07-12)*
-----------------------------
* Improve: Fix to stop ServerInitiatedDirective when Disconnected (#1963)
* Improve: Update libraries (#1969)
* New: Support Display Interface v1.8 (#1965)

Version 1.3.1 *(2021-07-06)*
-----------------------------
* Fix: Fix issue that TTS not played (#1953)
* Fix: An issue where the first login fails after installation on a specific device (#1954)
* New: Apply OggOpus codec for ASR (#1951)
* New: Add 'closeAll' JsInterace implementation (#1959)

Version 1.3.0 *(2021-06-29)*
-----------------------------
* Fix: An issue where the animation works when the bottom sheet is collapsed (#1945)
* Fix: Fix notify state in MessageRouter (#1948)

Version 1.2.5 *(2021-06-24)*
-----------------------------
* Fix: NuguChipsView background not working properly (#1916)
* Improve: Update ServerInitiatedDirective (#1851)
* Improve: Make App can custom action when template touched (#1922)
* Improve: Add the way App can custom close button of TemplateView (#1939)
* New: Add Template loading result interface (#1918)

Version 1.2.4 *(2021-06-23)*
-----------------------------
* Fix: Fix missing context update call (#1932)

Version 1.2.3 *(2021-06-11)*
-----------------------------
* Fix: NuguWebview unusable after detached from window (#1909)
* New: Add api to get context for RoutineAgent (#1911)

Version 1.2.2 *(2021-06-10)*
-----------------------------
* Fix: Prevent cancelling ExpectSpeech from stopRecognition(false) called. (#1905)
* Improve: Update gRPC v1.38.0 (#1900)

Version 1.2.1 *(2021-06-04)*
-----------------------------
* Fix: Change the NuguWebView setCookie url to host (#1891)
* Improve: Update VoiceChrome policy (#1862)
* Improve: support dark mode design (#1887)
    * native media player
* Improve: Update SilverTray v4.3.4 (#1894)
* Improve: Update SilverTray v4.3.6 (#1897)
* New: Support Battery Interface v1.1 (#1865)

Version 1.2.0 *(2021-05-28)*
-----------------------------
* Fix: Fix missing initiator at context (#1877)
* Improve: update media template logo spec (#1866)
* Improve: support dark mode design (#1867)
    * bypass to web template
    * nudge chips
* Improve: Send full context for Message.CandidatesListed event (#1869)
* Improve: Prevent back to IDLE state if exist preparing TTS (#1872)
* New: Add routine javascript Interface to NuguWebView (#1880)
* New: Get routine's state (#1884)
    * Add RoutineAgentInterface.getState(): State method

Version 1.1.4 *(2021-05-21)*
-----------------------------
* Fix: Fix wrong shutdown order for GrpcTransport (#1849)
* Fix: Revert "Clear chips at IDLE state #1779" (#1853)
* Fix: ChromeWindow is Freezing while dragging the BottomSheetBehavior (#1854)
* Fix: Fix ConcurrentModificationException (#1860)
* Improve: Provide keepConnection getter method to NetworkManager (#1856)

Version 1.1.3 *(2021-05-14)*
-----------------------------
* Fix: Fix concurrentModificationException (#1832)
* Fix: Fix invalid result handling for TTS.Speak (#1836)
* Fix: SetComplete immediately after preHandleDirective canceled (#1840)
* Fix: SetComplete when preHandleDirective canceled #1840 (#1841)
* Improve: Add fixedTextZoom Javascript interface to NuguWebView (#1833)

Version 1.1.2 *(2021-05-07)*
-----------------------------
* Fix: Fix missing call directive complete (#1814)
* Fix: Fix MediaTemplate layout malfunction (#1816)
* Fix: Fix crash (#1818)
* Fix: Fix mediaTemplate issues (#1823)
* Fix: Call setCompleted for ASR.ExpectSpeech after ASR finished. (#1824)
* Improve: Precache context at build NuguClient (#1827)

Version 1.1.1 *(2021-04-30)*
-----------------------------
* Fix: Clear chips explicitly (#1789)
* Fix: Notify dialogMode changed only if speaking (#1791)
* Fix: Update JadeMarble v0.2.6 (#1795)
* Fix: Fix order issue for audio display's render/clear (#1801)
    * Delay a current display's clear if a next pending directive exist
* Fix: The directive arrived after a timeout error (#1808)
* Improve: Add CustomChipsProvider Interface (#1786)
* Improve: Add ASR state param to CustomChipsProvider (#1803)
* Improve: Apply delay to removing template to avoid exposure of rear screen very short. (#1805)
* New: Add start API for Routine (#1797)

Version 1.1.0 *(2021-04-27)*
-----------------------------
* Improve: Add listener for Session (#1787)

Version 1.0.6 *(2021-04-23)*
-----------------------------
* Fix: Deliver chips, target LISTEN or SPEAKING properly (#1753)
* Fix: In ChromeWindowContentLayout, if StateChanged is not a callback, the state is incorrect (#1762)
* Fix: Fix NudgeInfo Bug (#1763)
* Fix: Fix wrong play stack order (#1774)
* Fix: Clear chips at IDLE  state (#1779)
* Fix: Fix wrong updateChips in ChromeWindow (#1782)
* Improve: Ensure to call preHandle of all directives before calling handle (#591)
* Improve: Notify dialogMode changes (#1756)
* Improve: Add listener for playSynchronizer (#1759)
* Improve: Using PlaySynchronizer to NudgeAgent logic (#1761)
* Improve: Update ChromeWindow (#1767)
* Improve: Update SPEAKING chips at Voice Chrome (#1769)
* Improve: Not stop play synced directive when routine stopped (#1770)
* New: Apply Nudge Chips Design (#1752)

Version 1.0.5 *(2021-04-15)*
-----------------------------
* Fix: FIx Template Layout Issue during Landscape (#1747)
* Improve: Ignore continue if other request running (#1744)

Version 1.0.4 *(2021-04-12)*
-----------------------------
* Fix: Fix AudioPlayer's focus not canceled (#1729)
    * Call cancel() of seamlessfocusManager when audio's play stopped before start
* Fix: Fix AndroidAudioFocusInteractor's wrong focus release (#1731)
* Improve: Apply play queue for Beep (#1708)
* Improve: Remove Escape Util (#1724)
* Improve: Update ConfigurationStore (#1734)
* New: Add Nudge to Chips Type (#1728)
* New: Support ASR Interface v1.6 (#1736)
* New: Add Speaking Target at Chips (#1738)

Version 1.0.3 *(2021-04-08)*
-----------------------------
* Fix: Fix bug in RadioListTemplate (#1646)
* Fix: Apply mobile VoiceChrome spec (#1695)
* Fix: Fix memory leak when orientation changed (#1707)
* Fix: Fix missing focusManager cancel (#1710)
* Fix: Handle re-focused for TTS.Speak directive correctly. (#1714)
* Fix: Fix wrong dismiss for ChromeWindow (#1718)
* Improve: Apply mobile VoiceChrome Spec (#1687)
* Improve: Support public API for RoutineAgent (#1701)
    * Support resume, stop API
    * Support status callback API
* Improve: Change the baseUrl of NuguOAuth to delegate (#1705)
* Improve: Update lottie 2.7.0 to 3.7.0 (#1716)

Version 1.0.2 *(2021-04-01)*
-----------------------------
* Revert "Update dokka 0.10.0 -> 1.4.30 #1684"

Version 1.0.1 *(2021-04-01)*
-----------------------------
* Enhancement: Apply close button to every display template (#1686)
* Fix: Fix SpeechRecognizer invalid operation (#1680)
    * When call startListening, startTriggerWithListening sequentially, the trigger starts sometimes. (the trigger should not start)
* Improve: Update dokka 0.10.0 -> 1.4.30 (#1684)

Version 1.0.0 *(2021-03-30)*
-----------------------------

Version 0.9.53 *(2021-03-26)*
-----------------------------
* Enhancement: Support NudgeInterface (#1664)
* Fix: Rename setCookie to addCookie in NuguWebView (#1665)
* Fix: Clear a display at specific case (#1666)
* Fix: Fix routine canceled incorrectly (#1668)
* Improve: Apply blocking policy for RequestPermission (#1670)
* Improve: Improve TriggerCallback (#1674)
* New: Support Nudge Interface v1.0 (#1582)

Version 0.9.52 *(2021-03-19)*
-----------------------------
* Fix: Apply blocking policy for AudioPlayerHandler (#1648)
* Improve: Update NuguOAuth (#1653)
* New: Support Permission Interface v1.0 (#1657)
* Improve: Way to add agent - change setProvider() method name to enable**()
* Improve: Support landscape on template

Version 0.9.51 *(2021-03-16)*
-----------------------------
* Fix: Remove unused dependencies (#1621)
* Fix: Fixed a case where the action of the current routine was incorrectly canceled (#1635)
    * If the occurred event is triggered by the current action, do not cancel it.
* Improve: Update IntroActivity (#1598)
* Improve: Improve gRPC connection for backoff and reconnect (#1627)
* Improve: Update NuguWebView (#1628)
* Improve: Move default transportFactory from ClientManager to NuguAndroidClient #1632 (#1637)
* Improve: Update ChromeWindow (#1641)
* New: Support Chips Interface v1.2 #1583 (#1640)

Version 0.9.50 *(2021-03-05)*
-----------------------------
* Fix: Ignore external audio focus result (#1617)
* Improve: Improve getContext performance (#1611)
    * Call getContext from another thread
    * Apply multi-thread
* Improve: Add annotations-api for Java 9+. (#1615)
* New: Support Display Interface v1.7 (#1607)
* New: Support AudioPlayer v1.6 (#1609)
* New: Add EARSET type at initiator (#1613)

Version 0.9.49 *(2021-02-26)*
-----------------------------
* Fix: Prevent start listening at SPEECH_END state (#1595)
* Fix: Add Idle state at SpeechRecognizerAggregator (#1601)
    * To distinguish SPEECH_END and a completion for ASR, add Idle state.
* Fix: Handle timeout issue for ASR.Recognize event (#1603)
* Fix: Prevent duplicate half-closed calls #1597 (#1605)
* Improve: Update grpc v1.36.0 (#1599)
* New: Add initiator info at startRecogition #1581 (#1586)

Version 0.9.48 *(2021-02-22)*
-----------------------------
* Fix: Fix IndexOutOfBoundsException issue #1584 (#1585)
* Fix: Fix wrong cancel for AudioPlayerTemplate (#1588)
* Fix: Fix sync for displays's update and player's resume (#1592)
* Improve: Fix update authorization without reconnect in grpc (#1590)

Version 0.9.47 *(2021-02-10)*
-----------------------------
* Fix: Fix wrong access at AudioPlayerTemplateHandler's templateDirectiveInfoMap (#1562)
* Fix: Update proguard-rules (#1570)
* Fix: Replace templateId for audioplayer's display  (#1572)
* Fix: Replace templateId for audioplayer's display #1572 (#1573)
* Fix: Fix call getDuration at wrong state (#1574)
* Fix: Prefix nugu-ux-kit resource to avoid naming conflicts  (#1576)
* Fix: Improve oauth error handling #1568 (#1577)
* Improve: Change UnsafeByteOperations to ProtobufExtensions with protobuf-javalite dependency issue (#1567)

Version 0.9.46 *(2021-01-29)*
-----------------------------
* Fix: Fix not playing audio player sometimes (#1544)
* Fix: Fix not release audio stream when DM canceled by focus (#1548)
* Fix: Fix not release playSync (#1553)
* Improve: Change method naming of NuguOAuth (#1539)
* Improve: Handle multiple onRendered call (#1557)
* New: Apply interaction control at Text.TextRedirect (#1546)

Version 0.9.45 *(2021-01-22)*
-----------------------------
* Fix: Fix to fail grpc connection when configuration fails (#1526)
* Fix: Fix timeout exception handling (#1531)
* Fix: Fix not working focus manager (#1535)
* Fix: Add missing listener registration (#1537)
    * register tts agent at InterLayerDisplayPolicyManager as listener
* Improve: Pass EPD model file path instead of EPD (#1528)
* New: Support Message Interface v1.4 (#1533)

Version 0.9.44 *(2021-01-15)*
-----------------------------
* Fix: Finish tts's playcontext when related display cleared (#1509)
* Fix: Add ExternalViewRenderer Interface to support thing like MediaNotification (#1513)
* Improve: Apply External Focus Interactor (#1505)
* Improve: Add configuration method (#1508)
* Improve: Add Custom TemplateView Constructor (#1514)
* Improve: Apply ChromeWindow in SampleApp (#1516)
* New: Send full context for ASR.ListenTimeout event (#1511)
* New: Add setVolume API at MediaPlayer (#1515)
* New: Send full context at Request***CommandIssued (#1522)

Version 0.9.43 *(2021-01-04)*
-----------------------------
* Bug: Fix ExoMediaPlayer doesn't call onRetrieved callback (#1483)
* Bug: Improve TemplateView (#1484)
* Fix: Only compare token for Routine.Stop, Routine,Continue (#1475)
* Fix: fix "Only fullscreen opaque activities can request orientation" issue (#1480)
* Fix: Fix ExoMediaPlayer doesn't calling onRetrieved callback (#1482)
* Fix: Missing call to onPostSendMessage when sending AttachmentMessage  (#1490)
* Improve: Update SilverTray v4.3.3 (#1477)
* Improve: Disables 'wait for ready' feature for the call (#1481)
* Improve: Deliver template json on update for AudioPlayer (#1485)
* Improve: Clear lastStoppedTts directive when a new directive handled (#1488)
* Improve: Custom management for BT's audio focus  (#1492)
* Improve: Cancel all directives immediately if routine's directive failed (#1495)
* Improve: Update grpc v1.34.0 (#1500)
* New: Support nugu configuration (#1330)

Version 0.9.42 *(2020-12-11)*
-----------------------------
* Fix: Fix screen orientation in oauth login (#1471)
* Fix: Fix type mismatch error (#1473)
* New: Apply template server (#1231)

Version 0.9.41 *(2020-12-09)*
-----------------------------
* Improve: Apply sdk version 29 (#1464)
* Improve: Update SilverTray v4.3.2 (#1466)
* New: Apply interaction control at Display.ControlScroll (#1460)

Version 0.9.40 *(2020-12-07)*
-----------------------------
* Fix: Add missing new backoff logic to http2 #1349 (#1442)
* Fix: Call missing callback (#1452)
* Fix: Fix threading issue for getOffset() (#1456)
* Improve: Handling error in network not available (#1440)
* Improve: Update SampleAPP to set more detailed URL scheme (#1444)
* New: Support AudioPlayer v1.5 (#1448)

Version 0.9.39 *(2020-11-27)*
-----------------------------
* Fix: Fix wrong directive result handling (#1424)
* Fix: Fix issue #1398 (#1427)
    * Send recent session correctly.
* Fix: Change wakeupInfo power parameter from mandatory to optional (#1430)
* Fix: Fix issue-1427 (#1437)
    * Fix wrong duplicated removal check
* Improve: Update AudioRecordSource for Android Q (#1397)
* Improve: Do not delay timer on player paused (#1415)
* Improve: Keep the screen on when ASRAgent is progressing (#1416)
* Improve: Update SilverTray v4.3.1 (#1419)
* Improve: Add privacy url to nugu-service-kit (#1421)
* Improve: Add getGlobalVisibleRect method for control TouchEvent in App #1404 (#1423)
* Improve: Enable/Disable external lib log (#1432)
    * Add LogSettings.enable()
* New: Support Utility Interface v1.0 (#1426)

Version 0.9.38 *(2020-11-20)*
-----------------------------
* New: Support Routine Interface v1.2 (#1400)
* Improve: Split NuguOpusPlayer (#1408)
* Improve: Update backoff for gRPC (#1349) 
* Fix not to pass outside touch event to child views in ChromeWindow (#1404)
* Fix audioplayer's deadlock issue (#1406)
* Fix: Change fields of epd param's as optional (#1401)
* Improve: Update Session Management (#1398)
* Fix constant value to android.R.id.content (#1395)
* New: Add exoplayer example for cache media #1391
* Improve: Add header field at API (#1393)
    * Replace dialogRequestId to header
* New: Add exoplayer example for cache media (#1391)

Version 0.9.37 *(2020-11-13)*
-----------------------------
* Fix: Deprecated System.UserInactivityReport (#1366)
* Fix: Fix wrong SONG spec in MediaPlayerAgent (#1369)
* Fix: Fix wrong param in MediaPlayerAgent (#1377)
* Fix: Prevent cancel already released object (#1386)
* Fix: Prevent cancel already released object #1386 (#1387)
* Improve: Apply timer when audioplayer's paused by explicitly (#1364)
* Improve: Update Routine Agent Specification (#1368)
    * Nullable playServiceId of Action
    * If receives a new routine, cancels the currently running routine and starts it.
    * Support Routine Agent v1.1 (apply postDelayInMilliseconds at Action)
* Improve: Apply cacheKey feature (#1371)
* Improve: Update context timeout policy (#1375)
    * Apply 10sec for every timeout for context except ASR.Recognize event. (keep 2sec for ASR.Recognize event)
* Improve: Pre-cache Playstack context (#1379)
* Improve: Prevent seekTo for stopped source (#1382)
* Improve:  Prevent excessive mobile network usage in MediaPlayer (#1384)
* Improve: Update Keensense v0.2.5 (#1388)
* New: Support ASR v1.4 (#1372)
    * Apply epd param of ExpectSpeech

Version 0.9.36 *(2020-11-06)*
-----------------------------
* Fix: Add missing notifications for directive failure (#1355)
    * If no handler for directive, notify it as onFailed
* Fix: Improve fallback of customTab (#1360)
* Improve: Update SilverTary v4.3.0 (#1357)
    * Support AudioAttributes instead of StreamType
* Improve: Improve warning notification for failed SDK (#1358)
* New: Support MediaPlayer v1.1 (#1342)

Version 0.9.35 *(2020-11-04)*
-----------------------------
* Fix: Revert issue-1320 (#1352)

Version 0.9.34 *(2020-11-02)*
-----------------------------
* Fix: Fix side-effect from issue-1320 (#1346)
* New: Support Text Interface v1.4 (#1341)
    * Add source field
    * Support TextRedirect directive
* New: Support TTS Interface v1.3 (#1343)

Version 0.9.33 *(2020-10-30)*
-----------------------------
* Fix: Prevent multiple rapid clicks (#1331)
* Fix: Fix wrong management for tts's playStack  (#1333)
* Fix: Fix invalid clear for current param(attr) (#1336)
* Improve: Prevent invalid holder focus acquisition (#1320)
* Improve: Fix tts's focus thread issue (#1322)
    * Solve focus change threading issue : change request from per agent  to per directive
* Improve: Update the Authentication Token via accountByInAppBrowser method (#1323)
* Improve: Replace android.util.log to nugu's LogInterface (#1328)
* Improve: Implement ThrottledOnClickListener (#1332)
* Improve: Send full context for RequestPlayCommandIssued (#1338)

Version 0.9.32 *(2020-10-23)*
-----------------------------
* Fix: Fix the parameter of referrerDialogRequestId to dialogRequestId #1295 (#1303)
* Fix: Prevent beep.play called twice. (#1308)
* Fix: Prevent tts cancel from previous focus release (#1315)
* Improve: Send full context for some events (#1304)
    * XXXCommandIssued
    * PlaybackFinished
* Improve: Cache AudioPlayer's context at init (#1306)
* Improve: Update SilverTray v4.2.6 (#1310)
* Improve: Update library (#1313)
    * Jademarble v0.2.2
    * Keensense v0.2.4
* New: Support PhoneCall v1.2 (#1317)
    * Add MakeCallSucceeded event

Version 0.9.31 *(2020-10-19)*
-----------------------------
* Improve: Apply Routine's stop(pause) condition (#1285)
* Improve: Update nugu-login-kit (#1291)
    * Fix correct listeners for each grantType
    * Add isAuthorizationCodeLogin and isClientCredentialsLogin method
    * Support login with webview when external browser startActivity fails
    * Add deprecation annotation for getMe method
    * Add listener to accountByInAppBrowser
    * Handling java.lang.SecurityException issues
    * Handling ActivityNotFound exception in openCustomTab
* Improve: Add dialogRequestId param to onDiscoverableStart of BluetoothAgent (#1295)
* Improve: Support Chips Interface v1.1 (#1298)
    * add token field at Chip
* Improve: Support Message Interface v1.3 (#1300)
* New: Support PhoneCall v1.2 (#1292)
    * Apply SearchScene field
    * Add recipient field at context
    * Deprecate SearchTarget

Version 0.9.30 *(2020-10-12)*
-----------------------------
* Fix: Fix crash issue (#1286)

Version 0.9.29 *(2020-10-12)*
-----------------------------
* Fix: Fix crash issue (#1286)

Version 0.9.28 *(2020-10-07)*
-----------------------------
* Fix: Fix wrong context to MediaPlayerAgent (#1282)
* Fix: Fix wrong calltimeout in Grpc(v1) #1277 (#1284)
* Improve: Lock more finely (#1280)

Version 0.9.27 *(2020-10-06)*
-----------------------------
* Fix: Do not clear tokens during login (#1261)
* Fix: Fix thread synchronization issue in the backoff (#1262)
* Fix: Fix wrong calltimeout in Grpc(v1) and Http2 (#1277)
* Improve: Add isExpired to NuguOAuth (#1272)
* Improve: Provide helper API to enable AudioPlayerAgent, LocationAgent (#1274)
* New: Support Routine v1.0 (#1195)
* New: Update MediaPlayer Interface v1.0 (#1254)
* New: Add ChromeWindow in nugu-login-kit (#1268)

Version 0.9.26 *(2020-09-28)*
-----------------------------
* Fix: Revert "Prevent closing AudioSource while read #1185"

Version 0.9.25 *(2020-09-28)*
-----------------------------
* Fix: Fix side effect for #1242 (#1252)
* Fix: Change provideState() of SoundAgent (#1256)
* Fix: Return AudioPlayer.Play handle after focus requested (#1259)

Version 0.9.24 *(2020-09-24)*
-----------------------------
* Fix: Cancel focus when preHandled ASR.DM canceled. (#1240)
* Fix: Fix an issue where the current request was canceled by the timeout event of the previous request (#1245)
* Improve: Prevent closing AudioSource while read (#1185)
* Improve: Apply PlaySync for Display.Update directive (#1242)
* Improve: Apply focus for Sound(Beep) (#1244)
* Improve: Notify missing callback for startListening (#1250)
    * Notify ERROR_ALREADY_RECOGNIZING while prev request executing.
* New: Add ASRBeepPlayer (#1248)

Version 0.9.23 *(2020-09-21)*
-----------------------------
* Fix: Fix missing EPD stop (#1214)
* Fix: Fix wrong stop ExpectSpeech (#1218)
* Fix: Fix missing state param in nugu-login-kit (#1220)
* Fix: Fix wrong logic to check if token has expired (#1222)
* Fix: Fix audioplayer not working (#1230)
    * Fix invalid focus change waiting
* Improve: Update SilverTray v4.2.5 (#1211)
* Improve: Do not manage focus at PhoneCallAgent (#1216)
* Improve: Add cacheMode method to NuguWebView (#1224)
* Improve: Add beep directive delegate (#1227)
* Improve: Prevent the issue of not calling onResponseStart (#1234)
* New: Delegate System.Exception directive (#1233)

Version 0.9.22 *(2020-09-11)*
-----------------------------
* Fix: Fix not return to idle state of DIalogUX (#1182)
    * Call onRecieveTTS only if acquire focus success.
* Fix: Fix using wrong expectSpeechParam  (#1192)
* Fix: Skip invalid value on build context (#1199)
* Improve: Add Last-Asr-Event-Time in grpc header (#1130)
* Improve: Use playStackControl's playServiceId for Layer policy (#1167)
    * Use playStackControl at Layer policy
    * Refresh display if same play running
* Improve: Add scope to OAuth to use server-initiative-directive (#1169)
* Improve: Allow to change logger (#1177)
* Improve: Change endpoint URI of mypage in nugu-login-kit (#1184)
* Improve: Add onResponseStart to MessageSender.Callback that occurs when the response data starts to be received (#1187)
* Improve: Remove sessionId at ExpectSpeechPayload (#1190)
    * Fix crash when call hashCode() for ExpectSpeechPayload. (sessionId is null)
* Improve: Add data field when requesting OAuth token (#1205)
* Improve: Update libs (#1207)
    * keensenese : 0.2.2 -> 0.2.3
    * jademarble : 0.2.0 -> 0.2.1

Version 0.9.21 *(2020-09-07)*
-----------------------------
* Fix: Fix crash for v0.9.20 (#1178)

Version 0.9.20 *(2020-09-04)*
-----------------------------
* Fix: Return preHandle for TTS after finish current item (#1149)
    * Ensure execution for blocked directive before next tts handle requested.
* Fix: Fix not stop requested on BUSY (#1154)
* Fix: Change NuguWebView cookies naming convention from CamelCase to kebap-case (#1158)
* Fix: Fix not working stop trigger (#1175)
* Improve: Add Last-Asr-Event-Time in grpc header (#1130)
    * Fix to send Header to Transport
    * Implement to send header in event of DefaultASRAgent
* Improve: Update policy for display management (#1152)
    * Clear evaporatable layers when new display rendered
    * Clear evaporatable layers when new play started which is not match playServiceId
* Improve: Add setTitle Javascript interface to NuguWebView (#1161)
* Improve: Prevent the tts player from starting before directive handled. (#1163)
* Improve: Return handle of AudioPlayer.Play after focus request finished. (#1165)
* Improve: Preset static context (#1173)
    * client.os
    * supportedInterfaces's static context
* New: Apply holding session while Display.Update (#1146)
    * If receive Display.Update(B) for display(A) and Session.Set(B), hold Session.Set(B) until display(A) cleared.
* New: Support Text Interface v1.3 (#1156)
    * Apply playServiceId of TextSource directive.
* New: Support Display Interface v1.6 (#1171)
    * Add UnifiedSearch1 Directive

Version 0.9.19 *(2020-08-28)*
-----------------------------
* Fix: Fix not cancel ASR.ExpectSpeech from PlaySync (#1110)
* Fix: Fix wrong foreground channel check (#1112)
* Fix: Fix play called twice (#1114)
* Fix: Fix incorrect error spec in oauth/me (#1116)
* Fix: Fix focus not release issue (#1118)
    * Fix SeamlessFocusManager's focusHolder not release focus, after ASR.DM.
* Fix: Enqueue callback not called (#1125)
* Fix: Fix invalidated release for FocusInteractor (#1127)
    * Cancels the job of release that occurred before acquire.
* Fix: Fix not working ASR (#1131)
    * Fix ASR.DM cancel by previous not finished ASR.
    * Fix ASR not working after ASR.DM failed by background focus.
* Fix: Fix invalid acquire focus at FocusHolder (#1133)
* Fix: Tracking foreground channel correctly (#1135)
* Fix: Fix thread issue for asr callback (#1138)
    * In some device, response receive before onRequested called, so pre call finish before send last attachment.
* Improve: Apply affectPersistant flag to handle exception case (#1121)
* Improve: Apply cancel for SeamlessFocusManager (#1123)
* Improve: Add new introspect of nugu-login-kit  (#1126)
* Improve: Add new introspect of nugu-login-kit (#1140)
* Improve: Add cookie to NuguWebView in nugu-service-kit (#1143)
    * Add method to set custom cookie
    * Add default cookies clientId, grantType
* New: Support PhoneCall 1.1 (#1145)
    * Add searchTargetList at SendCandidates

Version 0.9.18 *(2020-08-21)*
-----------------------------
* Fix: Fix missing callback when asr failed by background focus. (#1075)
    * Notify failure when asr start failed by background focus
* Fix: Fix acquireChannel for already active channel. (#1093)
* Fix: Fix not execute ASR.ExpectSpeech (#1097)
* Fix: Fix wrong channel when request ASR by DM (#1105)
* Improve: Improve ContextManager (#1045)
    * Request required context only.
* Improve: Return focus changes after tts stop (#1089)
* Improve: Do not wait unitil response timeout, when asr stop requested (#1095)
* Improve: Change maximum number of texts in the chips (#1103)

Version 0.9.17 *(2020-08-14)*
-----------------------------
* Fix: Clear a dialog attribute before new tts start (#1061)
* Fix: Fix wrong layer policy issue (#1067)
    * Do not clear display when start a play with same playServiceId.
* Fix: Fix missing callback when asr failed by background focus. (#1075)
* Fix: Intent extras missing when NuguOAuthCallbackActivity started (#1076)
* Fix: Eliminate duplicate sessionId when create context (#1081)
* Fix: Use distinct value for DialogRequestId,MessageId (#1083)
* Improve: Add Nugu Button Animation (#1036)
* Improve: Handling exception case for attachment (#1060)
    * Handling when no attachments are received
    * Handling exception for attachment
* Improve: Apply Sound Layer Policy (#1074)
* New: Apply CancelPolicy for DirectiveResultHandler (#1063)
    * Add a policy to decide whether to execute/cancel per directive, after a directive failed.
* New: Apply InteractionControl at Message.GetMessage (#1070)
* New: Apply Message Interface v1.2 (#1072)
    * Add EMERGENCY value at Contact.Type

Version 0.9.16 *(2020-08-11)*
-----------------------------
* Fix: Fix ASR.ExpectSpeech canceled by tts' focus. (#1050)
* Fix: Send templateId for AudioPlayer correctly (#1057)
* Improve: Apply the layer policy (#998)
    * Close the info,alert,overlay display if the other play which has no display started.
* Improve: Update SilverTray v4.2.3 (#1051)
* New: Add includeDialogAttribute flag on requestTextInput (#1053)

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
