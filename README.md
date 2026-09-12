# SENICO Diagnostic Stratégique

Application web digitalisant le canevas de diagnostic stratégique du **Plan Stratégique 2027-2031** de SENICO SA (Sénégalaise Industrie & Commerce). Les départements de l'entreprise, constitués en groupes de travail, saisissent les 17 sections du canevas ; un administrateur (comité de pilotage) suit l'avancement en temps réel, relit et valide les soumissions, puis le Directeur Général approuve celles qui entrent dans les documents consolidés, exportables en PDF, Word et Excel.

## Architecture

Monorepo à deux applications :

```
/backend    Spring Boot 3 / Java 21 — API REST + WebSocket (STOMP), MySQL 8, Flyway
/frontend   Next.js 14 (App Router) / TypeScript — Tailwind CSS, shadcn/ui, TanStack Query
/deploy     Configurations Nginx, systemd, PM2 (voir DEPLOYMENT.md)
```

### Backend

- **Auth** : JWT (access + refresh), bcrypt, rôles `ADMIN` / `DIRECTEUR_GENERAL` / `GROUP_LEADER`, rate limiting sur le login. Le DG consulte et arbitre tout, sans l'administration technique (groupes, mots de passe, cycles, édition des saisies), qui reste à l'admin — frontière posée dans `SecurityConfig` et vérifiée par `DirectionGeneraleAccessIT`.
- **Comptes** : l'admin crée les comptes (`/admin/users`) pour les trois rôles — un chef de groupe est rattaché à une direction, dont il remplit le canevas — et le mot de passe généré ne s'affiche qu'une fois. Tout mot de passe attribué par un tiers (création, réinitialisation) lève `must_change_password` : le serveur refuse alors tout appel autre que la connexion et le changement de mot de passe (`PasswordChangeGuardFilter`), jusqu'à ce que le titulaire choisisse le sien. Vérifié par `PremiereConnexionIT`.
- **Validation à deux niveaux** : le comité de pilotage valide une section soumise (`SUBMITTED` → `VALIDATED`), puis le DG l'approuve (`dg_approved_at`). Seule cette approbation fait entrer une contribution dans le Document de consolidation, la Note de synthèse et le Plan Stratégique de SENICO ; le plan sectoriel d'une direction reste un document de travail et affiche tout. Toute sortie de `VALIDATED` — révision, main rendue au groupe, nouvelle soumission, correction du contenu par l'admin — retire l'approbation, qui est alors à redemander.
- **Moteur de sections** : stockage JSON générique par section (`SectionResponse`), validation structurelle par type de section, verrouillage serveur des sections soumises, historique des 20 dernières révisions.
- **Champs calculés / synchronisations** (calculés à la lecture, jamais figés en base) :
  - Section 5 (TOWS) ← listes SWOT de la Section 4
  - Section 7 (Inventaire) ← agrégation des Sections 1, 3, 4, 6
  - Sections 9, 10, 11, 12, 17 ← intitulés d'axes de la Section 8
  - Section 11 (Budget) : totaux ligne / colonne / axe / général
  - Section 2 (Ressources) : lignes du modèle client rajoutées à leur place dans une matrice saisie avant leur ajout
  - Section 14 (Risques) : criticité = Niveau × Quotation
  - Section 14B (Effectifs) : totaux par année, qui suivent la hiérarchie (hiérarchie et statut ventilent les mêmes agents) ; ligne « Fonctionnaire » rajoutée si absente
  - Section 15 (Financement) : pourcentages par source
  - Section 16 (Business plan) : résultat d'exploitation, résultat net, variation et trésorerie cumulée
- **Temps réel** : WebSocket STOMP/SockJS (`/ws`) pousse au dashboard admin les changements de statut, soumissions et activité ; le frontend bascule sur du polling (15s) si la connexion est indisponible.
- **Exports** : PDF (OpenPDF) et Word (Apache POI) par groupe, Excel consolidé (une feuille par section, toutes les réponses de tous les groupes).
- **Note de synthèse** (`PsdBriefBuilder`) : le Plan Stratégique sur le plan d'un plan stratégique publié — sigles, mot du DG, l'essentiel du plan, contexte, méthode, présentation, parties prenantes, diagnostic (performances, ressources et compétences, PESTEL, SWOT, orientations croisées, risques et leur impact), bilan des performances des années précédentes, principaux enjeux et défis (avec la synthèse des contraintes par domaine d'activités), facteurs clés, cadre stratégique, mise en œuvre (budget par axe avec graphiques, financement, effectifs par hiérarchie, statut et genre), pilotage, synthèse du cadre stratégique (tableau OS / actions / contraintes du modèle client, puis récapitulatif unique objectifs, actions, indicateur, cible 2031 et coût), conclusion et annexes. Les tableaux du modèle client (matrice des ressources, effectifs, synthèse du cadre stratégique, contraintes) en reprennent les lignes et les colonnes. PDF en deux passes pour un sommaire paginé ; police Roboto embarquée (`resources/fonts`, licence Apache 2.0).
- **Cadre stratégique de l'entreprise** : vision, mission, valeurs, dispositif de pilotage et axes stratégiques sont arrêtés par la Direction Générale (blocs narratifs, écran « Plan Stratégique de SENICO ») et remplacent dans les documents les propositions de chaque direction. Les axes (`AXES_CONSOLIDES`, format lu par `PsdConsolidatedAxes`) regroupent les axes des directions : objectifs, budget et actions s'additionnent sous l'axe de l'entreprise ; un axe de direction non rattaché est signalé.

### Frontend

- App Router Next.js 14, TypeScript strict, Tailwind CSS avec le design system SENICO (tokens dans `tailwind.config.ts` / `globals.css`).
- TanStack Query pour les données serveur, Zustand pour la session (JWT persistés), react-hook-form + zod pour les formulaires, Recharts pour les graphiques, sonner pour les toasts.
- 17 formulaires de section typés individuellement (`src/types/sections.ts`), avec autosave debouncée + périodique et confirmation de soumission.
- Vue projecteur (`/projection`, réservée ADMIN) : écran plein format pensé pour être projeté en salle pendant les séances de travail — KPIs, classement des groupes, activité en direct et avancement par section, rafraîchis en temps réel (WebSocket + repli polling 15s). Accessible depuis la barre latérale admin ("Vue projecteur").

## Démarrage en développement

### Prérequis

- Java 21, Maven 3.9+
- Node.js 20+, npm
- MySQL 8 en local (ou accessible)

### Backend

```bash
cd backend
# Créer la base : CREATE DATABASE senico_diagnostic CHARACTER SET utf8mb4;
export DB_USER=root DB_PASSWORD=... # ou variables d'environnement equivalentes (voir .env.example)
mvn spring-boot:run
```

Le backend démarre sur `http://localhost:8080`, exécute automatiquement les migrations Flyway (schéma + données de démonstration) au premier lancement.

### Frontend

```bash
cd frontend
cp .env.example .env.local
npm install
npm run dev
```

Le frontend démarre sur `http://localhost:3000`.

### Travailler dans l'application (mode rapide)

`npm run dev` compile chaque page à sa première visite et sert du JavaScript non optimisé : plus de 16 Mo pour le tableau de bord admin, contre 260 Ko une fois buildé. C'est le mode pour modifier le code, pas pour s'en servir. Pour une séance de travail :

```bash
cd frontend
npm run prod   # build de production, puis serveur sur http://localhost:3000
```

Arrêter d'abord `npm run dev` : les deux partagent le dossier `.next` et le port 3000. Relancer `npm run prod` après toute modification du frontend.

## Comptes de démonstration

Créés automatiquement par la migration `V2__seed_data.sql` :

| Rôle | Identifiant | Mot de passe |
|---|---|---|
| Administrateur | `admin` | `Admin@2027` |
| Chef de groupe — Direction Commerciale | `dir.commerciale` | `Groupe@2027` |
| Chef de groupe — Direction Technique et Exploitation | `dir.technique` | `Groupe@2027` |
| Chef de groupe — Direction Financière et Comptable | `dir.financiere` | `Groupe@2027` |

⚠️ À changer avant toute mise en production réelle (cf. `DEPLOYMENT.md`).

## Scripts utiles

| Commande | Emplacement | Effet |
|---|---|---|
| `mvn spring-boot:run` | `backend/` | Démarre l'API en profil `dev` |
| `mvn clean package` | `backend/` | Build du jar exécutable |
| `npm run dev` | `frontend/` | Démarre le frontend en mode développement |
| `npm run build` | `frontend/` | Build de production (type-check inclus) |
| `npm run prod` | `frontend/` | Build de production puis démarrage : le mode rapide pour utiliser l'application |
| `npm run lint` | `frontend/` | Lint ESLint |

## Déploiement

Voir [`DEPLOYMENT.md`](./DEPLOYMENT.md) pour le runbook complet (Nginx, systemd, PM2, Flyway, checklist de recette).
