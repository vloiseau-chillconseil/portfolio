# Repository Guidelines

## Project Structure & Module Organization
- `name.abuchen.portfolio/` contains core domain logic (models, calculations, importers) and is UI-free.
- `name.abuchen.portfolio.ui/` holds the Eclipse RCP UI layer (views, dialogs, editors, handlers).
- `name.abuchen.portfolio.tests/` and `name.abuchen.portfolio.ui.tests/` provide JUnit plug-in tests.
- `portfolio-app/` is the Maven/Tycho build entry point with `portfolio-app/pom.xml`.
- `portfolio-product/` packages the application; `portfolio-target-definition/` defines the target platform.


## Build, Test, and Development Commands
- `mvn -f portfolio-app/pom.xml clean verify -Plocal-dev` runs the full Tycho build.
- `mvn -f portfolio-app/pom.xml clean compile -Plocal-dev -pl :portfolio-target-definition,:name.abuchen.portfolio.pdfbox1,:name.abuchen.portfolio.pdfbox3,:name.abuchen.portfolio -am -amd` builds core bundles only.
- `mvn -f portfolio-app/pom.xml verify -Plocal-dev -o -pl :portfolio-target-definition,:name.abuchen.portfolio.pdfbox1,:name.abuchen.portfolio.pdfbox3,:name.abuchen.portfolio,:name.abuchen.portfolio.junit,:name.abuchen.portfolio.tests -am -amd` runs core tests.
- `mvn -f portfolio-app/pom.xml verify -Plocal-dev -o -Dtest=<fully.qualified.TestClass>` runs a single test.
- Use Eclipse RCP launch configs (`PortfolioPerformance`, `PortfolioPerformance_Tests`) for local runs.

## Coding Style & Naming Conventions
- Java 21 is required; prefer `var` for local variables when it improves readability.
- Use the Eclipse formatter and organize imports; avoid reformatting unrelated code.
- PDF importers: do not auto-format; wrap manual alignment with `@formatter:off` / `@formatter:on`.
- PDF importer naming: `BankNamePDFExtractor.java`, tests `BankNamePDFExtractorTest.java`, fixtures like `Buy01.txt`.
- Do not add `$NON-NLS-1$` comments (I18N warnings are suppressed).

## Testing Guidelines
- JUnit plug-in tests live in `name.abuchen.portfolio.tests/`; UI tests are optional by default.
- Add tests for new calculation/business logic; use custom matchers for PDF importer assertions.
- Keep test data anonymized while preserving layout in fixtures.

## Commit & Pull Request Guidelines
- Commit messages: short summary (<= 50 chars), optional body wrapped at 72 chars, and links like `Closes #123` or `Issue: https://...`.
- Rebase on `master` before opening a PR; do not merge `master` into your branch.
- Keep PRs focused and small; open a draft PR early for large changes.

## Local Configuration
- Set `JAVA_HOME` to a Java 21 JDK; use `MAVEN_OPTS="-Xmx4g"` for builds.
- The `-Plocal-dev` profile skips coverage, checkstyle, and Babel translations to speed builds.
