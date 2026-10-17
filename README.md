# WasteWise Desktop 

A native JavaFX + CSS implementation of the supplied WasteWise HTML design. Uses real Java classes and an in-memory `ArrayList<Waste>`. No WebView, JavaScript, database, or network connection is used at runtime.

## Start on Windows

1. Extract the entire ZIP to a folder. Keep `lib`, `build`, and `src` together.
2. Install **JDK 21 or later** if needed. The project was tested on the installed JDK 26.0.1 with JavaFX 21.0.6, compiling for Java 21. JavaFX's Windows x64 libraries and the fonts are included.
3. Double-click **Launch WasteWise.vbs** for an empty tracker, or **Demo WasteWise.vbs** for the five sample records from the HTML reference.
4. Add records with **Add Waste Record**. Exit using the top-right close button or **Exit Java Program**.

These launchers open the application without a console window. If Windows Script Host is disabled, use PowerShell:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1
# Or load the reference's five sample records:
powershell -NoProfile -ExecutionPolicy Bypass -File .\run.ps1 -Demo
```

If a JDK is installed outside the usual Java / Eclipse Adoptium folders, set `JAVA_HOME` to its installation folder. All data disappears when the application closes. Demo records are only loaded with the demo launcher.

## Open in NetBeans

Use **File → Open Project** and select this folder (the one containing `pom.xml`). Choose JDK 21 or newer in the project settings. The included `nbactions.xml` maps Run to `compile javafx:run`.

The standard Maven build downloads its own dependencies on first use and therefore initially needs internet. The included PowerShell build uses `lib/` and works offline:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -Test
powershell -NoProfile -ExecutionPolicy Bypass -File .\build.ps1 -UiTest
```

Alternatively, with Maven installed: `mvn clean compile javafx:run`.

## Features

### Combined interface update

- **Overview Dashboard** retains the green summary panel and category doughnut. Each category now also shows its record count, and the latest five entries appear in a recent-records panel.
- **Segregation Logs** is a separate screen with four compact summary cards, a dedicated search/filter toolbar, and an expanding records table.
- The footer shows **Visible records weight**, which follows your search and category filter. The overview and summary cards always show the whole session.
- Navigation preserves search and filters. **Clear filters** resets both. Sidebar category shortcuts open the records screen for that category; **View all records** opens an unfiltered list.
- Weight sorting uses numeric model values. Undo restores the deleted record even when the table is sorted or filtered.
- A consistent light workspace, dark green sidebar, and brighter emerald actions combine the two reference designs. Branding now consistently uses WasteWise.

### Launching after an update

The build creates timestamped JAR files, and the launcher selects the newest one. This lets a new version be built while an older window is open without interrupting that window's in-memory records. Close the old window when you no longer need its session, then use either launcher to open the new design. Closing still discards records.

- Green desktop shell, sidebar, dashboard cards, category doughnut, records table, search and category pills.
- Add form with name, category, weight in kg, and collection location.
- Positive, finite weight validation; blank and excessively long text rejected.
- Overall and category totals always cover the full session; filters change the table only.
- Removal uses stable record IDs even after sorting or filtering. Undo restores the most recent deletion to its original list position. Another deletion replaces that undo target.
- Sort table columns by clicking their headings.
- Empty states, notifications, exit confirmation, real JVM heap usage.
- Drag the title bar to move the window. Use the top-right minimize and maximize/restore controls; the lower-right grip resizes it. Minimum size: 1120 × 640 on a screen large enough to fit it.
- Ctrl+K: search; Ctrl+N: add; Ctrl+Z: undo last deletion; Escape: dismiss dialog; Delete: remove selected table record.

## OOP structure

| File | Responsibility |
|---|---|
| `model/Waste.java` | Abstract class; private ID, name, weight and location; validated accessors |
| `model/BiodegradableWaste.java` | Inherits Waste; overrides category |
| `model/RecyclableWaste.java` | Inherits Waste; overrides category |
| `model/ResidualWaste.java` | Inherits Waste; overrides category |
| `model/Category.java` | The three supported categories |
| `model/WasteTracker.java` | Encapsulates ArrayList; add, view, total, filter, remove, undo |
| `WasteWiseApp.java` | JavaFX application, controls, event handlers and UI smoke checks |
| `src/main/resources/com/group6/wastewise/theme.css` | JavaFX CSS styling |

Model source lives in `src/main/java/com/group6/wastewise/`. Subclass `getCategory()` calls through the shared `Waste` type demonstrate polymorphism. The tracker contains no JavaFX dependencies, so its logic can be tested separately.

## Design and behavior notes

The supplied HTML is the design specification. This is a native recreation, not an embedded browser rendering. Inter and Geist Mono fonts, the green palette, sidebar, cards, table, dialogs and main interactions have been carried over. JavaFX text/control rendering and some effects differ from browser rendering; a pixel-identical match is not claimed.

Intentional functional changes: the runtime label says JavaFX, the memory meter reads the real JVM, window controls operate the window, exiting actually closes the process, and a persistent Undo button remains available after a toast disappears. Ordinary launches start empty; demo launches reproduce the reference's samples. Font files and dependencies are local so the app works offline.

The proposal originally mentions Swing or console. Its Tools and Technologies section should now say **JavaFX + CSS** to reflect the chosen implementation.

## Verification

`build.ps1 -Test` runs 35 model checks covering totals, categories, validation, searches, ID-based removal, undo, clearing and sample data. `-UiTest` additionally checks both screens, retained filters, visible/global totals, clearing filters, numeric sorting, sorted deletion/undo, the real JavaFX form, invalid input, adding, empty states, exit cancellation and window resizing. Twelve screenshots are generated under `build/qa/` for visual review. Smoke mode uses its own temporary in-memory session.

The packaged JAR was compiled and run locally. NetBeans itself has not been launched for verification. JavaFX 21 on JDK 26 emits a dependency deprecation warning about `sun.misc.Unsafe`; it did not prevent the tested application from running.

## Licenses

Third-party notices are in `licenses/`. JavaFX: GPL v2 with Classpath Exception; Inter and Geist Mono: SIL Open Font License. JavaFX source: https://github.com/openjdk/jfx21u . The JDK itself is not bundled.
# Waste-Wise-Eco-Tracker

## Readability and navigation polish

Text and table rows use larger type with darker secondary text. The overview heading is now Collection Summary. The title bar contains only the application name, Quick Search and About; JavaFX, ArrayList, Java version and live JVM memory information are in About. Category cards on both screens are keyboard-focusable buttons: select one to open that category and clear stale search text. The Total Collection card shows all records. The rounded application border remains, without the outer frame.


## Window controls

The application starts windowed and adapts its initial size to the display. Top-right controls provide minimize, maximize/restore, full screen, and close. Double-click a non-button area of the title bar to maximize/restore. Press F11 to toggle full screen, or Escape to leave it. Maximize uses the monitor work area so the taskbar remains available. Drag the lower-right grip to resize a normal window.

## EcoWise design update

The EcoWise desktop HTML now guides the dark emerald sidebar, title bar, Segregation Logs, forms, confirmations, About panel, and notifications. The existing light Overview Dashboard retains its Collection Summary, category cards, donut chart, and recent activity. WasteWise branding and real window controls remain.

Records include global category percentages and a live weight-distribution bar, independent of table filtering. The add form shows examples for the selected category. Records scroll on smaller windows. Existing validation, sorting, delete/undo, keyboard shortcuts, and session-only storage remain functional. The HTML's placeholder analytics navigation and simulated runtime information are not included.

Verification also checks global distribution percentages, zero-weight resets, dynamic category examples, and minimize/maximize/restore/full-screen controls.

## Interface consistency fixes

Aligned page heading sizes and spacing while retaining the light overview and dark records workspace. Recyclable colors now match across charts, badges, and labels. Records cards have stable heights, the distribution legend is compact, and the table footer fits at 1120 x 740; shorter windows retain scrolling. Long table values have tooltips, and sort indicators and delete controls have clearer contrast.

The add dialog only reserves space for errors when needed, uses full-width category guidance, and focuses the first input. Quick Search returns to the search area. Dialog close buttons have accessible names. Exit messaging distinguishes an empty session from records that will be lost. Added native UI checks for compact footer visibility, scrolling at 1120 x 640, and validation dialog bounds.
