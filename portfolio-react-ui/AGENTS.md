# Technique

Ce projet est un projet React/antd qui est une interface graphique pour des services GraphQL.
Le projet est déployé soit dans un navigateur soit sous la forme d'une application capacitor (URL: capacitor://).
Le serveur GraphQL authorise les appels CORS sur capacitor://

## Capacitor
On utilise la version 8 de capacitor.

# Fonctionnel

C'est une interface graphique permettant d'afficher des détails sur un ensemble de portefeuilles financiers.

## UI Layout Modes
- The app defines two layout modes based on viewport width.
- Mobile mode uses a 375px threshold (`window.innerWidth <= 375`).
- Classes applied: `body.app-mode-mobile` / `body.app-mode-desktop` and `app-layout--mobile` / `app-layout--desktop` on the root layout.
