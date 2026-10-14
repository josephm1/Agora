# Interactive template verification

Verified against the committed e-ink interactive renderer baseline `183f366f`.
The template change is isolated from the separate uncommitted Notes integration checkout.

## Automated results

- F-Droid unit suite: 3,098 tests, zero failures/errors.
- Play unit suite: 3,081 tests, zero failures/errors.
- Both debug APKs assemble successfully; packaged interactive host/sandbox/e-ink assets checked.
- Build-logic tests and `verifyKotlinFileSize` pass with no baseline exceptions.
- Packaged Chromium renderer tests pass, including the 360px-wide saved-interface fixture.
  Topic browsing, count changes and local random selection produce no sendPrompt event;
  Start produces one event with the selected inputs. Reopening resets controls.
- Strict MkDocs builds all 12 locales; template-page local links resolve.
- Staged diff whitespace checks and locale key/placeholder parity pass.

The generic tests exercise atomic CRUD, unsafe metadata/oversized payload rejection, corrupt storage,
merge/replace conflicts, stale-editor protection, exact arbitrary shortcuts, failed/foreign tool
artifacts, opt-in tools, provider projection, real Room local-run admission, composer bypass before
provider preparation, draft preservation, library interaction and archive/fork snapshot preservation.

## Reproduce

Run `./gradlew -p build-logic test`, then:

```sh
./gradlew :app:testFdroidDebugUnitTest :app:testPlayDebugUnitTest \
  :app:assembleFdroidDebug :app:assemblePlayDebug verifyKotlinFileSize --no-daemon --max-workers=2
node scripts/test-interactive-viewer.cjs
mkdocs build --strict
```

The browser script accepts PLAYWRIGHT_MODULE and CHROMIUM_PATH for existing installations.
Native submodules/Android SDK and PRoot packaging prerequisites remain the existing app build inputs.

## Remaining acceptance

No APK installation, physical-device interaction or paid model request was performed. User checks
remain: import/save pickers on Android, `/custom` in new/existing chats, linked-Skill Start confirmation,
physical e-ink presentation and an actual provider session. The Zero to Finals OSCE example includes a private offline pack of 300 Medicine/Surgery
source files. Its compact specialty/condition navigation and controls are tested in the packaged
browser sandbox. BMJ/ECG retrieval remains outside this proof of concept.

The draft PR is stacked on the renderer branch so its diff contains only this feature. For an
upstream submission after renderer PR #156 merges, cherry-pick this feature commit onto updated master.

## Composer slash-menu follow-up

Typing `/` lists all stored templates and further typing filters name/shortcut. A native Compose
preview verifies multiple choices and selection of an imported template with no shortcut. The
composer routes an explicit selected ID through local admission; missing/rejected selections
preserve the draft and never fall through to provider preparation. Busy/frozen inputs and
attachments disable selection. Tests: 23 composer submission and 2 menu tests pass on each flavor
(50 focused checks); the ARM64 F-Droid preview build and source-size check pass. Physical screen
reading diagnosed the original unregistered `/osce` message; no device interaction test or paid
model request was run for this follow-up.

The slash Send path also resolves a unique name match without an assigned shortcut. Exact
registered shortcuts win; ambiguous and unmatched slash commands remain local and preserve
the draft. Follow-up command/menu/composer tests: 28 per flavor (56 total), zero failures/skips;
ARM64 build passes. This reproduces the previously unregistered `/osce` case in host tests.

Saved-template Run now dispatches immediately in both inline and expanded presentations, without
a second confirmation. Three native Compose checks per flavor verify no-dialog single dispatch,
rejection acknowledgement without automatic retry, and retained confirmation for generated answers.
Ownership/prompt validation and the browser's active-user-gesture requirement remain in place.
Both focused flavor runs and the ARM64 replacement APK build pass. A read-only device screenshot
captured the old confirmation; no paid teaching session or physical interaction test was performed.

Interface-scroll/action-row follow-up: 8 native touch tests and 1 action-layout test pass on each
flavor (18 checks). Vertical gestures stay in the renderer even when the outer WebView/child
reports no scroll; outside vertical swipes still scroll chat. Horizontal drawer gestures, slider
claims and cancellation release pass. Both footer actions share one row at 328dp with independent
click actions. ARM64 APK and source-size checks pass. The initial device screenshot was read-only;
physical scroll acceptance remains with the user.

Boundary-handoff follow-up: 12 native touch checks per flavor pass (24 total), including top and
bottom handoff, mid-drag boundary updates, and slider ownership at an otherwise unscrollable edge.
Packaged Chromium tests verify actual root/nested scroll limits, touch panning across pointer
cancellation, touch-end cleanup, existing e-ink paging and bridge isolation. Bounds are reported
only on state changes, with a fresh report at each touch start. The latest helper is verified
byte-for-byte inside the rebuilt ARM64 APK. Official Android WebView/Compose and Chrome scroll
chaining references are recorded in the engineering contract. Physical acceptance remains user-run.

Expanded-save follow-up: the inline card now offers only Expand. Save is available in the expanded
viewer toolbar, retaining native artifact validation and linked-Skill metadata. After success it
is disabled until that viewer is closed/reopened; no save runs on opening or recomposition. Two
native action tests per flavor pass (4 checks), along with the focused F-Droid template tests,
source-size check and ARM64 build. Device installation preserves app data; physical acceptance
remains user-run.
