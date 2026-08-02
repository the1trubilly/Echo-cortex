# Android Jarvis — Echo Handoff

## Current milestone: OpenAI API-key persistence

### Scope completed

- Reproduced the reported persistence state on USB device `R5CX31PBW7V`. The private credential preferences file existed, but it contained neither the encrypted API-key entry nor its IV.
- Confirmed the failure was an unsaved settings draft: the previous UI saved only when the explicit Save/Replace button was tapped, while Close and system Back discarded typed input.
- Unified Save/Replace, keyboard Done, Close, and system Back through one save-and-verify path.
- Added an inline failure state. If encryption or storage fails, Settings remains open and reports the error instead of showing a false saved state.
- Installed the corrected APK and confirmed the encrypted API-key and IV entries exist.
- Fully stopped and relaunched the app, reopened Settings, and confirmed it still displays `API key saved`.

### Exact files changed

- `app/src/main/java/com/google/ai/edge/gallery/ui/home/SettingsDialog.kt` — persists a nonblank pending key on every exit path and verifies the repository can read it back before reporting success.
- `app/src/main/res/values/strings.xml` — adds the credential-save failure message.
- `ECHO_BRIEF.md` — records the durable credential-draft persistence rule and root-cause discovery.
- `ECHO_HANDOFF.md` — records this fix and its build/device evidence.

### Commands and evidence

- App-private storage inspection before the fix found a 153-byte `openai_credentials.xml` with zero API-key/IV entries.
- `gradlew.bat testDebugUnitTest assembleDebug` returned `BUILD SUCCESSFUL` in 25 seconds.
- Installing `app-debug.apk` on device `R5CX31PBW7V` returned `Success`.
- App-private storage after the fix contained both required encrypted entries; no plaintext credential was read or printed.
- After a full force-stop and relaunch, the Settings UI exposed its password field and displayed `API key saved`.
- The running app's Logcat contained no startup, Android Keystore, AES/GCM, or credential-storage crash.

### Unresolved problems

- Persistence is confirmed; successful OpenAI inference with the currently saved credential has not been tested in this milestone.
- Any credential pasted into a chat should be considered exposed and rotated even when the app stores its local copy securely.

### Recommended next step

Run one minimal OpenAI text request from Jarvis to distinguish successful authentication from quota, project-access, or model-access errors. Do not treat persistence alone as proof that the provider credential is valid.

## Previous milestone: safe Back navigation and one-tap GitHub skills

### Scope completed

- Reproduced the reported Back failure on USB device `R5CX31PBW7V`. Logcat confirmed a `NullPointerException` in `HomeScreen.kt:901`, where the retired home grid force-unwrapped the unregistered AI Chat task.
- Replaced Jarvis's Back destination with an in-session Android Jarvis control center. Both the system Back gesture and toolbar arrow now expose Settings, Skills, MCP servers, and Model Manager without unloading the current model.
- Made Settings, Skills, and MCP return to the control center when dismissed.
- Kept the old Home screen out of the Jarvis Back path; Model Manager remains directly reachable as a secondary destination.
- Upgraded the embedded GitHub skills page from a read-only browser into an installer. The official `skills/featured` folder renders inside the app and receives a neon `+` button beside each skill row.
- Added a narrow WebView bridge that accepts only exact official featured-skill folder URLs. Each request still passes through the existing `SkillManager` parser, duplicate check, persistence, and enabled-selection logic.
- Added conversion from ordinary GitHub tree/blob URLs to the raw-content base URL needed to retrieve `SKILL.md`, plus unit coverage for tree, blob, query-string, and direct URL forms.
- Updated Jarvis onboarding/add-skill copy to describe the in-app GitHub catalog rather than requiring GitHub Discussions URL copying.

### Exact files changed

- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/JarvisControlCenterBottomSheet.kt` — new Jarvis secondary-navigation hub for Settings, Skills, MCP, and models.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatScreen.kt` — routes Back into the control center, coordinates nested managers, and preserves the active chat session.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/SkillManagerBottomSheet.kt` — turns the in-app official GitHub page into a one-tap, status-reporting skill catalog.
- `app/src/main/java/com/google/ai/edge/gallery/data/Consts.kt` — adds the official featured-skills catalog URL.
- `app/src/main/java/com/google/ai/edge/gallery/skills/SkillManager.kt` — normalizes GitHub tree/blob pages to raw-content skill bases before the existing validation flow.
- `app/src/main/java/com/google/ai/edge/gallery/ui/common/chat/ChatView.kt` — distinguishes opening Jarvis controls from actually leaving a chat, preventing unnecessary model cleanup.
- `app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatScreen.kt` — passes the model-cleanup navigation policy through shared chat layers.
- `app/src/main/java/com/google/ai/edge/gallery/ui/navigation/GalleryNavGraph.kt` — removes legacy Home from the Jarvis navigation callback and points explicit model navigation to Model Manager.
- `app/src/main/res/values/strings.xml` — adds control-center/install status copy and updates GitHub catalog wording.
- `app/src/test/java/com/google/ai/edge/gallery/skills/SkillManagerUrlTest.kt` — verifies remote skill URL normalization.
- `ECHO_BRIEF.md` — records the durable navigation and skill-install architecture.
- `ECHO_HANDOFF.md` — records this milestone and its command/device evidence.

### Commands and evidence

- Initial reproduction: cold launch followed by system Back produced a fatal `NullPointerException` at `HomeScreen.kt:901` and returned to the launcher.
- `gradlew.bat testDebugUnitTest assembleDebug` returned `BUILD SUCCESSFUL` for the final source.
- The final APK install returned `Success`; cold launch returned `Status: ok` for `com.google.aiedge.gallery/com.google.ai.edge.gallery.MainActivity`.
- A system Back event visibly opened `Android Jarvis — Settings, skills, tools, and models`; `MainActivity` remained `topResumedActivity`, and the Android crash buffer was empty.
- Settings visibly opened from the hub and system Back returned to the same hub.
- The official GitHub `skills/featured` page rendered inside Jarvis with neon `+` buttons beside `mood-music`, `restaurant-roulette`, and `virtual-piano`.
- Tapping the rendered `mood-music` `+` produced native logs for GitHub-to-raw conversion, fetched `SKILL.md`, added the parsed skill, and reported `Successfully added skill from URL: mood-music`.
- The browser changed the installed row to `✓` and displayed `mood-music installed and turned on`.
- After a full app force-stop and cold restart, Manage Skills search found `mood-music` under Custom Skills with its toggle checked.

### Unresolved problems

- The official live catalog currently contains three skills. Broader community discovery will require a catalog/index format that supplies a trustworthy installable folder URL per entry; arbitrary Discussion posts are intentionally not granted bridge access.
- A GitHub DOM redesign could require updating the small button-injection selector. Native URL allowlisting and validation remain independent of that presentation layer.
- The OpenAI credential pasted into chat must not be propagated into commands or source. It should be revoked because it was exposed, and a replacement should be saved through the encrypted Settings field or dropped as a local file in `Desktop\Echo Downloads` for safe installation.

### Recommended next step

Add a native searchable skill index sourced from a signed/curated manifest while keeping the in-app GitHub detail view and existing validation path. Then implement the OpenAI Responses tool-call loop so installed Skills and MCP tools work with the GPT-5.6 tiers.

## Previous milestone: unified Jarvis interface

### Scope completed

- Made Jarvis the application start destination instead of the Edge Gallery task grid.
- Reused the existing Agent Skills chat as the single multimodal Jarvis surface, including model selection, image/audio attachment controls, Skills, MCP, history, and inline tool-result UI.
- Changed the default agent instructions so normal questions receive normal answers and Skills/MCP tools are optional capabilities.
- Added the existing OpenAI runtime choices to Jarvis and routed local versus cloud execution through the shared `AgentRuntimeExecutor` contract.
- Replaced provider codenames with the requested user-facing model tiers: `GPT-5.6 High`, `GPT-5.6 Medium`, and `GPT-5.6 Instant`. Kept the actual API model IDs unchanged and removed the duplicate `chat-latest` Instant card.
- Replaced the cloud models' leaked placeholder file paths/download icons with a cloud icon and `Cloud · No download required` status.
- Unregistered the old AI Chat task so Jarvis is the sole general chat surface and the three OpenAI models are not contributed twice.
- Changed global model aggregation to deduplicate by the documented stable model ID (`Model.name`) rather than mutable full-object equality.
- Changed the Jarvis back path from the global model selector to Home so the navigation drawer and Settings are reachable.
- Stopped registering Tiny Garden and standalone Mobile Actions as user-facing tasks. Their source remains dormant for later removal or extraction of permission-gated device capabilities.
- Replaced remaining primary-screen `Agent Skills` onboarding language with Jarvis language.

### Exact files changed

- `app/src/main/java/com/google/ai/edge/gallery/ui/navigation/GalleryNavGraph.kt` — adds the primary Jarvis route and selects GPT-5.6 Medium as the initial model when no Jarvis model is active.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/agentchat/AgentChatTaskModule.kt` — adds OpenAI models, provider routing, and normal-chat-first capability instructions.
- `app/src/main/java/com/google/ai/edge/gallery/data/OpenAiModels.kt` — exposes the three requested GPT-5.6 tier labels while retaining Sol/Terra/Luna only as internal provider IDs.
- `app/src/main/java/com/google/ai/edge/gallery/ui/common/ModelPicker.kt` — presents cloud models as cloud services rather than exposing their internal placeholder path.
- `app/src/main/java/com/google/ai/edge/gallery/ui/common/modelitem/StatusIcon.kt` — uses a cloud status icon for OpenAI entries instead of a downloaded-file icon.
- `app/src/main/java/com/google/ai/edge/gallery/ui/llmchat/LlmChatTaskModule.kt` — keeps the old AI Chat implementation dormant but removes its separate Hilt task registration.
- `app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/ModelManagerViewModel.kt` — deduplicates global models by stable model ID.
- `app/src/main/java/com/google/ai/edge/gallery/ui/modelmanager/GlobalModelManager.kt` — applies the same stable-ID rule to model variants.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/tinygarden/TinyGardenTaskModule.kt` — removes Tiny Garden from Hilt's visible task set without deleting its source.
- `app/src/main/java/com/google/ai/edge/gallery/customtasks/mobileactions/MobileActionsModule.kt` — removes Mobile Actions from Hilt's visible task set without deleting action code intended for later capability extraction.
- `app/src/main/res/values/strings.xml` — renames the agent surface to Jarvis and explains normal chat, supported media, and optional skills.
- `ECHO_BRIEF.md` — records the unified-interface, model-naming, and future capability decisions.
- `ECHO_HANDOFF.md` — records this milestone and its verification evidence.

### Commands and evidence

- `gradlew.bat testDebugUnitTest assembleDebug` returned `BUILD SUCCESSFUL`.
- The debug APK installed on USB device `R5CX31PBW7V` with `Success`.
- A cold launch of `com.google.aiedge.gallery/com.google.ai.edge.gallery.MainActivity` returned `Status: ok` and left that activity as `topResumedActivity`.
- The Android crash buffer was empty after startup.
- A device screenshot confirmed that the app opens directly to the black/neon Jarvis chat, with the Skills and MCP controls visible and `OpenAI · GPT-5.6 Medium` selected.

### Unresolved problems

- The OpenAI runtime does not yet send Skills/MCP definitions through the Responses API or execute its returned function calls. Skills and MCP tools remain functional for compatible local agent models; cloud tool calling is the next provider-runtime milestone.
- OpenAI accepts text and images in the current implementation but rejects raw audio clips. Provider-independent speech-to-text should be added before full duplex voice.
- The main Jarvis screen still uses a back-arrow affordance to reach secondary model-management screens. A later shell pass should replace it with a clearer settings/capabilities navigation affordance.
- The configured OpenAI key still returns `invalid_api_key`, so successful cloud inference remains unverified.
- A direct redacted check of the only candidate in `Desktop\bluetooth_content_share.html` also returned HTTP 401 `invalid_api_key`; it was deliberately not written back into the app. A different key exists in the Codex process environment, but using it requires the user's explicit authorization.

### Recommended next step

Implement the OpenAI Responses tool-call loop against the existing `AgentTools`/`RuntimeToolDispatcher` boundary so Skills and MCP tools work with all three GPT-5.6 tiers. Then add provider-independent speech-to-text to the same composer.

## Previous visual milestone

- Adopted the user-selected J.A.R.V.I.S. poster as Android Jarvis's canonical in-app brand artwork.
- Produced a faithful square robot-only derivative for small icon surfaces, preserving the neon-green Android body, cyan reactor head, and red center light while removing only the too-small wordmark.
- Applied the new mark to the adaptive launcher icon, splash screen, top app bar, and Learn Something New card footer.
- Added the full poster to the home hero without stretching it.
- Replaced the previous blue/light palettes with a locked black-and-neon-green visual system, retaining cyan and red only as supporting colors from the supplied artwork and for semantic states.
- Replaced the obsolete Light/Dark/Auto theme selector with an explained `Jarvis Neon` identity so users cannot accidentally restore the old light appearance.
- Preserved model, chat, agent, prompt, OpenAI, and settings behavior.

## Exact files changed

- `app/src/main/res/drawable-nodpi/jarvis_brand_poster.jpg` — unmodified copy of the user-selected Desktop poster used on the home screen.
- `app/src/main/res/drawable-nodpi/jarvis_brand_icon.png` — launcher-safe robot-only derivative used by Android/Compose brand surfaces.
- `app/src/main/assets/skills/learn-something-new/assets/jarvisBrandIcon.png` — compact mark packaged for the skill's generated-card canvas.
- `app/src/main/java/com/google/ai/edge/gallery/GalleryAppTopBar.kt` — loads the raster brand mark directly for the top bar.
- `app/src/main/java/com/google/ai/edge/gallery/ui/home/HomeScreen.kt` — adds the full poster to the home hero while retaining the accessible Android Jarvis title.
- `app/src/main/java/com/google/ai/edge/gallery/ui/home/SettingsDialog.kt` — replaces the theme switcher with the fixed Jarvis Neon identity and explanation.
- `app/src/main/java/com/google/ai/edge/gallery/ui/theme/Color.kt` — defines black, neon-lime, cyan, supporting green, and semantic error tokens for both legacy token sets.
- `app/src/main/java/com/google/ai/edge/gallery/ui/theme/Theme.kt` — updates custom task/chat/banner colors and locks Compose/system-bar appearance to Jarvis Neon.
- `app/src/main/res/drawable/icon.xml` — keeps the internal launcher/splash integration filename while pointing to the new raster mark.
- `app/src/main/res/drawable/logo.xml` — keeps the internal logo integration filename while pointing to the new raster mark.
- `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` — uses a black adaptive-icon background and removes the unsuitable full-color monochrome layer.
- `app/src/main/res/values/ic_launcher_background.xml` — changes the launcher background token to black.
- `app/src/main/res/values/themes.xml` — changes the base, splash, system bars, and licenses appearance to black/neon dark styling.
- `app/src/main/res/values-night/themes.xml` — aligns night splash and licenses surfaces with the same identity.
- `app/src/main/res/values/strings.xml` — adds brand-art accessibility and Jarvis Neon settings copy.
- `app/src/main/assets/skills/learn-something-new/scripts/index.html` — uses the new PNG mark and neon footer colors in generated cards.
- `ECHO_BRIEF.md` — records the durable visual-identity decisions and platform resource constraint.
- `ECHO_HANDOFF.md` — records this milestone and its verification evidence.

## Image-generation record

- Built-in image editing was used with the Desktop poster as the edit target.
- Final request: create a square Android adaptive-launcher composition by reframing the existing neon-green Android robot and circular reactor head; preserve the robot, head details, red center, cyan highlights, lighting, and black background; keep the full silhouette inside adaptive-icon safe zones; omit only the J.A.R.V.I.S. wordmark; add no text, objects, symbols, scenery, or watermark.
- The generated source remains outside the repository under Codex generated images. The project-bound final is `res/drawable-nodpi/jarvis_brand_icon.png`.

## Commands and evidence

- The selected Desktop source was `Echo Downloads/Screenshot_20260802_102115_ChatGPT.jpg`, 1080 × 1206.
- The launcher-safe derivative is 1254 × 1254.
- `gradlew.bat testDebugUnitTest assembleDebug` returned `BUILD SUCCESSFUL` in 35 seconds: 3 suites, 8 tests, 0 failures, 0 errors, 0 skipped.
- The first phone install returned `Success`, but device Logcat exposed a startup crash because Compose does not accept an Android `<bitmap>` wrapper through `painterResource`. The top bar was corrected to load the PNG directly.
- The final `gradlew.bat assembleDebug` returned `BUILD SUCCESSFUL` in 6 seconds.
- The corrected APK install returned `Success`.
- The corrected launcher activity remained focused and the Android crash buffer was empty.
- Device screenshots confirmed the supplied poster on the home screen, black canvas, neon-green text and controls, cyan supporting accents, green near-black cards, and the compact mark in the top bar.
- Settings visibly showed `Jarvis Neon` and its black/neon explanation while retaining OpenAI key, saved-prompt, and other settings controls.
- The Niagara launcher `A` section visibly showed `Android Jarvis` with the new robot icon centered and unclipped.

## Unresolved problems

- No feature-blocking visual or startup problem remains in this milestone.
- The user-selected source is a JPEG screenshot rather than a layered/vector master. Future high-resolution brand work should preserve this raster original or replace it only with an explicitly approved master asset.
- Android 13 themed/monochrome launcher icons are intentionally disabled because a full-color reactor/robot raster is not a valid monochrome mask. A dedicated single-color silhouette can be designed later if themed-icon support becomes important.
- The separate OpenAI milestone still lacks a successful cloud response because the selected API key returns `invalid_api_key`.
- Pre-existing build warnings and untracked `.idea/` plus `gradle/gradle-daemon-jvm.properties` paths remain untouched.

## Questions for Echo

- Should the next visual pass replace the remaining upstream Gemma promotional copy/graphics on the home screen with Jarvis-specific product language, or retain them while model compatibility is still inherited from Edge Gallery?
- Should a dedicated neon single-color Android/reactor silhouette be created for Android themed-icon support?

## Recommended next step

Replace the remaining home promotional copy with a concise Android Jarvis capability statement, then apply the same black/neon shell to the unified chat, tools, image, and voice surfaces as they converge. Keep the full poster reserved for the home identity and use the compact robot mark only where the available size is too small for the wordmark.
