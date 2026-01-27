# Information

This project is a POC to integrate a web UI provided by the Portfolio Performance application.
The basic idea is:
- A Java GraphQL (SPQR) server exposes PP data and actions (queries and mutations).
- A React web app consumes this data to build a web UI.
- Capacitor generates native iOS/Android apps from this React web app.

The web server can be enabled via preferences.

The goal is to let me build my own app connected in real time to PP running on my machine (or any machine) from my phone.

For security reasons, you must NOT expose the ports to the public internet. This topic still needs to be handled.
Personally, I access PP through a VPN.


# Technical

The `name.vloiseau.portfolio.graphql` directory is an Eclipse Java bundle that contains the GraphQL API and can serve the React static site.
The `portfolio-react-ui` directory contains the React site source code.

## How it works
- React development is made easier with Vite (hot reloading in the browser without recompilation, restart, or page reload).
- NPM tools build the site as a static deliverable.
- To simplify the build: Maven builds the React code and places it in `name.vloiseau.portfolio.graphql/web` so it is packaged as Java static resources and served as such.
- NPM is automatically downloaded and run by the Maven plugin, so the built site is not committed to the source repository.


# Branch
The development branch `feature_graphql_react_ui` is rebased on the stable tag `0.82.2` so I can use my code on top of the stable PP codebase.


# Build

Be careful to update the version number in these files after rebasing:
- `name.vloiseau.portfolio.graphql/pom.xml`
- `name.vloiseau.portfolio.graphql/META-INF/MANIFEST.MF`


## auto:
build-vloiseau.sh : build l'application avec un nouveau numéro de version

## manuel
```sh
mvn -f portfolio-app/pom.xml clean verify
```

```sh
rm -rf /Applications/PortfolioPerformance.app/
cp -R portfolio-product/target/products/name.abuchen.portfolio.product/macosx/cocoa/aarch64/PortfolioPerformance.app /Applications/PortfolioPerformance.app
```

# Application log:
`less "/Users/vloiseau/Library/Application Support/name.abuchen.portfolio.product/workspace/.metadata/.log"`
