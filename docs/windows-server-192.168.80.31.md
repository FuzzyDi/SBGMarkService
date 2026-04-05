# Deploy sbg-marking-server on Windows Server (192.168.80.31)

This guide assumes Set Retail 10 Centrum is already running on the same host `192.168.80.31`.

## 1. Prerequisites on Windows Server

- Java 17 installed (`java -version`)
- PostgreSQL reachable from the server (local or remote)
- Open inbound port for API (default `8080`)

Example firewall rule (run as Administrator):

```powershell
New-NetFirewallRule -DisplayName "SBG Marking Server 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow
```

## 2. Prepare deployment bundle

On build machine (repo root):

```powershell
.\scripts\windows\build-server-bundle.ps1
```

This creates/updates:

- `deploy\windows-server\sbg-marking-server.jar`
- `deploy\windows-server\marking-server.env` (copied from example if missing)

## 3. Copy bundle to Windows Server

Copy folder `deploy\windows-server` to server path, for example:

- `C:\SBG\marking-server`

## 4. Configure environment file

On Windows Server, edit:

- `C:\SBG\marking-server\marking-server.env`

Minimum required values:

- `SBG_MARKING_DB_URL`
- `SBG_MARKING_DB_USER`
- `SBG_MARKING_DB_PASSWORD`
- `SERVER_PORT` (default `8080`)

If security is handled externally, keep:

- `SBG_MARKING_AUTH_ENABLED=false`

## 5. Start / stop / status

Run in PowerShell as Administrator:

```powershell
cd C:\SBG\marking-server
.\start-server.ps1
.\status-server.ps1
```

Stop:

```powershell
.\stop-server.ps1
```

Foreground mode (for diagnostics):

```powershell
.\start-server.ps1 -Foreground
```

Logs:

- `C:\SBG\marking-server\logs\server.out.log`
- `C:\SBG\marking-server\logs\server.err.log`

## 6. Autostart after reboot (Scheduled Task)

Install startup task:

```powershell
cd C:\SBG\marking-server
.\install-startup-task.ps1
```

Remove startup task:

```powershell
.\uninstall-startup-task.ps1
```

## 7. Health check from network

From another machine:

```powershell
Invoke-RestMethod -Method Get -Uri "http://192.168.80.31:8080/actuator/health"
```

Expected: `status = UP`.

## 8. Set Retail 10 plugin endpoint

In Set Retail 10 plugin service options, set:

- `marking.service.url = http://192.168.80.31:8080`
- `marking.service.connect.timeout.ms = 3000` (or higher)
- `marking.service.read.timeout.ms = 5000` (or higher)

Then restart plugin/service in Set Retail environment.
