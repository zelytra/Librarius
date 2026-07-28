# Documentation de travail — Librarius

Documentation destinée aux **agents** et contributeurs qui reprennent le projet.
Elle complète la doc publique (`docs/ARCHITECTURE.md`, `docs/DEPLOYMENT.md`) en
décrivant *l'état réel du code*, les règles de travail et le périmètre à construire.

## Ordre de lecture recommandé

| # | Document | À lire quand |
|---|---|---|
| 1 | [ÉTAT-DES-LIEUX](ETAT-DES-LIEUX.md) | **Toujours en premier.** Ce qui existe vraiment, ce qui manque, la dette identifiée |
| 2 | [CONVENTIONS](CONVENTIONS.md) | Avant d'écrire la moindre ligne : git flow, commits, PR, style de code, tests |
| 3 | [PRODUIT](PRODUIT.md) | Travail fonctionnel : personas, écrans, parcours, règles métier |
| 4 | [ARCHITECTURE](ARCHITECTURE.md) | Travail technique : modules, flux, dépendances, décisions structurantes |
| 5 | [MODÈLE-DE-DONNÉES](MODELE-DE-DONNEES.md) | Toute évolution de schéma ou d'entité |
| 6 | [API](API.md) | Ajout/modification d'endpoint, contrat front↔back |
| 7 | [ROADMAP](ROADMAP.md) | Choix de la prochaine tâche : milestones, priorités, dépendances |

## Règle de mise à jour

Ces documents sont **vivants**. Une PR qui change le comportement, le schéma ou le
contrat d'API met à jour le document concerné **dans la même PR**. Une PR qui ferme
une issue coche la ligne correspondante dans [ROADMAP](ROADMAP.md).

Si un document contredit le code, **le code fait foi** : corrige le document et
signale l'écart dans la description de la PR.
