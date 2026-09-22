<#
.SYNOPSIS
    Sauvegarde quotidienne de la base MySQL de SENIPLAN (toutes les saisies des directions).

.DESCRIPTION
    Produit C:\seniplan\backups\senico_diagnostic-AAAA-MM-JJ.zip et supprime les sauvegardes plus
    anciennes que la retention. Les identifiants MySQL sont lus dans un fichier d'options
    (C:\seniplan\config\backup.cnf, droits restreints) :

        [client]
        user=senico_backup
        password=CHANGE_ME

    Planification (console administrateur), tous les jours a 02:00 :

        Register-ScheduledTask -TaskName 'SENIPLAN - sauvegarde MySQL' `
            -Action (New-ScheduledTaskAction -Execute 'powershell.exe' `
                -Argument '-NoProfile -ExecutionPolicy Bypass -File C:\seniplan\deploy\backup-mysql.ps1') `
            -Trigger (New-ScheduledTaskTrigger -Daily -At 02:00) `
            -Principal (New-ScheduledTaskPrincipal -UserId 'SYSTEM' -RunLevel Highest)

    Copier ensuite les .zip hors du serveur (partage reseau, stockage cloud) : une sauvegarde sur le
    serveur seul ne protege pas de la perte du serveur.

.PARAMETER MySqlDump
    Chemin de mysqldump.exe.
.PARAMETER OptionsFile
    Fichier d'options MySQL contenant [client] user / password.
.PARAMETER Database
    Base a sauvegarder.
.PARAMETER BackupDir
    Dossier de destination.
.PARAMETER RetentionDays
    Nombre de jours de sauvegardes conservees.
#>
[CmdletBinding()]
param(
    [string]$MySqlDump = "$env:ProgramFiles\MySQL\MySQL Server 8.4\bin\mysqldump.exe",
    [string]$OptionsFile = 'C:\seniplan\config\backup.cnf',
    [string]$Database = 'senico_diagnostic',
    [string]$BackupDir = 'C:\seniplan\backups',
    [int]$RetentionDays = 30
)

$ErrorActionPreference = 'Stop'

if (-not (Test-Path $MySqlDump)) { throw "mysqldump.exe introuvable : $MySqlDump" }
if (-not (Test-Path $OptionsFile)) { throw "Fichier d'options MySQL introuvable : $OptionsFile" }
New-Item -ItemType Directory -Force -Path $BackupDir | Out-Null

$stamp = Get-Date -Format 'yyyy-MM-dd'
$sql = Join-Path $BackupDir "$Database-$stamp.sql"
$zip = Join-Path $BackupDir "$Database-$stamp.zip"

# --result-file plutot qu'une redirection PowerShell, qui reencoderait le dump (BOM, UTF-16).
# --defaults-extra-file doit rester la premiere option. --no-tablespaces evite d'exiger le privilege
# global PROCESS pour le compte de sauvegarde.
& $MySqlDump "--defaults-extra-file=$OptionsFile" --single-transaction --routines --triggers --no-tablespaces `
    --default-character-set=utf8mb4 "--result-file=$sql" $Database
if ($LASTEXITCODE -ne 0) {
    Remove-Item $sql -ErrorAction SilentlyContinue
    throw "mysqldump a echoue (code $LASTEXITCODE)"
}

if (Test-Path $zip) { Remove-Item $zip -Force }
Compress-Archive -Path $sql -DestinationPath $zip
Remove-Item $sql

Get-ChildItem $BackupDir -Filter "$Database-*.zip" |
    Where-Object { $_.LastWriteTime -lt (Get-Date).AddDays(-$RetentionDays) } |
    Remove-Item -Force

Write-Host "Sauvegarde : $zip ($([math]::Round((Get-Item $zip).Length / 1KB)) Ko)"
