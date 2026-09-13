# Inkhorn design notes

**Reading this as:** a narrative idle game for adult readers who want an ending, with a
working-manuscript language, leaning toward Grenze Gotisch plus Alegreya on a verdigris and
iron-gall palette.

**Dials**

- DESIGN_VARIANCE 4. Reading needs a steady column. The growing initial is the only asymmetry: it
  sits left of the chapter's opening sentence and everything else keeps to one centred column capped
  at 600dp.
- MOTION_INTENSITY 3. Idle numbers already move, so motion only answers taps: new ledger rows fade in
  200 ms, unchosen options fade 250 ms on commit, a light haptic on a tap. One first-run moment: the
  first ledger line inks in left to right over 900 ms, once per install. Under reduced motion every
  one of these snaps, and cooldowns show seconds as text.
- VISUAL_DENSITY 5. Stores, actions and the ledger share one screen, split by spacing and hairline
  dividers, never by cards.

## Tokens

| Token | Role | Light | Dark | True black |
|---|---|---|---|---|
| Vellum | background | #E9EEE8 | #131A1B | #050707 |
| Leaf | sheets, option blocks, nav bar | #F5F7F2 | #1B2426 | #101617 |
| IronGall | text, icons | #1C2233 | #E2E8E1 | #E2E8E1 |
| FadedInk | secondary text | #4F5A6E | #9AA8A3 | #9AA8A3 |
| Verdigris | the one accent: buttons, vine, selected nav | #1F7466 | #62B8A8 | #62B8A8 |
| Rubric | irreversible warning and errors only | #A33A2C | #E48A78 | #E48A78 |

Dividers are IronGall at 12 percent. Button labels are Leaf on light and Vellum on dark.

## Type

- Grenze Gotisch SemiBold: the initial (72sp) and chapter and ending titles (28sp) only. Screen,
  sheet and dialog titles (Workshop, Settings, a choice, While you were away) are Alegreya Medium 24sp.
- Alegreya Regular, Italic, Medium, Bold: everything read. Body 16sp at 24sp line height, a 60
  character measure; headings 20sp Medium; labels 14sp Medium. Counts use `lnum, tnum`.
- Static instances cut from the Google Fonts variable masters with fontTools (wght 400, 500, 700,
  italic 400; Grenze Gotisch 600). OFL 1.1 texts in `docs/`.

## Shape

RadiusSm 4dp buttons and options, RadiusMd 8dp away entry and snackbars, RadiusLg 16dp sheet tops.

## The one memorable thing

The growing initial. The chapter's first letter set large in Grenze Gotisch, with a verdigris vine
that draws itself around the letter in proportion to the chapter goal. The path is trimmed to the
progress fraction on every frame from state, so it reads correctly with animations off. A faint
IronGall guide shows the whole path so the unfinished part reads as unfinished.

## Notes while building

- Actions sit above the stores, not below them. Every new store row (folios after the first tap,
  pennies after the first sale) used to push the buttons down under the player's thumb.
- Store counts show tenths below ten, so the first copied line reads 0.2 folios instead of 0.
- Rates in the store rows are measured over the next second of real production, so a pedlar waiting
  on an empty folio store shows no rate rather than a hopeful one.
- Routine ledger rows fold together while they are the newest rows: "Copied a line, thirty-one
  times". Story, choice and chapter rows always stand alone and read larger, in IronGall.
- Hand work from long-past chapters leaves the ledger: only this chapter's actions and the last
  one's stay, so chapter five is not a column of seven buttons.
- The status bar inset is padding outside every scrolling list, so rows never slide under the clock.
- The first-run moment is the chapter one opening line inking in left to right. It plays once per
  install and never under reduced motion.
- Grenze Gotisch's capitals I and N read as a D and "Il" on their own. Chapter two's opening now
  begins "The ink runs short" so its initial is a T; the credits wordmark, the landing page wordmark
  and the store captions are set in Alegreya Bold. The feature graphic's wordmark moved to Alegreya Bold
  too, a deliberate departure from blueprint section 11, because "Inkhorn" in Gotisch read as
  "Dnkhorn".
- The icon is teal (#2CC4A8 to #1AA29A) with an iron-gall horn and a notched quill lit by a Leaf
  edge, the row already reserved in the paid batch registry.
- Nav icons: AutoStories, Handyman and HistoryEdu from Material Icons Rounded, with labels. The
  blueprint lists chrome icons only; a bottom bar without icons reads as broken, so these three were
  added.

## Review pass

- A save file that was valid JSON but described a game the content cannot run (chapter 99, an unknown
  choice, a negative store, duplicate ledger ids) was accepted, and then crashed the app on every
  launch. `engine/SaveCheck` now refuses such a file at load ("That file is not an Inkhorn save.") and
  treats such a snapshot in the database as unreadable, falling back to the previous save or the
  unreadable-save screen instead of crash-looping.
- A failed import stopped the game clock until the next trip to the background; it now resumes.
- A store of 1.03 folios read "1 folios"; nouns now agree with the figure as written.
- No away sheet when nothing was replayed (a choice was already waiting), so it no longer covers the
  choice with "part of a day's copying, and nothing came of it".
- Screen, sheet and dialog titles moved from Grenze Gotisch to Alegreya, as blueprint section 7 asks:
  the Gotisch L on "Licences" read as a pound sign. The privacy policy and licence pages now render
  as spaced paragraphs instead of one block and hard-wrapped lines.
- The opening said "one skin of vellum" while the store showed three; it now says three.
- The story grew from about 7,200 to about 9,000 words: 63 more chapter lines, weighted toward the long
  later chapters, so a line lands every five to ten minutes. The blueprint planned roughly 15,000; the
  rest (longer endings, more choice aftermath) is the main thing left for v1.1.
