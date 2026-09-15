# PDX native design

Reference: the user-updated `Launcher design scope/Herdr Launcher.dc.html`, “hidden-chrome set”, updated 2026-09-14. The current rule is **larger content, with help reserved for Select**. Tap Select for a three-second bottom hint strip; hold for a full map. This supersedes the earlier persistent-strip request.

The first measured device, RG Rotate, is 720×720 at 240 dpi, about 480dp across. The app uses immersive content with transient Android bars available by edge swipe. Layout comes from actual window constraints, not the artboard’s CSS pixel assumption. Fonts are bundled, unmodified Space Grotesk and JetBrains Mono; source commits, SHA-256 hashes and OFL licenses are included in `licenses/`.

PDX v0.3.0 uses an original pixel-letter PDX launcher icon in the existing dark/white/amber palette. The compact name appears in Android app/home selection and Diagnostics. The agent home remains focused on real content, with no new permanent title bar. Other screen shapes require device validation; this measurement is not a universal handheld layout.

| Area | Reference | Implemented behavior |
|---|---|---|
| Home | 3b | Large real agent names, reported status dots and text, one white focus outline, no app bar or repeated card borders. D-pad and touch navigate the list. |
| Reading | 5a | Full-width black reading surface, grey left edge, small READ/target label, automatic touch and D-pad scrolling. Latest resumes following after browsing. |
| Input | 5b | Amber left edge and INPUT label, real ANSI viewport, exact button hints. |
| Actions / settings / apps | 3g, 3h, 4a, 4j | Dark rounded modals, large selectable rows, transient bottom hints. Installed apps and connection details come from the device. |
| Compose | 3i | Native editor and recipient review in a modal. Encrypted local drafts; sending requires explicit review and control. |
| Control confirmation | 3f | Shows the real host/session/pane before requesting ownership. Does not claim that ownership is available before Herdr grants it. |
| Assistant / account / voice | 7b–7h | Native dark modals, real subscription status, durable assistant context, literal transcript review and amber proposed-action labels. No QR or token-storage cards. |
| Button map | 3e | Hold Select to show the actual target, connection/access and full map; release closes it without triggering the tap action. |

Start tap opens System; holding Start for 600ms returns Home. Select is help-only in every mode. A in READ opens the control confirmation; holding Select shows the map. A stays release-only. Opening System from input releases control; B closes a modal or exits input locally. Presses held across closing the map are cleared so release cannot become Enter.

Home titles, status labels, counts and output are actual Herdr data. The reference’s invented agents, prompts, timers, battery/Wi-Fi examples and progress values are not copied. No prompt extraction or inferred approval labels are added. The System modal uses large navigable rows rather than miniature app tiles at the measured density. The same transient strip is available on settings, diagnostics, apps, drafting, voice, assistant, account, context and confirmation screens. It overlays the bottom without resizing the terminal. D-pad and Back are omitted from the strip; less obvious controls remain. Button pills and legends use their natural width and stay on one line; equal-width columns are avoided because they wrapped Select. Device UI checks inspect text layout and strip bounds.

Read output refreshes at the foreground polling interval. The visible snapshot and scroll position stay stable while browsing; at most one newer snapshot is held. Reaching the bottom or tapping Latest applies it. The source is Herdr’s recent-output window, up to 160 source lines, not unlimited history. Only explicit input switches to the live ANSI viewport.

The native container owns focus during browsing and routes controller events at the pre-IME stage, so Android does not consume the first D-pad down after touch to select a toolbar control. Text editors retain normal focus. See the [Android 12 input pipeline](https://android.googlesource.com/platform/frameworks/base/+/android12-release/core/java/android/view/ViewRootImpl.java) and [Compose font API](https://developer.android.com/develop/ui/compose/text/fonts).

Actual-device screenshots are reviewed locally and excluded from the archive because they contain private terminal output. The report distinguishes injected device events from printed-button calibration and sustained handheld comfort, which remain hands-on work.

Agent CHAT (v0.4.1) uses large native message text, a compact title/status header, distinct user bubbles, and a bottom reply/Mic row. Assistant Markdown headings, emphasis, lists and code render without raw delimiters. Terminal switches to the colored native reader. Select still reveals the same temporary hint strip over the bottom without moving content. A opens Input with controller, voice and keyboard choices; Y tap writes and Y hold dictates to the currently selected agent. Voice never changes the recipient to the assistant merely because the agent is in READ.

In v0.4.2, a compact Messages / Terminal selector sits in each read header. Both names remain visible on one line, with the current view marked in amber. X then A switches through the first Actions item. Messages is the default for supported agents, including return from input/background; load errors stay in Messages with an explicit Retry action. This keeps the reply bar available without silently dropping into terminal output.
