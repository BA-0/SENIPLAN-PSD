# DEPLOYMENT-WINDOWS.md — SENIPLAN

Runbook de déploiement en production sur **Windows Server 2022** (avec expérience utilisateur), pour l'application SENIPLAN du Plan Stratégique 2027-2031 de SENICO. L'architecture est la même que sous Linux (voir [DEPLOYMENT.md](DEPLOYMENT.md)) : Nginx en reverse proxy, backend Spring Boot et frontend Next.js en services Windows (via **NSSM**), MySQL 8 en local.

```
Internet ──80/443──▶ Nginx (service SeniplanProxy)
                       ├── /api/, /ws/, /actuator/health ──▶ Backend  127.0.0.1:8080 (service SeniplanBackend) ──▶ MySQL 127.0.0.1:3306
                       └── /                              ──▶ Frontend 127.0.0.1:3000 (service SeniplanFrontend)
```

| Port | Usage | Exposition |
|---|---|---|
| 80, 443 | HTTP / HTTPS (Nginx) | ouvert depuis l'extérieur |
| 3389 | Bureau à distance (administration) | équipe projet ou VPN uniquement (à la charge du réseau) |
| 8080 | backend | 127.0.0.1 seulement, rien à ouvrir |
| 3000 | frontend | 127.0.0.1 seulement, rien à ouvrir |
| 3306 | MySQL | 127.0.0.1 seulement, rien à ouvrir |

Arborescence cible sur le serveur :

```
C:\seniplan\
  backend\seniplan-backend.jar
  backend\config\application.properties     configuration et secrets du backend
  frontend\                                 .next, node_modules, public, package.json
  deploy\                                   scripts de ce dossier (deploy\windows)
  config\backup.cnf                         identifiants du compte de sauvegarde
  logs\                                     journaux applicatifs et des services
  backups\                                  sauvegardes MySQL quotidiennes
C:\nginx\                                   Nginx pour Windows (conf\nginx.conf, certs\)
```

Dimensionnement : 2 vCPU, 4 Go de RAM, 40 Go de disque suffisent (le backend est limité à 1 Go de heap).

## 1. Logiciels à installer sur le serveur

| Logiciel | Version | Source | Remarque |
|---|---|---|---|
| Java | JDK 21 LTS | [Eclipse Temurin](https://adoptium.net) (msi) ou Microsoft Build of OpenJDK | noter le chemin de `java.exe` |
| Node.js | 20 LTS ou plus récent | [nodejs.org](https://nodejs.org) (msi) | installe `node.exe` dans `C:\Program Files\nodejs` |
| MySQL | 8.4 LTS | [MySQL Installer](https://dev.mysql.com/downloads/installer/) | type « Server only » ; le service se nomme `MySQL84` |
| Nginx | dernière stable pour Windows | [nginx.org](https://nginx.org/en/download.html) (zip) | dézipper dans `C:\nginx` (nginx.exe directement dedans) |
| NSSM | 2.24 | [nssm.cc](https://nssm.cc/download) | copier `win64\nssm.exe` dans `C:\Windows\System32` |

Git et Maven ne sont pas nécessaires sur le serveur : les livrables se construisent sur le poste de développement (section 4).

## 2. Pare-feu Windows

Seuls 80 et 443 sont à ouvrir, en plus du Bureau à distance déjà géré par l'équipe réseau :

```powershell
New-NetFirewallRule -DisplayName 'SENIPLAN HTTP/HTTPS' -Direction Inbound -Protocol TCP -LocalPort 80,443 -Action Allow
```

Backend et frontend n'écoutent que sur 127.0.0.1 (configuration ci-dessous) ; MySQL est limité à la boucle locale à la section 3. Le service `SeniplanProxy` (Nginx) est donc le seul point d'entrée.

## 3. Base de données MySQL 8

Limiter MySQL à la machine : dans `C:\ProgramData\MySQL\MySQL Server 8.4\my.ini`, section `[mysqld]`, ajouter `bind-address=127.0.0.1`, puis `Restart-Service MySQL84`.

Créer la base, le compte applicatif et le compte de sauvegarde (`mysql -u root -p`) :

```sql
CREATE DATABASE senico_diagnostic CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER 'senico_app'@'localhost' IDENTIFIED BY 'CHANGE_ME';
GRANT ALL PRIVILEGES ON senico_diagnostic.* TO 'senico_app'@'localhost';
CREATE USER 'senico_backup'@'localhost' IDENTIFIED BY 'CHANGE_ME_TOO';
GRANT SELECT, SHOW VIEW, TRIGGER, EVENT, LOCK TABLES ON senico_diagnostic.* TO 'senico_backup'@'localhost';
FLUSH PRIVILEGES;
```

Deux options pour le contenu :

- **Base vide** : au premier démarrage, Flyway crée le schéma et les données de démonstration (compte `admin`, mots de passe `Admin@2027` / `Groupe@2027` **à changer aussitôt**).
- **Reprise des saisies déjà faites** (recommandé avant la retraite stratégique) : exporter la base du poste de développement, puis l'importer sur le serveur. Le dump contient `flyway_schema_history` : le backend ne rejoue donc pas les migrations ni ne recrée les données de démonstration.

```powershell
# Sur le poste de developpement (WAMP)
& 'C:\wamp64\bin\mysql\mysql8.4.7\bin\mysqldump.exe' -u root --single-transaction --routines --triggers --no-tablespaces `
    --default-character-set=utf8mb4 --result-file=C:\Temp\senico_diagnostic.sql senico_diagnostic

# Sur le serveur, apres copie du fichier
& 'C:\Program Files\MySQL\MySQL Server 8.4\bin\mysql.exe' -u root -p --default-character-set=utf8mb4 senico_diagnostic `
    -e "source C:/seniplan/senico_diagnostic.sql"
```

Toujours `--result-file` et `-e "source …"` plutôt que `>` et `<` : PowerShell réencoderait le fichier et n'accepte pas `<`.

## 4. Construire les livrables (sur le poste de développement)

### Backend

```powershell
cd backend
mvn -q clean package -DskipTests
Copy-Item target\diagnostic-strategique-1.0.0.jar C:\Temp\seniplan-backend.jar
```

### Frontend

Les URL publiques sont **figées au moment du build** (`NEXT_PUBLIC_*`) : renseigner `.env.production` avant `npm run build`, avec le domaine réel.

```powershell
cd frontend
Copy-Item .env.example .env.production
# Editer .env.production :
#   NEXT_PUBLIC_API_BASE_URL=https://seniplan.senico.sn/api/v1
#   NEXT_PUBLIC_WS_BASE_URL=https://seniplan.senico.sn/ws
npm ci
npm run build
Compress-Archive -Path .next, node_modules, public, package.json -DestinationPath C:\Temp\seniplan-frontend.zip
```

L'archive fait plusieurs centaines de Mo (node_modules). Si le serveur a accès à Internet, on peut n'y copier que `.next`, `public` et `package.json`, puis exécuter `npm ci --omit=dev` dans `C:\seniplan\frontend`.

À copier sur le serveur (Bureau à distance, copier-coller ou lecteur partagé) : `seniplan-backend.jar`, `seniplan-frontend.zip`, le dossier `deploy\windows` du dépôt et, le cas échéant, `senico_diagnostic.sql`.

## 5. Installer sur le serveur

Dans une console PowerShell **exécutée en tant qu'administrateur** :

```powershell
New-Item -ItemType Directory -Force -Path C:\seniplan\backend\config, C:\seniplan\frontend, C:\seniplan\deploy, C:\seniplan\config, C:\seniplan\logs, C:\seniplan\backups
Copy-Item C:\Temp\seniplan-backend.jar C:\seniplan\backend\
Expand-Archive C:\Temp\seniplan-frontend.zip -DestinationPath C:\seniplan\frontend
Copy-Item C:\Temp\windows\* C:\seniplan\deploy\
Copy-Item C:\seniplan\deploy\backend-config.properties.example C:\seniplan\backend\config\application.properties
```

Éditer `C:\seniplan\backend\config\application.properties` : `DB_PASSWORD`, `CORS_ALLOWED_ORIGINS` (domaine public en https) et `JWT_SECRET`, généré **sur le serveur** et jamais transmis par mail :

```powershell
[Convert]::ToBase64String((1..48 | ForEach-Object { Get-Random -Maximum 256 }) -as [byte[]])
```

Le fichier suit les mêmes noms que `backend\.env.example` ; Spring Boot le charge automatiquement depuis le dossier `config\` situé à côté du jar, et ses valeurs remplacent celles empaquetées.

Nginx : copier la configuration et adapter le nom de domaine.

```powershell
Copy-Item C:\seniplan\deploy\nginx.conf C:\nginx\conf\nginx.conf
# Editer server_name dans C:\nginx\conf\nginx.conf
Set-Location C:\nginx; .\nginx.exe -t
```

Enregistrer et démarrer les trois services :

```powershell
Set-Location C:\seniplan\deploy
.\install-services.ps1 -JavaExe 'C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot\bin\java.exe'
```

Paramètres utiles : `-NodeExe`, `-NginxDir`, `-MySqlService` (si le service ne s'appelle pas `MySQL84`), `-SkipNginx` (reverse proxy géré par IIS). Le script restreint les droits du fichier de configuration, enregistre les services en démarrage automatique (le backend dépend de MySQL), avec redémarrage en cas de plantage, et journalise dans `C:\seniplan\logs`. Il peut être relancé après toute modification : les services existants sont recréés.

Vérifier :

```powershell
Invoke-RestMethod http://127.0.0.1:8080/actuator/health          # status : UP (20 a 40 s apres le demarrage)
Invoke-WebRequest http://127.0.0.1:3000 -UseBasicParsing           # StatusCode : 200
Invoke-RestMethod http://localhost/actuator/health                 # via Nginx
Get-Service Seniplan*
```

## 6. HTTPS

Nginx lit un certificat et sa clé au format PEM dans `C:\nginx\certs` (`seniplan.senico.sn-chain.pem` : certificat + chaîne ; `seniplan.senico.sn-key.pem` : clé privée).

- **Certificat fourni par SENICO** (PKI interne ou autorité commerciale) : demander la livraison en PEM. Depuis un `.pfx`, la conversion se fait avec OpenSSL (`openssl pkcs12 -in cert.pfx -nokeys -out chain.pem` et `-nocerts -nodes -out key.pem`), disponible avec Git for Windows sur le poste de développement.
- **Let's Encrypt** (le domaine doit être joignable depuis Internet sur le port 80) : [win-acme](https://www.win-acme.com), qui renouvelle automatiquement via une tâche planifiée.

  ```powershell
  .\wacs.exe --source manual --host seniplan.senico.sn --validation filesystem --webroot C:\nginx\html `
      --store pemfiles --pemfilespath C:\nginx\certs --installation script --script "C:\seniplan\deploy\reload-nginx.cmd"
  ```

  où `reload-nginx.cmd` contient `cd /d C:\nginx && nginx.exe -s reload`. La configuration fournie sert déjà `/.well-known/acme-challenge/` depuis `C:\nginx\html`.

Une fois les fichiers en place : dans `nginx.conf`, décommenter le bloc `server { listen 443 ssl; … }` (y recopier les `location` du bloc 80) et la ligne `return 301 https://…` du bloc 80, puis `.\nginx.exe -t` et `nssm restart SeniplanProxy`. Vérifier que `CORS_ALLOWED_ORIGINS` et les `NEXT_PUBLIC_*` du build pointent bien sur `https://`.

## 7. Recette

Dérouler la checklist de [DEPLOYMENT.md, section 10](DEPLOYMENT.md#10-checklist-de-recette) sur l'URL publique : santé, connexion admin et chef de groupe, autosave, soumission, validation, mise à jour temps réel du tableau de bord (WebSocket à travers Nginx), exports PDF / Word / Excel.

## 8. Mise à jour

```powershell
# Backend : nouveau jar
Stop-Service SeniplanBackend
Copy-Item C:\Temp\seniplan-backend.jar C:\seniplan\backend\seniplan-backend.jar -Force
Start-Service SeniplanBackend

# Frontend : nouveau build (archive construite comme en section 4)
Stop-Service SeniplanFrontend
Remove-Item C:\seniplan\frontend\.next -Recurse -Force
Expand-Archive C:\Temp\seniplan-frontend.zip -DestinationPath C:\seniplan\frontend -Force
Start-Service SeniplanFrontend
```

Les migrations Flyway du nouveau jar s'appliquent au démarrage. Faire une sauvegarde (section 9) avant toute mise à jour du backend.

## 9. Sauvegardes

Créer `C:\seniplan\config\backup.cnf` (droits administrateurs et SYSTEM uniquement) :

```ini
[client]
user=senico_backup
password=CHANGE_ME_TOO
```

```powershell
icacls C:\seniplan\config\backup.cnf /inheritance:r /grant:r '*S-1-5-32-544:F' '*S-1-5-18:F'
# Test manuel
powershell -NoProfile -ExecutionPolicy Bypass -File C:\seniplan\deploy\backup-mysql.ps1
# Tous les jours a 02:00
Register-ScheduledTask -TaskName 'SENIPLAN - sauvegarde MySQL' `
    -Action (New-ScheduledTaskAction -Execute 'powershell.exe' -Argument '-NoProfile -ExecutionPolicy Bypass -File C:\seniplan\deploy\backup-mysql.ps1') `
    -Trigger (New-ScheduledTaskTrigger -Daily -At 02:00) `
    -Principal (New-ScheduledTaskPrincipal -UserId 'SYSTEM' -RunLevel Highest)
```

Les archives `C:\seniplan\backups\senico_diagnostic-AAAA-MM-JJ.zip` sont conservées 30 jours ; à recopier hors du serveur (partage réseau, stockage cloud). Restauration : dézipper puis `mysql.exe … -e "source C:/…/senico_diagnostic-AAAA-MM-JJ.sql"` comme à la section 3.

## 10. Journaux et dépannage

| Où | Quoi |
|---|---|
| `C:\seniplan\logs\backend.log` | journal applicatif Spring Boot |
| `C:\seniplan\logs\SeniplanBackend-stderr.log` | erreurs de démarrage du backend (configuration, MySQL) |
| `C:\seniplan\logs\SeniplanFrontend-*.log` | Next.js |
| `C:\nginx\logs\seniplan.access.log`, `error.log` | Nginx |
| Observateur d'événements › Système | démarrage / arrêt des services (source `nssm`) |

- `nssm status SeniplanBackend`, `nssm edit SeniplanBackend` (fenêtre de paramétrage), `nssm restart SeniplanBackend`.
- Backend arrêté aussitôt lancé : lire `SeniplanBackend-stderr.log` ; causes habituelles : mot de passe MySQL, service MySQL arrêté, `JWT_SECRET` trop court.
- `SSL connection required` côté MySQL : le profil `prod` impose SSL (`useSSL=true`) ; MySQL 8 génère ses certificats à l'installation, vérifier que `ssl` n'a pas été désactivé dans `my.ini`.
- Nginx renvoie 502 : le service visé (backend ou frontend) est arrêté ou encore en cours de démarrage.
- Changer le compte des services : relancer `install-services.ps1 -ServiceAccount 'SENICO\svc-seniplan'` (NSSM demande alors le mot de passe via `nssm set … ObjectName`).

## 11. Avant la retraite stratégique

- [ ] Base reprise du poste de développement ou mots de passe de démonstration changés
- [ ] `JWT_SECRET` généré sur le serveur, `DB_PASSWORD` propre à la production
- [ ] `CORS_ALLOWED_ORIGINS` et `NEXT_PUBLIC_*` (build frontend) sur le domaine public en https
- [ ] HTTPS actif, redirection 80 → 443
- [ ] Ports 3000, 8080, 3306 injoignables depuis un autre poste (`Test-NetConnection <serveur> -Port 8080` → False)
- [ ] Sauvegarde planifiée, un cycle sauvegarde / restauration testé
- [ ] Recette de la section 7 déroulée avec un compte admin et un compte chef de groupe
