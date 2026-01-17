Commandes importantes

Compilation:
mvn -f portfolio-app/pom.xml clean verify -Plocal-dev


mvn -f portfolio-app/pom.xml clean verify  -DskipTests -Dtycho.test.skip=true -Dtycho.os=macosx -Dtycho.ws=cocoa -Dtycho.arch=aarch64


Lancement:
portfolio-product/target/products/name.abuchen.portfolio.product/macosx/cocoa/aarch64/PortfolioPerformance.app/Contents/MacOS/PortfolioPerformance
Lancement en mode debug:
portfolio-product/target/products/name.abuchen.portfolio.product/macosx/cocoa/aarch64/PortfolioPerformance.app/Contents/MacOS/PortfolioPerformance -consoleLog -vmargs "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005

