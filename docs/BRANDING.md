# Branding: iCall by ImadLabs

* App name: **iCall**. Brand: **ImadLabs** (always one word, capital I and L). Telegram channel: https://t.me/ImadLabs.
* Source of truth for brand assets and rules: https://github.com/ImadKhan1999/Graphics (BRAND_GUIDELINES.md,
  DESIGN_SYSTEM.md, CLAUDE.md, brand/colors/tokens.json). Read its CLAUDE.md before changing visuals.

## Assets in this repo

| File | What |
|---|---|
| `branding/icall/icall-master-icon.svg` | iCall master icon (1024², follows the Graphics repo app template: HexaCore chassis, handset in the quantum aperture, Cyber Mint "L" pathway). |
| `branding/icall/icall-1024x1024.png`, `icall-512x512.png` | Rendered from the SVG with cairosvg (512 = Play Store icon). |
| `branding/imadlabs-symbol.svg` | Copy of Graphics `brand/logos/imadlabs-logo-standalone-symbol.svg`. |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | Adaptive icon foreground (vector port of the master SVG; group translate 24,19.2 scale 0.3 keeps it inside the 66 dp safe zone). Background colour `@color/launcher_bg` = #070B14. |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | Android 13+ themed icon (outline, handset, L). |
| `app/src/main/res/drawable/icall_mark.xml` | Same icon without the background, used in About and the welcome popup. |
| `app/src/main/res/drawable/imadlabs_symbol.xml` | ImadLabs HexaCore symbol (from Graphics apps/template), used next to the wordmark. |

To render the SVG again: `pip install cairosvg` then
`python3 -c "import cairosvg; cairosvg.svg2png(url='branding/icall/icall-master-icon.svg', write_to='out.png', output_width=1024, output_height=1024)"`.
The icon could also be added to the Graphics repo as `apps/icall/` (not done: this session has read-only access there).

## In-app branding

* Colours: `ui/theme/Theme.kt` (tokens from Graphics `brand/colors/tokens.json`). Dark theme is the default;
  Settings › Appearance › Theme switches Dark / Light / System (`Prefs.themeMode`).
* About screen (`ui/screens/AboutScreen.kt`, Settings › About iCall): icon, version, "by ImadLabs" wordmark
  ("Labs" in Cyber Mint), **Join t.me/ImadLabs** button, Linphone version.
* Settings › About also has a direct "ImadLabs on Telegram" link.
* Welcome popup (`ui/WelcomeDialog.kt`): shown at app start until the user ticks **Don't show this again**
  (`Prefs.hideWelcome`). Buttons: Join Telegram / Close.
