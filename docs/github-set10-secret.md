# GitHub Secret For Set10 Plugin Build

Plugin workflow uses secret:

- `SET10_API_JAR_BASE64`

## Prepare secret value (PowerShell)

```powershell
cd E:\Projects\SbgPosAgent\marking-auto-km
.\scripts\encode-set10-jar.ps1 -JarPath "E:\Projects\SbgPosAgent\knowledge_base\set10\sources\set10pos-api-0.0.0_DO_NOT_CHANGE_VERSION-SNAPSHOT.jar"
```

The script creates:

- `set10_api_jar.base64.txt`

## Add secret in GitHub

1. Open repository: `FuzzyDi/SBGMarkService`
2. Go to `Settings -> Secrets and variables -> Actions -> New repository secret`
3. Name: `SET10_API_JAR_BASE64`
4. Value: full content of `set10_api_jar.base64.txt`
5. Save

After that, workflow `.github/workflows/plugin-build.yml` builds Set10 plugin jar instead of skipping.
