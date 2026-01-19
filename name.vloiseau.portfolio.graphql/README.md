Commandes importantes

Compilation:
mvn -f portfolio-app/pom.xml clean verify


mvn -f portfolio-app/pom.xml clean verify  -DskipTests -Dtycho.test.skip=true -Dtycho.os=macosx -Dtycho.ws=cocoa -Dtycho.arch=aarch64


Lancement:
portfolio-product/target/products/name.abuchen.portfolio.product/macosx/cocoa/aarch64/PortfolioPerformance.app/Contents/MacOS/PortfolioPerformance
Lancement en mode debug:
portfolio-product/target/products/name.abuchen.portfolio.product/macosx/cocoa/aarch64/PortfolioPerformance.app/Contents/MacOS/PortfolioPerformance -consoleLog -vmargs "-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005



La génération des ressources maven permet de générer la version compilée du site web statique React

mvn -f portfolio-app/pom.xml install 
mvn -f portfolio-app/pom.xml generate-resources



mvn -f portfolio-app/pom.xml -DskipTests -pl :portfolio-target-definition -am install
