# Inkhorn

Inkhorn is a slow-reveal idle game about the medieval book trade, told as a written ledger. It opens
with one line of ink and one button, grows from a copying desk to an ink mill, a bindery, a lending
library, a press and a printing city, asks eleven choices that cannot be taken back, and ends in one
of three endings with credits and a second chronicle. It is for adult readers who like incremental
games but want a story with a last page. Paid once; no ads, no purchases, no account.

Everything runs on the device. The app declares no internet permission and sends nothing anywhere.

## Build

Requires JDK 17 and the Android SDK with platform 36.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
./gradlew bundleRelease       # needs keystore.properties, see below
```

`keystore.properties` and the `.jks` are not committed. Without them the release build stays
unsigned instead of failing:

```properties
storeFile=<slug>-upload.jks
storePassword=...
keyAlias=<slug>
keyPassword=...
```

## Layout

```
app/src/main/kotlin/com/mohdshayan/inkhorn/
  App.kt, MainActivity.kt   process and activity entry points
  di/                       ServiceLocator, the manual dependency container
  engine/                   the game as plain Kotlin, no Android imports: GameState, Content, Engine,
                            CatchUp and Projection, EndingResolver, AwaySummary, NumberWords,
                            SaveCodec, SaveCheck (refuses saves this content cannot run), ChronicleText
  content/                  ContentLoader, reads assets/story.json
  data/GameRepository.kt    the running game: 10 Hz tick, autosave, catch-up, chapter-note job
  data/SaveTransfer.kt      save file out through the share sheet, and back in from a document
  data/prefs/               DataStore settings (AppPrefs)
  data/db/                  Room: runs, snapshots, ledger, choices
  work/                     ChapterNoteWorker and the one notification
  review/                   the single Play review prompt after chapter three
  ui/theme/                 verdigris and iron-gall tokens, Grenze Gotisch and Alegreya, shapes, motion
  ui/nav/                   AppNav and the type-safe routes
  ui/ledger/                Ledger, the growing initial, choice, away and notes sheets
  ui/workshop/ ui/chronicle/ ui/ending/ ui/settings/
app/src/main/assets/        story.json, privacy-policy.html, font licences
app/src/test/               JVM tests: engine ticks, catch-up, projection, endings, balance, content,
                            save codec, number words, and EdgeCaseTest (corrupt save files, zero and
                            huge values, cap boundaries, daylight saving and time zones); SaveScenarios
                            writes demo saves when SAVE_OUT is set
tools/                      build_story.py and story_more.py write story.json; balance_sim.py is a fast
                            Python port of the balance test for tuning
store/                      Play listing copy, icon, feature graphic, raw screenshots (screenshots-raw/)
                            and captioned ones (screenshots/, from captions.json)
docs/                       the GitHub Pages site: landing page, privacy policy, fonts, screenshots and
                            font licences
```

The story and every number in it live in `tools/build_story.py`. Run `python3 tools/build_story.py`
after editing it, then `python3 tools/balance_sim.py` for a quick read and `./gradlew testDebugUnitTest`
for the real check: `BalanceSimTest` fails if a greedy player does not finish chapter one inside 30
minutes and the whole arc inside 6 to 10 hours with every choice strategy.

## Licences

- Grenze Gotisch by Omnibus-Type, SIL Open Font License 1.1 (`docs/OFL-GrenzeGotisch.txt`).
- Alegreya by Huerta Tipografica, SIL Open Font License 1.1 (`docs/OFL-Alegreya.txt`).
- The bundled TTFs are static instances cut from the Google Fonts variable masters with fontTools.
- Material Icons (Rounded), Apache License 2.0, through `material-icons-extended`.
- `story.json`, the story text and every game number, is original to this project and ships under the
  same copyright as the app.
- Google Play In-App Review (`review-ktx`), covered by the Play Core Software Development Kit Terms of Service.

Copyright SocialSure Private Limited.
