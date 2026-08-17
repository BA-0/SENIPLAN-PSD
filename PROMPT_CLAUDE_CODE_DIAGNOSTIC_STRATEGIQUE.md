# PROMPT CLAUDE CODE — Application « SENICO Diagnostic Stratégique » (PSD 2027-2031)

> Copie tout ce qui suit dans Claude Code (VS Code).

---

Tu es un développeur fullstack senior. Tu vas construire une application web complète de A à Z pour **SENICO SA (Sénégalaise Industrie & Commerce)**, appelée **« SENICO Diagnostic Stratégique »**, qui digitalise le canevas du diagnostic stratégique du **Plan Stratégique de Développement (PSD) 2027-2031**.

## 1. CONTEXTE MÉTIER

SENICO organise une retraite stratégique. Les départements de l'entreprise sont constitués en **groupes de travail** (un groupe = un département). Chaque groupe doit remplir **l'intégralité** d'un canevas de diagnostic stratégique composé de **17 sections** (détaillées plus bas). Chaque groupe est dirigé par un **chef de groupe** qui est le seul à se connecter et à saisir/soumettre les réponses au nom de son groupe. Un **administrateur** (la DSI / le comité de pilotage) suit en **temps réel** l'avancement des saisies et soumissions de tous les groupes.

## 2. RÔLES ET PERMISSIONS

### ADMIN
- Crée / modifie / désactive les groupes de travail (départements) et leurs chefs de groupe (identifiants générés ou définis manuellement, mot de passe réinitialisable).
- Tableau de bord global **temps réel** : progression de chaque groupe (% de sections complétées, sections en cours, sections soumises), horodatage de la dernière activité, statut par section (Non commencé / En cours / Soumis / Validé / À réviser).
- Peut consulter en lecture toutes les saisies de tous les groupes, section par section, **même avant soumission** (suivi en direct des brouillons).
- Peut **valider** ou **renvoyer pour révision** une section soumise, avec un commentaire.
- Peut exporter les réponses : par groupe (PDF et Word complets reprenant la structure du canevas) et export global consolidé (Excel : une feuille par section, toutes les réponses de tous les groupes).
- Vue comparative : afficher côte à côte les réponses de plusieurs groupes sur une même section (ex. comparer les SWOT de tous les départements).
- Statistiques : nombre de groupes, taux de complétion global, sections les plus/moins avancées, activité récente (timeline des dernières actions).

### CHEF DE GROUPE (répondant)
- Se connecte via une **page de login** (identifiant + mot de passe).
- Voit uniquement l'espace de travail de **son** groupe.
- Tableau de bord personnel : progression de son groupe (jauge de complétion, checklist des 17 sections avec statuts), échéance éventuelle, derniers commentaires de l'admin.
- Remplit chaque section via des formulaires dynamiques (tableaux éditables : ajout/suppression de lignes).
- **Sauvegarde automatique** (brouillon, autosave toutes les X secondes + bouton « Enregistrer »).
- **Soumet** une section quand elle est terminée (une section soumise devient en lecture seule, sauf si l'admin la renvoie « À réviser »).
- Peut télécharger un PDF récapitulatif des réponses de son groupe.

## 3. LES 17 SECTIONS DU CANEVAS (modèle exact des formulaires)

Chaque section est un formulaire structuré. Les tableaux sont **dynamiques** (le chef de groupe ajoute autant de lignes que nécessaire, sauf indication contraire). Respecte exactement les intitulés ci-dessous.

### Section 1 — Analyse des parties prenantes
Tableau dynamique, colonnes : Acteur (PP) | Rôles / Responsabilités | Attentes / Intérêt / Priorités | Stratégie d'adaptation | Niveau d'importance (Fort / Moyen / Faible — select) | Niveau d'influence (Fort / Moyen / Faible — select) | Actions.

### Section 2 — Matrice d'analyse des ressources et compétences
Tableau à **lignes fixes** (les ressources sont prédéfinies), colonnes : Forces / Acquis | Faiblesses | Défis à relever. Lignes :
1. Cadre juridique, institutionnel et organisationnel
2. Leadership, Pilotage, Management et Gouvernance
3. Position concurrentielle
4. Capacités institutionnelles (Ressources matérielles, financières, humaines et immatérielles)
5. Budget ou ressources financières
6. Comptabilité et gestion financière
7. Système de contrôle
8. Système d'information et de gestion
9. Suivi évaluation
10. Communication
11. Autres (Achats, Exploitation commerciale, Technique et armement, RH)
12. Compétences

### Section 3 — Analyse PESTEL
Tableau à lignes fixes : Politique, Économique, Social et culturel, Technologique, Environnemental, Légal. Colonnes : Menaces | Opportunités | Actions pour atténuer les menaces ou saisir les opportunités.

### Section 4 — Analyse SWOT (FFOM)
Grille 2×2 classique : Interne → Forces / Faiblesses ; Externe → Opportunités / Menaces. Chaque quadrant = liste dynamique d'items (ajout/suppression). UI : matrice visuelle 4 quadrants colorés.

### Section 5 — Mise en relation du diagnostic stratégique (matrice de confrontation SWOT / TOWS)
Les listes de forces, faiblesses, opportunités et menaces sont **pré-remplies automatiquement depuis la Section 4** (lecture seule, synchronisées). Le groupe remplit les cellules de croisement :
- Forces × Faiblesses : comment maximiser les forces ? comment minimiser les faiblesses ? en quoi les forces permettent de maîtriser les faiblesses ?
- Opportunités : comment maximiser les opportunités ? comment utiliser les forces pour tirer parti des opportunités ? comment corriger les faiblesses en tirant parti des opportunités ?
- Menaces : comment minimiser les menaces ? comment utiliser les forces pour réduire les menaces ? comment minimiser les faiblesses et les menaces ?
- En quoi les opportunités permettent de minimiser les menaces ?

Disposition UI : matrice de confrontation classique — **colonnes** = Forces (liste auto) et Faiblesses (liste auto) ; **lignes** = Opportunités (liste auto) et Menaces (liste auto) ; les 4 cellules de croisement (F×O, f×O, F×M, f×M) + les cellules de bordure (maximiser forces, minimiser faiblesses, maximiser opportunités, minimiser menaces) et les 2 cellules de synthèse (forces↔faiblesses, opportunités↔menaces) sont des zones de texte riches. Sur écran étroit, bascule en accordéon (une question = un panneau) tout en conservant les listes SWOT visibles en rappel.

### Section 6 — Analyse causale
Tableau : Sources | Analyse. Lignes fixes : Manifestation des problèmes (effet négatif, besoins) | Causes immédiates | Causes sous-jacentes | Causes profondes | Solutions. Chaque cellule peut contenir plusieurs items.

### Section 7 — Inventaire
Page de synthèse consolidée regroupant en lecture/édition légère : SWOT, PESTEL, Analyse des parties prenantes, Analyse causale (récapitulatif auto-alimenté depuis les sections 1, 3, 4, 6, avec possibilité d'ajouter une note de synthèse).

### Section 8 — Axes stratégiques / Orientations
4 axes : Axe 1, Axe 2, Axe 3, Axe 4. Pour chacun : intitulé + description. (Les intitulés saisis ici alimentent automatiquement les sections 9 à 12 et 17.)

### Section 9 — Cadre logique (×4, un par axe)
Pour chaque axe : champ « Objectif », puis tableau : Logique d'intervention | Indicateurs Objectivement Vérifiables (IOV) | Moyens et Sources de Vérification | Conditions Critiques / Hypothèses. Lignes fixes : Impact (Finalité) | Effet (Objectif spécifique) | Effets immédiats (Résultats immédiats) | Extrants (Produits / Activités) | Ressources / Intrants (Moyens).

### Section 10 — Plan d'actions 2027-2031 (×4, un par axe)
Par axe, structuré par blocs EFFET (EFFET 1 — OS1, EFFET 2 — OS2, … le nombre d'effets est **dynamique**, 4 par défaut, ajout/suppression possible ; chaque effet porte un intitulé saisi et synchronisé avec les sections 12 et 17). Dans chaque bloc, tableau dynamique : Extrants | Activités pour atteindre les résultats | 2027 | 2028 | 2029 | 2030 | 2031 (cases à cocher de planification : cocher = activité prévue cette année-là) | Responsables.

### Section 11 — Budget 2027-2031 (×4, un par axe, montants en FCFA)
Même structure que le plan d'actions : Extrants | Activités | montants numériques 2027…2031 | TOTAUX (**calculés automatiquement** ligne + colonne + total axe + total général) | Responsable. Formatage des nombres : séparateurs de milliers, suffixe FCFA.

### Section 12 — Cadre de mesure de rendement 2027-2031 (×4, un par axe)
Tableau : Résultat / Extrant | Indicateur (IOV) | Réf. 2026 | 2027 | 2028 | 2029 | 2030 | 2031 | Responsables. Regroupements : Impact (Finalité — horizon 2031), Effet (Objectif spécifique de l'axe), Effets immédiats (par OS — synchronisés avec les effets définis en Section 10), Extrants (Produits), Ressources / Intrants (Moyens).

### Section 13 — Fiche d'indicateurs
Tableau dynamique : Intitulés indicateurs | Modes de calcul | Périodicités | Sources et moyens de collecte | Sources de vérification | Structures responsables.

### Section 14 — Matrice d'analyse des risques
Tableau dynamique : Catégorie de risque | Présence (Oui/Non — select) | Quels risques (nature détaillée) | Niveau de risque N (Élevé=3 / Moyen=2 / Faible=1 — select) | Impact sur les domaines d'activités | Quotation Q (Élevé=3 / Moyen=2 / Faible=1 — select) | **Criticité = N × Q (calculée automatiquement, badge coloré : 6-9 rouge « Élevée — action prioritaire », 3-4 orange « Moyenne — à surveiller », 1-2 vert « Faible — sous contrôle »)** | Actions de mitigation ou de contingence.

### Section 15 — Plan de financement
Tableau à lignes fixes : Ressources propres | Subventions publiques | Partenaires techniques et financiers (PTF) | Emprunts | Autres sources | **TOTAL (auto)**. Colonnes : Montant (FCFA) | Pourcentage % (**calculé auto**) | Modalités de mobilisation | Période | Responsables.

### Section 16 — Business plan
a) **Compte d'exploitation prévisionnel** : Rubriques (Produits d'exploitation / Chiffre d'affaires, Charges d'exploitation, **Résultat d'exploitation = auto**, Charges financières, **Résultat net = auto**) × colonnes 2027…2031 + TOTAL auto.
b) **Flux financiers (trésorerie)** : Flux d'exploitation, Flux d'investissement, Flux de financement, **Variation nette de trésorerie = auto**, **Trésorerie de fin de période = auto (report cumulé)** × colonnes 2027…2031.

### Section 17 — Tableau de synthèse du cadre stratégique — PSD 2027-2031
Champ « Vision » global, puis pour chaque axe (intitulés repris de la Section 8) : liste dynamique d'Orientations stratégiques (OS1, OS2, …), chaque OS contenant des Actions (Action 1.1, 1.2, … numérotation automatique) et une colonne « Contraintes à lever ou opportunités à saisir ».

## 4. SUIVI TEMPS RÉEL (exigence forte)

- Utilise des **WebSockets (STOMP over SockJS côté Spring Boot)** pour pousser en direct au dashboard admin : changements de statut de section, soumissions, et progression de saisie (événement émis à chaque autosave).
- Le dashboard admin se met à jour **sans rechargement** : cartes par groupe avec jauge circulaire de progression, indicateur « en train de saisir » (présence en ligne), flux d'activité en direct (« Le groupe Production a soumis la Section 4 — SWOT il y a 2 min »).
- Fallback polling (toutes les 15 s) si le WebSocket est indisponible.

## 5. STACK TECHNIQUE (impérative — standard SENICO)

- **Backend** : Spring Boot 3.x / Java 21, Spring Security + **JWT** (access + refresh token), Spring Data JPA, Spring WebSocket (STOMP), validation Jakarta, MapStruct ou DTO manuels propres.
- **Base de données** : **MySQL 8**, migrations **Flyway**, charset utf8mb4.
- **Frontend** : **Next.js 14** (App Router), TypeScript, **Tailwind CSS**, shadcn/ui, TanStack Query (React Query), Zustand pour l'état global léger, react-hook-form + zod pour les formulaires, Recharts pour les graphiques, sonner pour les toasts, lucide-react pour les icônes.
- **Exports** : PDF côté backend (OpenPDF ou iText / ou génération HTML + impression), Excel via Apache POI, Word via docx4j ou poi-ooxml.
- **Déploiement cible** : Ubuntu 22.04 LTS, Nginx en reverse proxy, backend en service systemd (variables d'environnement via fichier `.env` chargé par systemd `EnvironmentFile`), frontend via **PM2**. Fournis les fichiers de conf Nginx, le unit systemd et un runbook de déploiement `DEPLOYMENT.md`.
- Prévois un fichier `application.yml` avec profils `dev` et `prod`, CORS configuré proprement, et un **seed** (data.sql ou CommandLineRunner) créant : 1 admin par défaut + 3 groupes de démonstration.

## 6. MODÈLE DE DONNÉES (guide)

Entités principales :
- `User` (id, username, password bcrypt, fullName, role: ADMIN | GROUP_LEADER, groupId nullable, enabled, lastLoginAt)
- `WorkGroup` (id, name = nom du département, description, leader, createdAt)
- `Section` (référentiel des 17 sections : code, titre, ordre, type)
- `GroupSectionStatus` (groupId, sectionId, status: NOT_STARTED | IN_PROGRESS | SUBMITTED | VALIDATED | REVISION_REQUESTED, submittedAt, validatedAt, adminComment, lastActivityAt)
- Contenu des réponses : pour la flexibilité des tableaux dynamiques, stocke le contenu de chaque section en **JSON** (colonne JSON MySQL) dans une entité `SectionResponse` (groupId, sectionId, contentJson, version, updatedAt, updatedBy) + historique léger `SectionResponseRevision` (audit des sauvegardes, garder les 20 dernières).
- `ActivityLog` (groupId, userId, action, sectionId, timestamp) pour le flux d'activité temps réel.
- Les structures JSON de chaque section doivent être **typées côté frontend (interfaces TypeScript par section)** et **validées côté backend** (schémas de validation par section).

## 7. CHARTE GRAPHIQUE SENICO — DESIGN SYSTEM COMPLET (impérative)

Implémente ce design system dans `tailwind.config.ts` (tokens) + `globals.css` (variables CSS) et applique-le partout, y compris dans les composants shadcn/ui (surcharge les variables du thème shadcn avec ces valeurs).

### 7.1 Palette de couleurs

**Vert SENICO (primary)** — base `#2D7A45` :

| Token | Hex | Usage |
|---|---|---|
| `primary-50` | `#EEF7F1` | Fonds de survol, fonds de badges verts, lignes de tableau sélectionnées |
| `primary-100` | `#D6ECDD` | Fonds d'alertes succès, chips |
| `primary-200` | `#ADD9BB` | Bordures d'éléments actifs |
| `primary-300` | `#7FC297` | Graphiques (séries secondaires) |
| `primary-400` | `#52A470` | Jauges, barres de progression, hover d'icônes |
| `primary-500` | `#2D7A45` | **Couleur principale** : boutons primaires, liens, éléments actifs, focus |
| `primary-600` | `#266A3B` | Hover des boutons primaires |
| `primary-700` | `#1F5731` | Active/pressed, texte sur fond `primary-50` |
| `primary-800` | `#184427` | Sidebar sombre (fond), en-têtes d'exports PDF |
| `primary-900` | `#11301C` | Sidebar sombre (dégradé bas), texte de titres sur fond clair vert |

**Rouge SENICO (accent/danger)** — base `#EC1D25` :

| Token | Hex | Usage |
|---|---|---|
| `accent-50` | `#FDEDEE` | Fonds d'alertes erreur |
| `accent-100` | `#FBD4D6` | Fonds de badges criticité élevée |
| `accent-500` | `#EC1D25` | **Rouge principal** : erreurs, criticité 6-9, actions destructives, accents (swoosh) |
| `accent-600` | `#D01118` | Hover boutons destructifs |
| `accent-700` | `#A80E14` | Active/pressed destructif, texte d'erreur sur fond `accent-50` |

**Neutres (gris)** — utilise l'échelle Tailwind `slate` :
- Fond de l'app : `slate-50` `#F8FAFC` ; cartes : blanc `#FFFFFF`.
- Bordures : `slate-200` `#E2E8F0` ; séparateurs : `slate-100`.
- Texte principal : `slate-800` `#1E293B` ; texte secondaire : `slate-500` `#64748B` ; texte désactivé/placeholder : `slate-400`.

**Couleurs sémantiques de statut** (badges, heatmap, pastilles) :

| Statut | Fond | Texte / bordure |
|---|---|---|
| Non commencé | `slate-100` | `slate-500` |
| En cours | `#EFF6FF` (blue-50) | `#1D4ED8` (blue-700) |
| Soumis | `primary-50` | `primary-700` |
| Validé | `primary-500` (plein) | blanc |
| À réviser | `#FFF7ED` (orange-50) | `#C2410C` (orange-700) |
| Criticité élevée (6-9) | `accent-100` | `accent-700` |
| Criticité moyenne (3-4) | `#FEF3C7` (amber-100) | `#B45309` (amber-700) |
| Criticité faible (1-2) | `primary-100` | `primary-700` |

Règle d'or : le vert domine (identité, navigation, actions positives), le rouge est **rare et signifiant** (erreurs, criticité, suppression) — ne jamais l'utiliser en décoration.

### 7.2 Typographie

- **Police unique : Inter** (via `next/font/google`, `display: swap`), fallback `system-ui, -apple-system, sans-serif`. Chiffres tabulaires (`font-feature-settings: "tnum"`) dans tous les tableaux budgétaires et KPIs.
- Échelle :

| Élément | Taille / graisse | Couleur |
|---|---|---|
| H1 (titre de page) | 24px / semibold (600) | `slate-800` |
| H2 (titre de section/carte) | 18px / semibold | `slate-800` |
| H3 (sous-titre) | 15px / medium (500) | `slate-700` |
| Corps | 14px / regular (400), line-height 1.6 | `slate-700` |
| Texte secondaire / aide | 13px / regular | `slate-500` |
| Labels de formulaire | 13px / medium | `slate-700` |
| Valeurs KPI (dashboards) | 30px / bold (700), tabulaire | `slate-800` |
| En-têtes de tableau | 12px / semibold, uppercase, letter-spacing 0.05em | `slate-500` |
| Badges | 12px / medium | selon statut |

### 7.3 Boutons

Tous : hauteur 40px (36px en `sm`), padding horizontal 16px, `rounded-lg`, 14px medium, transition 150ms, focus ring `ring-2 ring-primary-500/40 ring-offset-2`, état disabled à 50% d'opacité + `cursor-not-allowed`, icône lucide 16px à gauche si pertinent.

| Variante | Défaut | Hover | Active | Usage |
|---|---|---|---|---|
| **Primaire** | fond `primary-500`, texte blanc | `primary-600` | `primary-700` | Enregistrer, Soumettre, Se connecter, Créer |
| **Secondaire** | fond blanc, bordure `slate-200`, texte `slate-700` | fond `slate-50`, bordure `slate-300` | fond `slate-100` | Annuler, Précédent, Exporter |
| **Ghost** | transparent, texte `slate-600` | fond `slate-100` | fond `slate-200` | Actions de tableau, icônes |
| **Destructif** | fond `accent-500`, texte blanc | `accent-600` | `accent-700` | Supprimer, Désactiver un groupe |
| **Destructif discret** | texte `accent-500`, transparent | fond `accent-50` | fond `accent-100` | Supprimer une ligne de tableau |
| **Lien** | texte `primary-500`, sans fond | souligné, `primary-600` | — | Liens inline |

Le bouton « Soumettre la section » est le seul à pouvoir être plus visible : taille `lg` (44px) + légère ombre `shadow-md shadow-primary-500/20`.

### 7.4 Champs de formulaire

- Inputs/selects/textareas : hauteur 40px, `rounded-lg`, fond blanc, bordure `slate-200`, texte 14px `slate-800`, placeholder `slate-400`.
- Focus : bordure `primary-500` + `ring-2 ring-primary-500/20`. Erreur : bordure `accent-500` + message 13px `accent-700` sous le champ. Désactivé/lecture seule (sections soumises) : fond `slate-50`, texte `slate-500`.
- Labels au-dessus du champ, astérisque rouge pour les champs requis.
- Cellules de tableaux éditables : sans bordure visible au repos (l'input épouse la cellule), bordure + ring au focus, alignement à droite pour les montants.

### 7.5 Cartes, tableaux et surfaces

- Cartes : fond blanc, `rounded-xl`, bordure `slate-200`, ombre `shadow-sm` (hover `shadow-md` si cliquable), padding 20–24px, titre H2 + éventuelle action à droite.
- Tableaux : en-tête fond `slate-50` sticky, lignes hauteur ≥ 48px, zébrage `slate-50/50`, hover de ligne `primary-50/60`, bordures horizontales `slate-100` uniquement, ligne TOTAL en `semibold` fond `primary-50`.
- Sidebar : fond dégradé `primary-800 → primary-900`, texte `white/80`, item actif fond `white/10` + barre gauche 3px `#7FC297` + texte blanc, hover `white/5`, logo SENICO en haut sur pastille blanche `rounded-lg` padding 8px, pastilles de statut des sections en 8px à droite de chaque item.
- Header : blanc, bordure basse `slate-200`, hauteur 64px.
- Modales : `rounded-xl`, overlay `slate-900/50`, largeur max 480px (confirmations) / 640px (formulaires).
- Toasts (sonner) : succès à accent vert `primary-500`, erreur à accent `accent-500`, position bas-droite.

### 7.6 Iconographie, rayons, ombres, animations

- Icônes : **lucide-react** uniquement, 16px (inline) / 20px (navigation, boutons) / 24px (KPIs), stroke 1.75.
- Rayons : `rounded-lg` (8px) champs et boutons, `rounded-xl` (12px) cartes et modales, `rounded-full` badges, avatars et jauges.
- Ombres : `shadow-sm` par défaut, `shadow-md` au survol d'éléments cliquables — jamais plus lourd.
- Animations : transitions 150–200ms `ease-out` (hover, focus), apparition des modales/toasts en fade+scale léger, squelettes de chargement `animate-pulse` gris. Pas d'animations gratuites.

### 7.7 Graphiques (Recharts)

- Série principale `primary-500`, séries secondaires `primary-300` / `#52A470`, comparaisons neutres `slate-300`; le rouge `accent-500` réservé aux dépassements/risques.
- Jauges circulaires de progression : piste `slate-100`, arc `primary-500`, valeur centrale 30px bold.
- Grilles `slate-100`, axes 12px `slate-500`, tooltips sur carte blanche `rounded-lg shadow-md`.

### 7.8 Logo

- Fichier fourni : place-le dans `frontend/public/logo-senico.png`. Description : wordmark « senico » minuscules arrondies vert `#2D7A45`, swoosh rouge `#EC1D25` intégrant le point du « i », baseline « SÉNÉGALAISE INDUSTRIE COMMERCE » en rouge.
- Emplacements : sidebar (pastille blanche), page de login (centré, largeur ~180px), en-têtes des exports PDF/Word, favicon (générer un favicon à partir du « s » vert ou du swoosh).
- Ne jamais déformer, recolorer ni poser le logo sur fond vert/rouge — toujours sur blanc.

## 8. EXIGENCES UI/UX (soigne particulièrement)

- **Page de login** élégante : logo SENICO centré, carte de connexion, fond avec dégradé subtil vert, messages d'erreur clairs.
- **Layout applicatif** : sidebar de navigation (dashboard, sections 1→17 avec pastilles de statut, exports, paramètres), header avec nom du groupe / de l'utilisateur, avatar, déconnexion.
- **Dashboard admin** : KPIs en cartes (groupes actifs, % complétion global, sections soumises / validées, activité du jour), graphique en barres de progression par groupe, heatmap groupes × sections (statuts en couleurs), flux d'activité temps réel, tableau des groupes avec drill-down.
- **Dashboard chef de groupe** : jauge circulaire de complétion, stepper/checklist des 17 sections cliquables, prochaines sections à remplir, commentaires admin en évidence.
- **Formulaires** : tableaux éditables agréables (lignes zébrées, ajout de ligne fluide, suppression avec confirmation), autosave avec indicateur discret (« Enregistré à 14:32 » / « Enregistrement… »), navigation Précédent/Suivant entre sections, barre de progression de la section.
- Responsive (utilisable sur laptop et tablette), états vides illustrés, loaders squelettes, confirmations avant soumission (« Une fois soumise, la section ne sera plus modifiable »).
- Toute l'interface en **français**.

## 9. SÉCURITÉ

- Mots de passe bcrypt, JWT en header Authorization, refresh token, expiration raisonnable.
- Contrôle d'accès strict côté API : un chef de groupe ne peut lire/écrire QUE les données de son groupe ; les endpoints admin protégés par rôle.
- Verrouillage des sections soumises côté backend (pas seulement côté UI).
- Validation serveur de toutes les entrées, protection CORS, rate limiting simple sur le login.

## 10. LIVRABLES ET FAÇON DE TRAVAILLER

1. Commence par me présenter l'**architecture du projet** (arborescence backend + frontend) et le schéma de la base, puis enchaîne directement sur l'implémentation.
2. Construis le projet dans un monorepo : `/backend` (Spring Boot) et `/frontend` (Next.js), avec un `README.md` racine (lancement en dev, comptes de démo, scripts).
3. Implémente dans cet ordre : auth + rôles → gestion des groupes (admin) → moteur de sections (statuts, autosave, soumission) → les 17 formulaires → dashboards + temps réel → exports PDF/Excel/Word → conf de déploiement.
4. Code propre, commenté en français quand utile, conventions REST cohérentes (`/api/v1/...`).
5. À la fin, fournis `DEPLOYMENT.md` (Nginx + systemd + PM2 + Flyway) et une checklist de recette.

Travaille de manière autonome : ne me pose des questions que si un point est réellement bloquant, sinon prends des décisions raisonnables et documente-les.
