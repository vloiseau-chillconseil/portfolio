# Repository Guidelines

## Project Structure & Module Organization
- `name.abuchen.portfolio/` holds the core domain and business logic (models, calculations, importers).
- `name.abuchen.portfolio.ui/` contains the Eclipse RCP UI layer and resources.
- `name.abuchen.portfolio.tests/` and `name.abuchen.portfolio.ui.tests/` provide JUnit plug-in tests.
- `portfolio-app/` hosts the Maven/Tycho build entry point (`portfolio-app/pom.xml`).
- `portfolio-product/` packages the application; `portfolio-target-definition/` defines the target platform.

## Build, Test, and Development Commands
- `mvn -f portfolio-app/pom.xml clean verify -Plocal-dev` runs the Tycho build.
- `mvn -f portfolio-app/pom.xml clean compile -Plocal-dev -pl :portfolio-target-definition,:name.abuchen.portfolio.pdfbox1,:name.abuchen.portfolio.pdfbox3,:name.abuchen.portfolio -am -amd` builds core bundles only.
- `mvn -f portfolio-app/pom.xml verify -Plocal-dev -o -pl :portfolio-target-definition,:name.abuchen.portfolio.pdfbox1,:name.abuchen.portfolio.pdfbox3,:name.abuchen.portfolio,:name.abuchen.portfolio.junit,:name.abuchen.portfolio.tests -am -amd` runs core tests.
- `mvn -f portfolio-app/pom.xml verify -Plocal-dev -o ... -Dtest=<fully.qualified.TestClass>` runs a single test.
- `mvn -f portfolio-app/pom.xml verify -Plocal-dev -pl :portfolio-target-definition,:name.abuchen.portfolio.pdfbox1,:name.abuchen.portfolio.pdfbox3,:name.abuchen.portfolio,:name.abuchen.portfolio.ui,:name.abuchen.portfolio.junit,:name.abuchen.portfolio.ui.tests -am -amd` runs UI tests.
- For runs, use Eclipse RCP launch configs (`PortfolioPerformance`, `PortfolioPerformance_Tests`).

## Local Configuration
- `JAVA_HOME` must point to a Java 21 JDK.
- Use `MAVEN_OPTS="-Xmx4g"` for builds.
- The `-Plocal-dev` profile speeds builds by skipping coverage, checkstyle, and Babel translations.

## Coding Style & Naming Conventions
- Java 21 is required; prefer `var` for local variables where it improves readability.
- Use the Eclipse formatter and organize imports automatically; avoid reformatting unrelated code.
- **PDF importers**: do not auto-format; use `@formatter:off` / `@formatter:on` around manual formatting.
- Do not generate `$NON-NLS-1$` comments (I18N warnings are suppressed globally).
- PDF importer naming: `BankNamePDFExtractor.java` with tests `BankNamePDFExtractorTest.java` and fixtures like `Buy01.txt`.

## Testing Guidelines
- JUnit plug-in tests live in `name.abuchen.portfolio.tests/`; UI tests are not required by default.
- Add tests for new calculation/business logic; use custom matchers for PDF importer assertions.
- Keep test data anonymized while preserving layout (especially PDF text fixtures).

## Architecture Overview
- Eclipse RCP/E4 app with OSGi bundles; core domain logic is UI-free and shared by all features.
- Packages: `model/` (entities), `datatransfer/` (CSV/PDF/IBFlex), `money/` (currencies), `snapshot/` (performance), `online/` (quote feeds), `math/`, `util/`.
- UI structure: `views/`, `dialogs/`, `handlers/`, `editor/`, `wizards/` inside `name.abuchen.portfolio.ui/`.
- Key entry points: `Client.java` (aggregate root), `ClientFactory.java` (persistence), `AbstractPDFExtractor.java` (PDF imports), `PortfolioPart.java` (main editor).

## Commit & Pull Request Guidelines
- Commit messages: short summary (<= 50 chars), optional body wrapped at 72 chars, and links like `Closes #123` or `Issue: https://...`.
- Rebase on `master` before opening a PR; do not merge `master` into your branch.
- Keep PRs focused and small; open a draft PR early for large changes.

## React UI
- React UI est dans le répertoire portfolio-react-ui, tu peux lire le fichier AGENTS.md dans ce répertoire.