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
physical e-ink presentation and an actual provider session. The abbreviated OSCE fixture validates
interface reuse; medical content coverage and BMJ/ECG retrieval are deliberately deferred.

The draft PR is stacked on the renderer branch so its diff contains only this feature. For an
upstream submission after renderer PR #156 merges, cherry-pick this feature commit onto updated master.
