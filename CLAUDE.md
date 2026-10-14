# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project is

A Selenium + Apache POI based UI test automation framework for a DYL/Unilever distributor-management web application ("ngui"). It drives the app through the browser per-country (Pakistan/PK, Bangladesh/BD, Philippines/PH, Thailand/TH), uploads/downloads Excel files against real screens, and validates the results. Test scenarios are **not written as Java test methods** — they are declarative rows in per-country config Excel workbooks (`configPK.xlsx`, `configBD.xlsx`, etc.) that a driver loop interprets at runtime. A second, unrelated Swing desktop tool (`ReleaseValidator`) compares microservice versions across environments.

There is no test framework (JUnit/TestNG) wired up — this is an executable automation runner, not a `mvn test` suite.

## Build & run

- Build: `mvn clean package` (produces a shaded/executable jar via `maven-shade-plugin`; main class is `org.example.api.ReleaseValidator` per `pom.xml`, but the UI automation entry point below is run directly, not via the packaged jar).
- Run the Excel/UI automation driver: run `org.example.ui.Main` (e.g. `mvn compile exec:java -Dexec.mainClass=org.example.ui.Main`, or run it from the IDE). It must be run with the **repo root as the working directory** — it reads `application.properties` and `users.json` from `new File("application.properties")` (relative path), not the classpath.
- Run the release/version comparator Swing tool: run `org.example.api.ReleaseValidator` (reads `environments.properties` from the working directory).
- No test suite exists (`src/test` is absent); there is nothing to run via `mvn test`.
- Java 17, Maven, Windows-oriented (Chrome via WebDriverManager/Selenium 4).

## Runtime configuration files (repo root, not resources)

- `application.properties` — driver/runtime settings: `selenium.headless`, `downloadedExcels` (download dir), `sharedPath` (UNC path for shared reports), `zoom`, `ReportsFolderName`.
- `users.json` — list of test users/environments to execute against (username, password, url, `resourcesFolder`, `configPath`, `country`, `execute: yes|no`). `Main` iterates this list and runs a full session per entry. Contains real dev-environment credentials — treat as sensitive, don't echo contents into chat/logs unnecessarily.
- `environments.properties` — used by `ReleaseValidator` to define environments/base URLs for the version comparator.

## The config-Excel execution model (core architecture)

Everything under `Resources<COUNTRY>/` (`ResourcesPK`, `ResourcesBD`, `ResourcesPH`, `ResourcesTH`) is per-country test data. Each country has a `config<COUNTRY>.xlsx` workbook that `ExcelLoader.loadScreens()` parses into a `List<Map<String,Object>>` of "screen" definitions, reading these sheets:

- **Screens** — one row per test/screen: `testID`, `screenName`, `screenId`, `mode`, `templatePath`, `validateMode`, `execute` (y/blank = run, anything else = skip).
- **Params** — extra key/value params keyed by `testID`, merged into each screen's `params` map (used to fill download filter fields).
- **Rules** — column-generation rules keyed by `testID` + column name (`type`, `length`, `prefix`) — drives `ExcelGen.generateExcel()` to mutate a template/downloaded Excel before re-upload (e.g. generate unique codes).
- **Validations** — per-`testID` post-upload validation config (`screenName`, `columnId`, `validateCols`, etc.), consumed by `PostUploadValidator`.
- **ScenarioData** — free-form key/value pairs per `testID` used by multi-screen flows (PJP, LOCUS_UP, etc.) to look up related screens/IDs.

`Main.main()` loads `users.json`, and for each user with `execute != no`: creates a Chrome `WebDriver` (`DriverFactory`), logs in (`loginPage`), loads that user's `configPath` workbook via `ExcelLoader`, and for each screen row with `execute` not explicitly "n", navigates the app's search box to the screen and dispatches to `ModeExecutionHandler.executeMode(...)` based on the row's `mode`.

### `mode` values (switch in `ModeExecutionHandler`)

- `DOWNLOAD_UPLOAD` — upload the template as-is, then trigger a download, then validate.
- `DOWNLOAD_UPDATE_UPLOAD` — generate a fresh Excel from the template using `rules` (`ExcelGen`), upload it, download, validate.
- `DOWNLOAD_ONLY` / `UPLOAD_ONLY` — single-direction file operation + validation.
- `DOWNLOAD_MODIFY_UPLOAD` — download the app's current export, mutate it via `ExcelGen` using `rules`, re-upload, validate.
- `PEP` — special-cased Product Exclusion Policy flow (extra click before download/upload).
- `PJP` — multi-screen flow (DSR / Header / Detail screens resolved via `scenarioData` test IDs and `basePage.findScreenByTestId`) delegated to `PJPExcelUpload.run(...)`.
- `LOCUS_UP` — multi-screen bulk-order + delivery-date + locus-upload + transaction-inquiry flow, delegated to `LocusUploadFlow.run(...)`.
- `STOCK_RECON` — download the latest export, hand the file to `PhysicalStockReconciliationFlow.run(...)`.
- `PARTIAL_RETURN_BUDGET` — delegated to `PartialReturnBudgetFlow.run(...)`.

Each mode's outcome becomes a `ValidationResult`, appended via `TestSummary.appendValidation(...)`. Cross-screen `testDataCleanup` (silent upload of a `testDataExcel` from `scenarioData`) runs after upload-driven modes.

### `validateMode` values (switch in `PostUploadValidator.run`)

Read from the Screens/Validations sheet per `testID`: `DOWNLOAD_COMPARE` (re-download and diff generated vs. downloaded values via `DownloadComparator`), `UI_SEARCH` (navigate to a grid and search for the generated value), `COMPARE`, `LMT`, and others — blank means skip validation entirely.

## Package layout

- `org.example.ui` — `Main` (driver loop) plus `org.example.ui.pages` (Selenium page objects, one per app screen, e.g. `OrderBookingPage`, `SANPage`, `loginPage`, all extending `basePage`) and `org.example.ui.utilities` (everything else: Excel I/O, validation, flow orchestrators, driver/session plumbing).
- `org.example.api` — standalone `ReleaseValidator` (Swing GUI) and `ApiFieldValidator`; unrelated to the Selenium flow, uses REST-assured against `environments.properties`-defined environments to diff service versions across two environments.
- `basePage` (`org.example.ui.pages.basePage`) — shared Selenium helpers (`find`, `click` with retry via `Event.robustClick`, `type` with stale-element retry, loader waits) that every page object extends; also hosts `findScreenByTestId`, used by multi-screen flows to look up a screen row by testID across the loaded config.
- Key utilities: `DriverFactory` (Chrome setup/headless/download dir), `ExcelLoader`/`ExcelGen`/`ExcelUtils`/`ExcelValidator` (config parsing, data generation, comparison), `FileManager` (upload/download orchestration + toast handling via `ToastHandles`), `PostUploadValidator` + `DownloadComparator`/`ExactExcelComparator`/`UploadedVsDownloadedComparator` (post-action validation), `TestSummary` (accumulates results into an HTML report under `Reports/`, keyed by counts of download/upload/validation pass-fail), `ScreenshotService`, `LoggerUtil` (log4j2 wrapper), `GeneratedDataStore` (shares generated test values across screens within a run).
- `PJPExcelUpload`, `LocusUploadFlow`, `PhysicalStockReconciliationFlow`, `PartialReturnBudgetFlow` — self-contained multi-step flow orchestrators invoked for their respective `mode`s; each returns a `ValidationResult` and takes the already-resolved screen maps/`WebDriver`/`downloadDir` rather than re-parsing the config.

## Working with this codebase

- There are no automated tests to validate changes — verification is either a real Selenium run against a dev environment (slow, needs valid `users.json` credentials and network access to `*.unilever.com`), or careful manual reading of the Excel-driven control flow.
- `DownloadedExcels/`, `Screenshots/`, `Reports/`, `logs/`, and `target/` are run artifacts — don't treat files there as source of truth for expected behavior. Downloads and screenshots are written to `<folder>/<Country>/<run timestamp>/` (one subfolder per country per run, never wiped mid-run), and `RetentionCleaner` deletes run folders older than `retentionDays` (set in `application.properties`, default 3) at the start of each run. `Reports/` files are timestamped per run and are not deleted by the app.
- Excel workbook structure (sheet names, column order in `Screens`/`Params`/`Rules`/`Validations`/`ScenarioData`) is a positional/name contract consumed by `ExcelLoader` — changing a config workbook's headers or column order will silently break parsing (cells are read by fixed index in several sheets, e.g. `Params`/`Rules`/`ScenarioData` use `row.getCell(0/1/2/3...)` directly).
- When adding a new `mode` or `validateMode`, follow the existing pattern: add the branch in `ModeExecutionHandler`/`PostUploadValidator`, and if it's multi-screen, add a new orchestrator class mirroring `PJPExcelUpload`/`LocusUploadFlow` rather than inlining logic into `Main` or the handler.

## Git workflow

- **Always ask the user before committing or pushing.** Never run `git commit` or `git push` on your own initiative, even when a task looks finished. Make the code changes, then ask whether to commit; ask again before any push. An earlier approval for one commit or push does not carry over to later ones.
- When asked to commit, break the work into small commits grouped by purpose, with a descriptive message for each.
- Never commit `users.json` (real credentials), run artifacts (`Reports/`, `DownloadedExcels/`, `Screenshots/`, `logs/`, `target/`), `~$` Excel lock files, or template workbooks that runs rewrite in place.
