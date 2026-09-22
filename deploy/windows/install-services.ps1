<#
.SYNOPSIS
    Enregistre les services Windows de SENIPLAN avec NSSM : backend (Spring Boot), frontend (Next.js)
    et reverse proxy (Nginx).

.DESCRIPTION
    A lancer dans une console PowerShell "Executer en tant qu'administrateur", une fois les livrables
    deposes dans C:\seniplan (cf. DEPLOYMENT-WINDOWS.md). Le script peut etre relance : un service deja
    enregistre est arrete, supprime et recree avec les parametres a jour.

    Services crees :
      SeniplanBackend   java -jar seniplan-backend.jar        (127.0.0.1:8080)
      SeniplanFrontend  node next start -H 127.0.0.1 -p 3000  (127.0.0.1:3000)
      SeniplanProxy     nginx.exe                             (80 / 443)

.PARAMETER Root
    Dossier d'installation (par defaut C:\seniplan), contenant backend\, frontend\ et logs\.
.PARAMETER JavaExe
    Chemin de java.exe (JDK ou JRE 21).
.PARAMETER NodeExe
    Chemin de node.exe (Node.js 20 LTS ou plus recent).
.PARAMETER NginxDir
    Dossier de Nginx pour Windows (contient nginx.exe et conf\nginx.conf).
.PARAMETER MySqlService
    Nom du service MySQL, dont le backend depend (par defaut MySQL84 ; verifier avec Get-Service MySQL*).
.PARAMETER ServiceAccount
    Compte d'execution des services backend et frontend. Par defaut le compte a droits reduits
    "NT AUTHORITY\LocalService" ; le script lui donne lecture sur $Root et ecriture sur $Root\logs.
.PARAMETER SkipNginx
    Ne pas enregistrer le service Nginx (reverse proxy gere autrement, par exemple IIS).

.EXAMPLE
    .\install-services.ps1
.EXAMPLE
    .\install-services.ps1 -JavaExe "C:\Program Files\Eclipse Adoptium\jdk-21.0.4.7-hotspot\bin\java.exe" -MySqlService MySQL80
#>
[CmdletBinding()]
param(
    [string]$Root = 'C:\seniplan',
    [string]$JavaExe = "$env:ProgramFiles\Java\jdk-21\bin\java.exe",
    [string]$NodeExe = "$env:ProgramFiles\nodejs\node.exe",
    [string]$NginxDir = 'C:\nginx',
    [string]$MySqlService = 'MySQL84',
    [string]$ServiceAccount = 'NT AUTHORITY\LocalService',
    [switch]$SkipNginx
)

$ErrorActionPreference = 'Stop'

function Assert-Path([string]$Path, [string]$What) {
    if (-not (Test-Path $Path)) {
        throw "$What introuvable : $Path"
    }
}

function Invoke-Nssm {
    # nssm renvoie un code non nul en cas d'erreur, sans lever d'exception PowerShell.
    & nssm @args
    if ($LASTEXITCODE -ne 0) {
        throw "nssm $($args -join ' ') : echec (code $LASTEXITCODE)"
    }
}

function Invoke-Icacls {
    & icacls @args | Out-Null
    if ($LASTEXITCODE -ne 0) {
        throw "icacls $($args -join ' ') : echec (code $LASTEXITCODE)"
    }
}

function Resolve-Sid([string]$Account) {
    # Comptes integres : SID connus, sans dependre du nom localise
    switch -Regex ($Account) {
        '^NT AUTHORITY\\LocalService$'   { return '*S-1-5-19' }
        '^NT AUTHORITY\\NetworkService$' { return '*S-1-5-20' }
        '^(NT AUTHORITY\\)?(LocalSystem|SYSTEM)$' { return '*S-1-5-18' }
    }
    $sid = (New-Object System.Security.Principal.NTAccount($Account)).Translate([System.Security.Principal.SecurityIdentifier])
    return '*' + $sid.Value
}

function Register-SeniplanService {
    param(
        [string]$Name,
        [string]$DisplayName,
        [string]$Description,
        [string]$Program,
        [string]$Arguments,
        [string]$WorkingDirectory,
        [string]$Account,
        [string[]]$DependsOn = @(),
        [string[]]$Environment = @()
    )

    if (Get-Service -Name $Name -ErrorAction SilentlyContinue) {
        Write-Host "  $Name existe deja : arret et suppression avant recreation"
        & nssm stop $Name | Out-Null
        Invoke-Nssm remove $Name confirm
    }

    Invoke-Nssm install $Name $Program
    if ($Arguments) { Invoke-Nssm set $Name AppParameters $Arguments }
    Invoke-Nssm set $Name AppDirectory $WorkingDirectory
    Invoke-Nssm set $Name DisplayName $DisplayName
    Invoke-Nssm set $Name Description $Description
    Invoke-Nssm set $Name Start SERVICE_AUTO_START
    if ($Account) { Invoke-Nssm set $Name ObjectName $Account }
    if ($DependsOn.Count -gt 0) { Invoke-Nssm set $Name DependOnService @DependsOn }
    if ($Environment.Count -gt 0) { Invoke-Nssm set $Name AppEnvironmentExtra @Environment }

    # Sorties console dans $Root\logs, avec rotation a 10 Mo
    Invoke-Nssm set $Name AppStdout "$Root\logs\$Name-stdout.log"
    Invoke-Nssm set $Name AppStderr "$Root\logs\$Name-stderr.log"
    Invoke-Nssm set $Name AppRotateFiles 1
    Invoke-Nssm set $Name AppRotateOnline 1
    Invoke-Nssm set $Name AppRotateBytes 10485760

    # Redemarrage automatique apres un plantage, 5 s plus tard
    Invoke-Nssm set $Name AppExit Default Restart
    Invoke-Nssm set $Name AppRestartDelay 5000

    Write-Host "  $Name enregistre"
}

# ---- Verifications ----
if (-not (Get-Command nssm -ErrorAction SilentlyContinue)) {
    throw "nssm.exe introuvable dans le PATH. Telecharger NSSM (https://nssm.cc) et copier nssm.exe dans C:\Windows\System32."
}
Assert-Path $JavaExe 'java.exe'
Assert-Path $NodeExe 'node.exe'
Assert-Path "$Root\backend\seniplan-backend.jar" 'Jar du backend'
Assert-Path "$Root\backend\config\application.properties" 'Configuration du backend'
Assert-Path "$Root\frontend\node_modules\next\dist\bin\next" 'Frontend construit (node_modules\next)'
Assert-Path "$Root\frontend\.next" 'Frontend construit (.next)'
if (-not $SkipNginx) {
    Assert-Path "$NginxDir\nginx.exe" 'nginx.exe'
    Assert-Path "$NginxDir\conf\nginx.conf" 'Configuration Nginx'
}

$backendDependsOn = @()
if (Get-Service -Name $MySqlService -ErrorAction SilentlyContinue) {
    $backendDependsOn = @($MySqlService)
} else {
    Write-Warning "Service MySQL '$MySqlService' introuvable : le backend ne sera pas declare dependant de MySQL (parametre -MySqlService)."
}

# ---- Dossiers et droits ----
# icacls recoit des SID (*S-1-5-...) : les noms des comptes integres changent avec la langue de Windows.
$serviceSid = Resolve-Sid $ServiceAccount
$adminsSid = '*S-1-5-32-544'   # Administrateurs
$systemSid = '*S-1-5-18'       # SYSTEM

New-Item -ItemType Directory -Force -Path "$Root\logs" | Out-Null
Write-Host "Droits sur $Root pour $ServiceAccount"
Invoke-Icacls $Root /grant "${serviceSid}:(OI)(CI)RX" /T /Q
Invoke-Icacls "$Root\logs" /grant "${serviceSid}:(OI)(CI)M" /Q
# Next.js ecrit son cache d'images et de rendu dans .next
Invoke-Icacls "$Root\frontend\.next" /grant "${serviceSid}:(OI)(CI)M" /T /Q
# La configuration contient les secrets : administrateurs, SYSTEM et le compte des services uniquement
Invoke-Icacls "$Root\backend\config\application.properties" /inheritance:r /grant:r "${adminsSid}:F" "${systemSid}:F" "${serviceSid}:R" /Q

# ---- Services ----
Write-Host 'Enregistrement des services'
Register-SeniplanService -Name 'SeniplanBackend' `
    -DisplayName 'SENIPLAN - Backend (Spring Boot)' `
    -Description 'API et WebSocket de SENIPLAN, application du Plan Strategique 2027-2031 de SENICO.' `
    -Program $JavaExe `
    -Arguments "-Xms256m -Xmx1024m -Dfile.encoding=UTF-8 -jar `"$Root\backend\seniplan-backend.jar`"" `
    -WorkingDirectory "$Root\backend" `
    -Account $ServiceAccount `
    -DependsOn $backendDependsOn

Register-SeniplanService -Name 'SeniplanFrontend' `
    -DisplayName 'SENIPLAN - Frontend (Next.js)' `
    -Description 'Interface web de SENIPLAN.' `
    -Program $NodeExe `
    -Arguments 'node_modules\next\dist\bin\next start -H 127.0.0.1 -p 3000' `
    -WorkingDirectory "$Root\frontend" `
    -Account $ServiceAccount `
    -Environment @('NODE_ENV=production')

if (-not $SkipNginx) {
    # Nginx tourne sous le compte par defaut (LocalSystem) pour ouvrir les ports 80/443 et lire ses certificats.
    Register-SeniplanService -Name 'SeniplanProxy' `
        -DisplayName 'SENIPLAN - Reverse proxy (Nginx)' `
        -Description 'Reverse proxy HTTP/HTTPS de SENIPLAN vers le frontend et le backend.' `
        -Program "$NginxDir\nginx.exe" `
        -Arguments '' `
        -WorkingDirectory $NginxDir `
        -Account ''
    # nginx.exe ne repond ni a Ctrl+C ni a la fermeture de fenetre : NSSM termine directement l'arborescence de processus.
    Invoke-Nssm set SeniplanProxy AppStopMethodSkip 6
}

# ---- Demarrage ----
Write-Host 'Demarrage'
foreach ($name in @('SeniplanBackend', 'SeniplanFrontend') + $(if ($SkipNginx) { @() } else { @('SeniplanProxy') })) {
    Start-Service -Name $name
    Write-Host "  $name : $((Get-Service $name).Status)"
}

Write-Host ''
Write-Host 'Verifier dans une minute (le backend met 20 a 40 s a demarrer) :'
Write-Host '  Invoke-RestMethod http://127.0.0.1:8080/actuator/health   -> status : UP'
Write-Host '  Invoke-WebRequest http://127.0.0.1:3000 -UseBasicParsing   -> StatusCode : 200'
Write-Host "  Journaux : $Root\logs\"
