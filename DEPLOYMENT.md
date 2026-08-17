# DEPLOYMENT.md — SENICO Diagnostic Stratégique

Runbook de déploiement en production sur **Ubuntu 22.04 LTS**, Nginx en reverse proxy, backend en service **systemd**, frontend géré par **PM2**.

## 1. Prérequis serveur

```bash
sudo apt update && sudo apt upgrade -y
sudo apt install -y openjdk-21-jdk mysql-server nginx git curl
curl -fsSL https://deb.nodesource.com/setup_20.x | sudo -E bash -
sudo apt install -y nodejs
sudo npm install -g pm2
```

Créer un utilisateur système dédié :

```bash
sudo useradd -r -m -s /usr/sbin/nologin senico
sudo mkdir -p /opt/senico-diagnostic /etc/senico-diagnostic /var/log/senico-diagnostic
sudo chown -R senico:senico /opt/senico-diagnostic /var/log/senico-diagnostic
```

## 2. Base de données MySQL 8

```sql
CREATE DATABASE senico_diagnostic CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'senico_app'@'localhost' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON senico_diagnostic.* TO 'senico_app'@'localhost';
FLUSH PRIVILEGES;
```

Les migrations **Flyway** (`backend/src/main/resources/db/migration`) s'exécutent automatiquement au démarrage du backend (schéma + données de démonstration : 1 admin, 3 groupes).

## 3. Backend (Spring Boot)

### Build

```bash
cd backend
mvn -q clean package -DskipTests
```

Le jar exécutable est produit dans `backend/target/diagnostic-strategique-1.0.0.jar`.

### Déploiement

```bash
sudo cp target/diagnostic-strategique-1.0.0.jar /opt/senico-diagnostic/backend/diagnostic-strategique.jar
sudo cp ../deploy/systemd/senico-diagnostic-backend.service /etc/systemd/system/
sudo cp ../.env.example /etc/senico-diagnostic/backend.env   # puis EDITER les valeurs reelles
sudo chmod 600 /etc/senico-diagnostic/backend.env
sudo chown senico:senico /opt/senico-diagnostic/backend/diagnostic-strategique.jar
sudo systemctl daemon-reload
sudo systemctl enable --now senico-diagnostic-backend
sudo systemctl status senico-diagnostic-backend
```

Vérifier la santé : `curl http://127.0.0.1:8080/actuator/health` → `{"status":"UP"}`.

Logs : `journalctl -u senico-diagnostic-backend -f` ou `/var/log/senico-diagnostic/backend.log`.

## 4. Frontend (Next.js via PM2)

### Build

```bash
cd frontend
cp .env.example .env.production
# Editer .env.production : NEXT_PUBLIC_API_BASE_URL / NEXT_PUBLIC_WS_BASE_URL vers le domaine public
npm ci
npm run build
```

### Déploiement

```bash
sudo mkdir -p /opt/senico-diagnostic/frontend
sudo cp -r .next node_modules public package.json .env.production /opt/senico-diagnostic/frontend/
sudo cp ../deploy/pm2/ecosystem.config.js /opt/senico-diagnostic/frontend/
sudo chown -R senico:senico /opt/senico-diagnostic/frontend

sudo -u senico pm2 start /opt/senico-diagnostic/frontend/ecosystem.config.js --env production
sudo -u senico pm2 save
pm2 startup systemd -u senico --hp /home/senico   # suivre l'instruction affichee (sudo ...)
```

Logs : `pm2 logs senico-diagnostic-frontend`.

## 5. Nginx (reverse proxy)

```bash
sudo cp deploy/nginx/senico-diagnostic.conf /etc/nginx/sites-available/
# Editer server_name avec le domaine reel
sudo ln -s /etc/nginx/sites-available/senico-diagnostic.conf /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

### HTTPS (Let's Encrypt)

```bash
sudo apt install -y certbot python3-certbot-nginx
sudo certbot --nginx -d diagnostic.senico.sn
```

Certbot modifie automatiquement la configuration Nginx pour rediriger en HTTPS et renouvelle le certificat via un timer systemd.

## 6. Variables d'environnement — référence

| Variable | Description | Composant |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | Connexion MySQL | Backend |
| `JWT_SECRET` | Clé de signature JWT (≥ 256 bits, aléatoire) | Backend |
| `JWT_ACCESS_EXPIRATION_MS` / `JWT_REFRESH_EXPIRATION_MS` | Durées de vie des tokens | Backend |
| `CORS_ALLOWED_ORIGINS` | Origines autorisées (domaine du frontend) | Backend |
| `SPRING_PROFILES_ACTIVE` | `dev` ou `prod` | Backend |
| `NEXT_PUBLIC_API_BASE_URL` | URL publique de l'API (`https://.../api/v1`) | Frontend |
| `NEXT_PUBLIC_WS_BASE_URL` | URL publique du endpoint WebSocket (`https://.../ws`) | Frontend |

## 7. Mise à jour (déploiement continu)

```bash
# Backend
cd backend && git pull && mvn -q clean package -DskipTests
sudo cp target/diagnostic-strategique-1.0.0.jar /opt/senico-diagnostic/backend/diagnostic-strategique.jar
sudo systemctl restart senico-diagnostic-backend

# Frontend
cd frontend && git pull && npm ci && npm run build
sudo cp -r .next node_modules public /opt/senico-diagnostic/frontend/
sudo -u senico pm2 restart senico-diagnostic-frontend
```

## 8. Sauvegardes

Sauvegarder quotidiennement la base MySQL (contient toutes les saisies) :

```bash
mysqldump --single-transaction -u senico_app -p senico_diagnostic | gzip > /var/backups/senico-diagnostic-$(date +%F).sql.gz
```

À planifier via cron ou systemd timer, avec rétention (ex. 30 jours) et copie hors-site.

## 9. Points de sécurité connus

- Le projet cible **Next.js 14** (exigence du cahier des charges). La dernière version patchée de la branche 14.x (`14.2.35`) est utilisée, mais `npm audit` signale des CVE dont le correctif n'existe que sur Next.js 16 (majeur, breaking change). À évaluer pour une montée de version ultérieure hors du périmètre initial.
- Mots de passe hashés bcrypt, JWT access/refresh, rate limiting sur `/api/v1/auth/login`, verrouillage des sections soumises côté backend (pas seulement UI), CORS restreint au domaine du frontend.
- Penser à changer tous les mots de passe de démonstration (`Admin@2027`, `Groupe@2027`) avant toute mise en production réelle.

## 10. Checklist de recette

- [ ] `curl https://<domaine>/actuator/health` → `UP`
- [ ] Connexion admin (`admin` / mot de passe changé) → tableau de bord temps réel accessible
- [ ] Connexion chef de groupe → seules les données de son groupe sont visibles
- [ ] Autosave d'une section (attendre ~20s ou cliquer « Enregistrer ») → indicateur « Enregistré à HH:MM »
- [ ] Soumission d'une section → statut passe à « Soumis », section verrouillée
- [ ] Admin : validation / renvoi pour révision d'une section soumise, avec commentaire
- [ ] Dashboard admin : la heatmap et le flux d'activité se mettent à jour sans rechargement après une action d'un chef de groupe (WebSocket) — couper le WS pour vérifier le repli en polling (15s)
- [ ] Export PDF (groupe), Export Word (groupe), Export Excel consolidé (admin) téléchargent des fichiers valides
- [ ] Vue comparative admin affiche côte à côte les réponses de plusieurs groupes sur une section
- [ ] Réinitialisation de mot de passe d'un chef de groupe fonctionne et le nouveau mot de passe permet la connexion
