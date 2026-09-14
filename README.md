# HeliBoard
HeliBoard is a privacy-conscious and customizable open-source keyboard, based on AOSP / OpenBoard.
Does not use internet permission, and thus is 100% offline.

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png" alt="Get it on F-Droid" height="80">](https://f-droid.org/packages/helium314.keyboard/)
[<img src="https://user-images.githubusercontent.com/663460/26973090-f8fdc986-4d14-11e7-995a-e7c5e79ed925.png" alt="Get APK from GitHub" height="80">](https://github.com/HeliBorg/HeliBoard/releases/latest)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png" alt="Get it on IzzyOnDroid" height="80">](https://apt.izzysoft.de/fdroid/index/apk/helium314.keyboard)

## Table of Contents

- [Features](#features)
- [Contributing](#contributing-)
   * [Reporting Issues](#reporting-issues)
   * [Translations](#translations)
   * [To Community](#to-community)
   * [Code Contribution](CONTRIBUTING.md)
- [Links](#links)
- [License](#license)
- [Credits](#credits)
  * [Funding](#funding)

## 🏷️ Fork feature: Style Tags

This fork adds a **style tags** feature: type `<tag content>` anywhere in
any text field, and as soon as you type the closing `>`, the keyboard
replaces the raw sequence with a transformed version — Unicode styled
text, or a small utility insertion (date, time, clipboard content,
random number, case change).

### Available tags

**Unicode styles** (accents are stripped before styling, e.g. `café` → `cafe`):

| Tag | Result | Example |
|---|---|---|
| `<b texte>` | Bold sans | 𝗍𝖾𝗑𝗍𝖾 |
| `<i texte>` | Italic sans | 𝘵𝘦𝘹𝘵𝘦 |
| `<bi texte>` | Bold italic sans | 𝙩𝙚𝙭𝙩𝙚 |
| `<script texte>` | Script / cursive | 𝓉𝑒𝓍𝓉𝑒 |
| `<double texte>` | Double-struck | 𝕥𝕖𝕩𝕥𝕖 |
| `<small texte>` | Superscript | ᵗᵉˣᵗᵉ |
| `<sub texte>` | Subscript (limited alphabet, unsupported letters fall back to normal) | |
| `<fullwidth texte>` (alias `<vapor texte>`) | Fullwidth | ｔｅｘｔｅ |
| `<mono texte>` | Monospace | |
| `<box texte>` | Squared | 🅃🄴🅇🅃🄴 |
| `<neg texte>` | Negative squared | |
| `<upside texte>` | Upside down | ǝʇxǝʇ |
| `<zalgo texte>` | Glitch (moderate intensity) | |
| `<spaced texte>` | Spaced out | t e x t e |

**Utility tags** (accents are kept):

| Tag | Result |
|---|---|
| `<date>` | Today's date (`dd/MM/yyyy`) |
| `<time>` | Current time (`HH:mm`) |
| `<clip>` | Current clipboard content |
| `<rand X-Y>` | Random integer between X and Y (inclusive) |
| `<upper texte>` | UPPERCASE |
| `<title texte>` | Title Case |

Tags can be nested one level, e.g. `<b <i texte>>` — the outermost tag
wins (overlapping two different Unicode styles on the same characters
isn't representable; see the limitation documented in
[`TextTransformer.kt`](app/src/main/java/helium314/keyboard/tags/TextTransformer.kt)).
An unclosed or unrecognized tag is left as raw text.

### Quick access

`<` and `>` are used constantly with this feature, so it's worth pinning
them to the toolbar: **Settings → Toolbar → Customize toolbar key codes**,
pick two keys you don't use, set their code to `60` (`<`) and `62` (`>`),
then pin them under **Settings → Toolbar → Pin toolbar keys** so they're
always visible above the keyboard.

### Implementation

- Core logic: [`app/src/main/java/helium314/keyboard/tags/TextTransformer.kt`](app/src/main/java/helium314/keyboard/tags/TextTransformer.kt)
  — pure Kotlin, no Android dependency, unit-testable in isolation.
- Tests: [`app/src/test/java/helium314/keyboard/tags/TextTransformerTest.kt`](app/src/test/java/helium314/keyboard/tags/TextTransformerTest.kt)
  (38 tests covering every tag, accents, nesting, unclosed/unknown tags,
  digits/punctuation, empty input).
- Integration point: `InputLogic.java`, `handleNonSpecialCharacterEvent` —
  intercepts the `>` code point, looks at the text before the cursor via
  `RichInputConnection`, and on a match, deletes the raw sequence and
  commits the transformed text.
- CI: [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml)
  builds a debug APK on every push and runs `TextTransformerTest`
  (the project's own pre-existing Robolectric-based tests are skipped in
  this workflow — they require Android SDK stub setup this generic CI
  doesn't provide, and are unrelated to this feature).


# Features
<ul>
  <li>Add dictionaries for suggestions and spell check</li>
  <ul>
    <li>build your own, or get them  <a href="https://codeberg.org/Helium314/aosp-dictionaries#dictionaries">here</a> (quality may vary)</li>
    <li>additional dictionaries for emojis or scientific symbols can be used to provide suggestions (similar to "emoji search")</li>
    <li>note that for Korean layouts, suggestions only work using <a href="https://github.com/openboard-team/openboard/commit/83fca9533c03b9fecc009fc632577226bbd6301f">this dictionary</a>, the tools in the dictionary repository are not able to create working dictionaries</li>
  </ul>
  <li>Customize keyboard themes (style, colors and background image)</li>
  <li>Emoji search (inline and separate, requires <a href="https://codeberg.org/Helium314/aosp-dictionaries">emoji dictionary</a>)</li>
  <ul>
    <li>can follow the system's day/night setting on Android 10+ (and on some versions of Android 9)</li>
    <li>can follow dynamic colors for Android 12+</li>
  </ul>
  <li>Customize keyboard <a href="https://github.com/HeliBorg/HeliBoard/blob/main/layouts.md">layouts</a> (only available when disabling <i>use system languages</i>)</li>
  <li>Customize special layouts, like symbols, number,  or functional key layout</li>
  <li>Multilingual typing</li>
  <li>Glide typing (<i>only with closed source library</i> ☹️)</li>
  <ul>
    <li>library not included in the app, as there is no compatible open source library available</li>
    <li>can be extracted from GApps packages ("<i>swypelibs</i>"), or downloaded <a href="https://github.com/erkserkserks/openboard/tree/46fdf2b550035ca69299ce312fa158e7ade36967/app/src/main/jniLibs">here</a> (click on the file and then "raw" or the tiny download button)</li>
  </ul>
  <li>Clipboard history</li>
  <li>One-handed mode</li>
  <li>Split keyboard</li>
  <li>Number pad</li>
  <li>Backup and restore your settings and learned word / history data</li>
</ul>

For [FAQ](https://github.com/HeliBorg/HeliBoard/wiki/FAQ), [hidden features](https://github.com/HeliBorg/HeliBoard/wiki/9.-Hidden-features) and more information about the app and features, please visit the [wiki](https://github.com/HeliBorg/HeliBoard/wiki)

# Contributing ❤

## Reporting Issues

Whether you encountered a bug, or want to see a new feature in HeliBoard, you can contribute to the project by opening a new issue [here](https://github.com/HeliBorg/HeliBoard/issues). Your help is always welcome!

Before opening a new issue, be sure to check the following:
 - **Does the issue already exist?** Make sure a similar issue has not been reported by browsing [existing issues](https://github.com/HeliBorg/HeliBoard/issues?q=). Please search open and closed issues. In case of feature requests you could also check the [FAQ](https://github.com/HeliBorg/HeliBoard/wiki/FAQ) and [hidden features](https://github.com/HeliBorg/HeliBoard/wiki/9.-Hidden-features).
 - **Is the issue still relevant?** Make sure your issue is not already fixed in the latest version of HeliBoard.
 - **Is it a single topic?** If you want to suggest multiple things, open multiple issues.
 - **Did you use the issue template?** It is important to make life of our kind contributors easier by avoiding issues that miss key information to their resolution.
 - **Is it written by a human?** Do not use LLMs or similar to generate issues. Having LLMs help with translation or similar is acceptable, but must be disclosed. See also [AI_USAGE.md](AI_USAGE.md)
Note that issues that that ignore part of the issue template will likely get treated with very low priority, as often they are needlessly hard to read or understand (e.g. huge screenshots, not providing a proper description, or addressing multiple topics). Blatant violation of the guidelines may result in the issue getting closed.

If you're interested, you can read the following useful text about effective bug reporting (a bit longer read): https://www.chiark.greenend.org.uk/~sgtatham/bugs.html

## Translations
Translations can be added using [Weblate](https://translate.codeberg.org/projects/heliboard/). You will need an account to update translations and add languages. Add the language you want to translate to in Languages -> Manage translated languages in the top menu bar.
Updating translations in a PR will not be accepted, as it may cause conflicts with Weblate translations.

Some notes on translations
* when translating metadata, translating the changelogs is rather useless. It's available as it was requested by translators.
* the `hidden_features_message` is horrible to translate with Weblate, and serves little benefit as it's just a copy of what's already in the wiki: https://github.com/HeliBorg/HeliBoard/wiki/9.-Hidden-features. It's been made available in the app on user request/contribution.

## To Community
There is the [discussions on GitHub](https://github.com/HeliBorg/HeliBoard/discussions), or if you prefer a more open network there is [Lemmy](https://lemmy.world/c/Heliboard).
You can share your themes, layouts and dictionaries with other people:
* Themes can be saved and loaded using the menu on top-right in the _adjust colors_ screen
  * you can share custom colors in a separate [discussion section](https://github.com/HeliBorg/HeliBoard/discussions/categories/custom-colors)
  * there are theme collections available at [Star-Trowa/heliboard-themes](https://github.com/Star-Trowa/heliboard-themes) and [PickleHik3/droid-tings](https://github.com/PickleHik3/droid-tings)
* Custom keyboard layouts are text files whose content you can edit, copy and share
  * this applies to main keyboard layouts and to special layouts adjustable in advanced settings
  * see [layouts.md](layouts.md) for details
  * you can share custom layouts in a separate [discussion section](https://github.com/HeliBorg/HeliBoard/discussions/categories/custom-layout)
  * [Roccobot's Layout Maker](https://roccobot.github.io/HeliBoard-RLM/) is a browser-based editor for json layout files
* Creating dictionaries is a little more work
  * first you will need a wordlist, as described [here](https://codeberg.org/Helium314/aosp-dictionaries/src/branch/main/wordlists/sample.combined) and in the repository readme
  * the you need to compile the dictionary using [external tools](https://github.com/remi0s/aosp-dictionary-tools)
  * the resulting file (and ideally the wordlist too) can be shared with other users
  * note that there will not be any further dictionaries added to this app, but you can add dictionaries to the [dictionaries repository](https://codeberg.org/Helium314/aosp-dictionaries)

## Code Contribution
See [Contribution Guidelines](CONTRIBUTING.md)

# Links
* Info
  * [Wiki](https://github.com/HeliBorg/HeliBoard/wiki), including FAQ, help on customizing layouts, and gesture data gathering
  * [Layout documentation](layouts.md) (more technical info regarding layout customization)
  * [For creating custom dictionaries](https://codeberg.org/Helium314/aosp-dictionaries#wordlist-information) (see also top of the linked readme)
* Community
  * [Lemmy](https://lemmy.world/c/Heliboard)
  * [Reddit](https://www.reddit.com/r/HeliBoard)
  * GitHub [discussions](https://github.com/HeliBorg/HeliBoard/discussions)
* Other
  * [Translations](https://translate.codeberg.org/projects/heliboard/)
  * [Dictionaries](https://codeberg.org/Helium314/aosp-dictionaries)
  * [k3lp](https://codeberg.org/k3lp/k3lp) is a WIP library for keyboard layout parsing that will be implemented in HeliBoard when ready (created by [FlorisBoard](https://github.com/florisboard/florisboard/) maintainers)
  * [swipe-o-scope](https://codeberg.org/eclexic/swipe-o-scope) for visualizing gesture data as created when using gesture data gathering

# License

HeliBoard (as a fork of OpenBoard) is licensed under GNU General Public License v3.0.

 > Permissions of this strong copyleft license are conditioned on making available complete source code of licensed works and modifications, which include larger works using a licensed work, under the same license. Copyright and license notices must be preserved. Contributors provide an express grant of patent rights.

See repo's [LICENSE](/LICENSE) file.

Since the app is based on Apache 2.0 licensed AOSP Keyboard, an [Apache 2.0](LICENSE-Apache-2.0) license file is provided.
The icon is licensed under [Creative Commons BY-SA 4.0](https://creativecommons.org/licenses/by-sa/4.0/). A [license file](LICENSE-CC-BY-SA-4.0) is also included.

# Credits
- Icon by [Fabian OvrWrt](https://github.com/FabianOvrWrt) with contributions from [The Eclectic Dyslexic](https://github.com/the-eclectic-dyslexic)
- [OpenBoard](https://github.com/openboard-team/openboard)
- [AOSP Keyboard](https://android.googlesource.com/platform/packages/inputmethods/LatinIME/)
- [LineageOS](https://review.lineageos.org/admin/repos/LineageOS/android_packages_inputmethods_LatinIME)
- [Simple Keyboard](https://github.com/rkkr/simple-keyboard)
- [Indic Keyboard](https://gitlab.com/indicproject/indic-keyboard)
- [FlorisBoard](https://github.com/florisboard/florisboard/)
- Our [contributors](https://github.com/HeliBorg/HeliBoard/graphs/contributors)

## Funding

This project is funded through [NGI Mobifree Fund](https://nlnet.nl/mobifree), a fund established by [NLnet](https://nlnet.nl) with financial support from the European Commission's [Next Generation Internet](https://ngi.eu) program. Learn more at the [NLnet project page](https://nlnet.nl/project/GestureTyping).

[<img src="https://nlnet.nl/logo/banner.png" alt="NLnet foundation logo" width="20%" />](https://nlnet.nl)

Further the project benefits from donations provided by many users (thank you all!).
