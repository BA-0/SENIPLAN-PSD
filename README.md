# SENICO Diagnostic Stratégique

Application web digitalisant le canevas de diagnostic stratégique du **Plan Stratégique de Développement (PSD) 2027-2031** de SENICO SA (Sénégalaise Industrie & Commerce). Les départements de l'entreprise, constitués en groupes de travail, saisissent les 17 sections du canevas ; un administrateur (comité de pilotage) suit l'avancement en temps réel, relit et valide les soumissions, puis exporte les résultats.

## Architecture

Monorepo à deux applications :

```
/backend    Spring Boot 3 / Java 21 — API REST + WebSocket (STOMP), MySQL 8, Flyway
/frontend   Next.js 14 (App Router) / TypeScript — Tailwind CSS, shadcn/ui, TanStack Query
/deploy     Configurations Nginx, systemd, PM2 (voir DEPLOYMENT.md)
```

### Backend

- **Auth** : JWT (access + refresh), bcrypt, rôles `ADMIN` / `GROUP_LEADER`, rate limiting sur le login.
- **Moteur de sections** : stockage JSON générique par section (`SectionResponse`), validation structurelle par type de section, verrouillage serveur des sections soumises, historique des 20 dernières révisions.
- **Champs calculés / synchronisations** (calculés à la lecture, jamais figés en base) :
  - Section 5 (TOWS) ← listes SWOT de la Section 4
  - Section 7 (Inventaire) ← agrégation des Sections 1, 3, 4, 6
  - Sections 9, 10, 11, 12, 17 ← intitulés d'axes de la Section 8
  - Section 11 (Budget) : totaux ligne / colonne / axe / général
  - Section 14 (Risques) : criticité = Niveau × Quotation
  - Section 15 (Financement) : pourcentages par source
  - Section 16 (Business plan) : résultat d'exploitation, résultat net, variation et trésorerie cumulée
- **Temps réel** : WebSocket STOMP/SockJS (`/ws`) pousse au dashboard admin les changements de statut, soumissions et activité ; le frontend bascule sur du polling (15s) si la connexion est indisponible.
- **Exports** : PDF (OpenPDF) et Word (Apache POI) par groupe, Excel consolidé (une feuille par section, toutes les réponses de tous les groupes).

### Frontend

- App Router Next.js 14, TypeScript strict, Tailwind CSS avec le design system SENICO (tokens dans `tailwind.config.ts` / `globals.css`).
- TanStack Query pour les données serveur, Zustand pour la session (JWT persistés), react-hook-form + zod pour les formulaires, Recharts pour les graphiques, sonner pour les toasts.
- 17 formulaires de section typés individuellement (`src/types/sections.ts`), avec autosave debouncée + périodique et confirmation de soumission.

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
| `npm run lint` | `frontend/` | Lint ESLint |

## Déploiement

Voir [`DEPLOYMENT.md`](./DEPLOYMENT.md) pour le runbook complet (Nginx, systemd, PM2, Flyway, checklist de recette).
