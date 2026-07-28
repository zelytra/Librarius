# Spécification fonctionnelle — Librarius

> Ce document décrit **le produit visé**. Ce qui est déjà livré est signalé ✅,
> ce qui reste à construire 🔜. L'état factuel du code est dans
> [ÉTAT-DES-LIEUX](ETAT-DES-LIEUX.md).

## 1. Proposition de valeur

Librarius est une **bibliothèque personnelle** pour lecteurs de **romans et de mangas**.
Elle répond à trois besoins que les outils existants traitent mal ensemble :

1. **Savoir ce que je possède** — y compris quel *tome* et quelle *édition*, pour ne pas
   racheter en double en librairie.
2. **Savoir où j'en suis** — lectures en cours, progression, historique, objectifs.
3. **Savoir ce qui arrive** — prochaines sorties des séries suivies, liste de souhaits
   priorisée avec budget estimé.

Le différenciateur assumé : le traitement **de la série et du tome** (mangas), là où les
concurrents raisonnent au livre unitaire.

## 2. Personas

| Persona | Profil | Besoin dominant |
|---|---|---|
| **Léa — la collectionneuse manga** | 400 tomes, 25 séries en cours | Ne pas racheter un tome ; savoir quel tome manque ; connaître les dates de sortie VF |
| **Marc — le lecteur de romans** | 120 romans, 2 lectures en cours | Suivre sa progression, tenir un objectif annuel, retrouver ce qu'il a aimé |
| **Sarah — la nouvelle arrivante** | Vient de Booknode/Babelio | Importer sa bibliothèque existante sans ressaisie |

Cible v1.0 : **produit public** multi-utilisateurs, inscription ouverte, français d'abord.

## 3. Objets métier

| Objet | Définition | Porté par |
|---|---|---|
| **Œuvre** (`work`) | Le contenu intellectuel : un roman, ou *un tome* de manga | Catalogue partagé |
| **Édition** (`edition`) | Une matérialisation : ISBN, éditeur, langue, pagination, couverture, format | Catalogue partagé |
| **Série** (`series`) 🔜 | Regroupement ordonné d'œuvres (« One Piece », « Le Trône de fer ») | Catalogue partagé |
| **Titre possédé** (`library_item`) | Le lien utilisateur ↔ édition : statut, note, date d'acquisition, rang | Utilisateur |
| **Progression** (`reading_progress`) | Page courante / pourcentage, dates de début et de fin | Utilisateur |
| **Souhait** (`wishlist_item`) | Priorité, prix estimé, note | Utilisateur |
| **Rang** (`rank_category`) | Or / Argent / Bronze + catégories personnalisées | Partagé (built-ins) + utilisateur |
| **Objectif** (`reading_goal`) | Cible annuelle en livres, tomes ou pages | Utilisateur |

**Règle structurante** : le catalogue est **partagé** entre tous les utilisateurs, la
possession est **privée**. Deux utilisateurs qui possèdent le même tome pointent sur la
même `edition` mais ont deux `library_item` distincts.

## 4. Écrans

### 4.1 Accueil ✅ / 🔜

Tableau de bord personnel.

| Section | État | Détail |
|---|---|---|
| En-tête (date, salutation, accès Réglages) | ✅ | Salutation à rendre contextuelle (matin/soir) |
| Reprendre la lecture | ✅ | Carrousel des titres `READING` — 🔜 afficher la progression en % |
| Compteurs (lus / en cours / à lire) | ✅ | |
| Prochaines sorties | ✅ | 🔜 filtrer sur les **séries suivies** plutôt que sur les tendances AniList |
| Derniers lus | ✅ | |
| État vide | ✅ | Renvoi vers Découvrir |
| **Réordonner / masquer les sections** | 🔜 | Persisté par utilisateur (`dashboard_layout`) |
| **Objectif annuel** | 🔜 | Jauge de progression, l'API existe déjà |

### 4.2 Collection ✅ / 🔜

Inventaire complet, bascule **Bibliothèque / Mangathèque**.

- ✅ Vue grille de couvertures, badge de rang, suppression rapide.
- ✅ Tri : ajout, titre, auteur, genre. Filtre par rang.
- 🔜 **Vue Séries** : regrouper par série, afficher « 12 / 105 tomes », signaler les
  tomes manquants dans une suite possédée.
- 🔜 Recherche textuelle dans sa propre collection.
- 🔜 Filtres additionnels : statut, année d'acquisition, éditeur, langue.
- 🔜 Pagination / défilement infini (l'API renvoie tout aujourd'hui).
- 🔜 Sélection multiple → action groupée (changer statut, supprimer, assigner un rang).

### 4.3 Détail d'un titre ✅ / 🔜

- ✅ Couverture, titre, auteurs, genres, pages, série, année, résumé.
- ✅ Attribution d'un rang (Or / Argent / Bronze).
- ✅ Marquer « en cours » / « lu ».
- 🔜 **Saisie de progression** : page courante ou pourcentage, dates début/fin.
- 🔜 **Note personnelle** (1–5) et commentaire privé.
- 🔜 **Éditions alternatives** : lister les autres `edition` de la même `work`, permettre
  d'indiquer laquelle on possède.
- 🔜 **Navigation de série** : tome précédent / suivant, accès à la fiche série.
- 🔜 Historique de lecture (relectures).

### 4.4 Découvrir ✅ / 🔜

Recherche dans le catalogue externe (Open Library pour les livres, AniList pour les mangas).

- ✅ Recherche par mot-clé, bascule Livre / Manga, ajout direct en collection ou en souhaits.
- 🔜 Recherche **par auteur** et **par année** (annoncé dans la vision, non implémenté).
- 🔜 **Scan de code-barres ISBN** (caméra) — clé en librairie, natif via Capacitor.
- 🔜 Fiche catalogue avant ajout : choisir *l'édition* et le *statut* initial.
- 🔜 Suggestions personnalisées à partir des genres les plus présents dans la collection.
- 🔜 Ajout manuel guidé (livre absent des catalogues).

### 4.5 Souhaits ✅ / 🔜

- ✅ Liste avec priorité (Prioritaire / Bientôt / Un jour), prix estimé, note, suppression.
- 🔜 **Budget total** et budget par priorité.
- 🔜 **Conversion en possession** en un geste (souhait → collection, statut `OWNED`).
- 🔜 Alerte « un souhait sort bientôt » (croisement avec les prochaines sorties).

### 4.6 Statistiques ✅ / 🔜

- ✅ Lus / en cours / à lire, pages lues, nombre de séries, répartition par genre (top 6).
- 🔜 **Évolution temporelle** : livres lus par mois, par année.
- 🔜 **Objectif annuel** : jauge, rythme nécessaire, projection.
- 🔜 Rythme de lecture (pages/jour), durée moyenne d'un livre.
- 🔜 Répartition par auteur, éditeur, langue, rang.
- 🔜 Rétrospective annuelle partageable.

### 4.7 Réglages ✅ / 🔜

- ✅ Import Booknode / Babelio (pseudo) et import CSV.
- ✅ Sélection de thème.
- 🔜 **Profil** : nom affiché, langue, fuseau.
- 🔜 **Export de sa bibliothèque** (CSV + JSON) — *exigence RGPD*.
- 🔜 **Suppression de compte et de ses données** — *exigence RGPD*.
- 🔜 Préférences de notification.
- 🔜 Gestion des catégories personnalisées (l'API existe déjà).
- 🔜 Mentions légales, CGU, politique de confidentialité.

### 4.8 Série 🔜 (écran à créer)

- Fiche série : couverture, résumé, statut (en cours / terminée), nombre total de tomes.
- Grille des tomes : possédés, lus, manquants, à paraître.
- Action « suivre la série » → alimente les prochaines sorties et les notifications.

## 5. Parcours clés

**P1 — Ajouter un livre repéré en librairie**
Découvrir → scan ISBN 🔜 → fiche catalogue → choix statut → ajouté en collection.

**P2 — Reprendre une lecture**
Accueil → carrousel « Reprendre » → Détail → saisir la page courante 🔜 → progression mise à jour.

**P3 — Compléter une série**
Collection → vue Séries 🔜 → série incomplète → tomes manquants → ajout aux souhaits en un geste.

**P4 — Arriver sur Librarius avec une bibliothèque existante**
Réglages → Import → pseudo Booknode → titres rapprochés du catalogue → collection peuplée.

**P5 — Suivre son objectif annuel**
Réglages/Stats → définir l'objectif → jauge sur l'Accueil 🔜 → rétrospective en fin d'année 🔜.

## 6. Règles métier

1. Un utilisateur ne peut posséder **qu'une fois** la même édition (`UNIQUE(user, edition)`).
   Une relecture n'est pas un doublon : elle relève de l'historique de lecture 🔜.
2. Statuts : `OWNED` (possédé, non lu) → `READING` → `READ`. Le passage à `READ` fixe
   `finished_at` ; le passage à `READING` fixe `started_at` s'il est vide.
3. Les rangs **Or / Argent / Bronze** sont des built-ins (`user_id NULL`) non
   supprimables. Un utilisateur peut créer ses propres catégories.
4. Un titre porte **au plus un rang**.
5. Les dates de sortie affichées sont celles **du fournisseur** (souvent JP/EN) et
   doivent être signalées comme telles tant que les dates VF ne sont pas disponibles.
6. Les données de possession sont **strictement privées** : aucune ressource ne renvoie
   les données d'un autre utilisateur.
7. Un import ne crée jamais de doublon : rapprochement par ISBN13, puis titre + auteur.

## 7. Hors périmètre (décisions explicites)

- Réseau social (amis, partage de bibliothèque, commentaires publics).
- Prêt de livres entre utilisateurs.
- Lecture de contenu (l'app gère la *possession*, pas les fichiers).
- Marketplace / achat intégré : les prix sont **saisis** par l'utilisateur, pas récupérés.
