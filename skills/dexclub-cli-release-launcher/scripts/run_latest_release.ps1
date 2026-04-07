$ErrorActionPreference = "Stop"
$RemoteFailureLimit = 2

function Get-UnsupportedMessage {
    return -join ([char[]](0x8BE5,0x20,0x73,0x6B,0x69,0x6C,0x6C,0x20,0x65E0,0x6CD5,0x5728,0x5F53,0x524D,0x7CFB,0x7EDF,0x4E0A,0x4F7F,0x7528))
}

function Write-UnsupportedPlatform {
    [Console]::Error.WriteLine((Get-UnsupportedMessage))
}

function Get-Repo {
    if ($env:DEXCLUB_CLI_GITHUB_REPO) {
        return $env:DEXCLUB_CLI_GITHUB_REPO
    }
    return "DexClub/dexclub-cli"
}

function Get-CacheRoot {
    if ($env:DEXCLUB_CLI_CACHE_DIR) {
        return [System.IO.Path]::GetFullPath($env:DEXCLUB_CLI_CACHE_DIR)
    }

    if ($env:LOCALAPPDATA) {
        return [System.IO.Path]::Combine($env:LOCALAPPDATA, "dexclub-cli", "releases")
    }

    if ($env:USERPROFILE) {
        return [System.IO.Path]::Combine($env:USERPROFILE, ".cache", "dexclub-cli", "releases")
    }

    return [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "dexclub-cli", "releases")
}

function Get-StateDir {
    return [System.IO.Path]::Combine((Get-CacheRoot), ".state")
}

function Get-RepoStateKey {
    return ((Get-Repo) -replace '[^A-Za-z0-9._-]', '_')
}

function Get-SelectedTagFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform
    )

    return [System.IO.Path]::Combine((Get-StateDir), "$(Get-RepoStateKey).$Platform.tag")
}

function Set-SelectedTag {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform,
        [Parameter(Mandatory = $true)]
        [string]$Tag
    )

    New-Item -ItemType Directory -Force -Path (Get-StateDir) | Out-Null
    Set-Content -LiteralPath (Get-SelectedTagFile -Platform $Platform) -Value $Tag -NoNewline
}

function Get-SelectedTag {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform
    )

    $tagFile = Get-SelectedTagFile -Platform $Platform
    if (Test-Path $tagFile) {
        return (Get-Content -LiteralPath $tagFile -TotalCount 1).Trim()
    }

    return $null
}

function Get-RemoteFailureFile {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform
    )

    return [System.IO.Path]::Combine((Get-StateDir), "$(Get-RepoStateKey).$Platform.remote_failures")
}

function Get-RemoteFailureCount {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform
    )

    $failureFile = Get-RemoteFailureFile -Platform $Platform
    if (Test-Path $failureFile) {
        $value = (Get-Content -LiteralPath $failureFile -TotalCount 1).Trim()
        if ($value) {
            return [int]$value
        }
    }

    return 0
}

function Set-RemoteFailureCount {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform,
        [Parameter(Mandatory = $true)]
        [int]$Count
    )

    New-Item -ItemType Directory -Force -Path (Get-StateDir) | Out-Null
    Set-Content -LiteralPath (Get-RemoteFailureFile -Platform $Platform) -Value $Count -NoNewline
}

function Reset-RemoteFailures {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform
    )

    Set-RemoteFailureCount -Platform $Platform -Count 0
}

function Write-RemoteFailureAndExit {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform,
        [Parameter(Mandatory = $true)]
        [string]$Message
    )

    $count = (Get-RemoteFailureCount -Platform $Platform) + 1
    Set-RemoteFailureCount -Platform $Platform -Count $count
    [Console]::Error.WriteLine($Message)

    if ($count -lt $RemoteFailureLimit) {
        [Console]::Error.WriteLine("Remote access failed ($count/$RemoteFailureLimit). Retry after repository or network access is restored.")
    }
    else {
        [Console]::Error.WriteLine("Remote access failed ($count/$RemoteFailureLimit). Further remote checks are now disabled until --reset-remote-failures is used.")
    }

    exit 30
}

function Assert-RemoteAccessAllowed {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform,
        [Parameter(Mandatory = $true)]
        [string]$Context
    )

    $count = Get-RemoteFailureCount -Platform $Platform
    if ($count -ge $RemoteFailureLimit) {
        if ($Context -eq "no-cache") {
            [Console]::Error.WriteLine("No compatible local cache is available, and remote access is disabled after $RemoteFailureLimit failed attempts. Use --reset-remote-failures after connectivity is restored.")
        }
        else {
            [Console]::Error.WriteLine("Remote access is disabled after $RemoteFailureLimit failed attempts. Use --reset-remote-failures after connectivity is restored.")
        }
        exit 30
    }
}

function Get-Platform {
    if (-not [System.Runtime.InteropServices.RuntimeInformation]::IsOSPlatform([System.Runtime.InteropServices.OSPlatform]::Windows)) {
        Write-UnsupportedPlatform
        exit 20
    }

    $arch = [System.Runtime.InteropServices.RuntimeInformation]::OSArchitecture
    switch ($arch.ToString().ToLowerInvariant()) {
        "x64" { return "windows-x64" }
        "arm64" { return "windows-arm64" }
        default {
            Write-UnsupportedPlatform
            exit 20
        }
    }
}

function Invoke-GitHubRequest {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [string]$OutFile
    )

    $headers = @{ "User-Agent" = "dexclub-cli-release-launcher" }
    $params = @{
        Uri = $Uri
        Headers = $headers
        UseBasicParsing = $true
    }

    if ($OutFile) {
        $params["OutFile"] = $OutFile
    }

    return Invoke-WebRequest @params
}

function Get-LatestReleaseTagFromApi {
    $repo = Get-Repo
    $response = Invoke-GitHubRequest -Uri "https://api.github.com/repos/$repo/releases/latest"
    $payload = $response.Content | ConvertFrom-Json
    if (-not $payload.tag_name) {
        throw "Missing tag_name in latest release API response."
    }
    return $payload.tag_name
}

function Get-LatestReleaseTag {
    return Get-LatestReleaseTagFromApi
}

function Download-File {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Uri,
        [Parameter(Mandatory = $true)]
        [string]$OutputPath
    )

    $tmpPath = "$OutputPath.tmp"
    if (Test-Path $tmpPath) {
        Remove-Item -Force $tmpPath
    }

    Invoke-GitHubRequest -Uri $Uri -OutFile $tmpPath | Out-Null
    Move-Item -Force $tmpPath $OutputPath
}

function Get-AssetInfo {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform,
        [Parameter(Mandatory = $true)]
        [string]$Tag
    )

    $cacheRoot = Get-CacheRoot
    $assetBase = "dexclub-cli-$Platform"
    $releaseDir = Join-Path $cacheRoot $Tag
    $zipPath = Join-Path $releaseDir "$assetBase.zip"
    $shaPath = Join-Path $releaseDir "$assetBase.sha256"
    $extractDir = Join-Path $releaseDir $assetBase
    $extractTmp = "$extractDir.tmp"
    $digestMarker = Join-Path $extractDir ".archive-sha256"

    return @{
        AssetBase = $assetBase
        ReleaseDir = $releaseDir
        ZipPath = $zipPath
        ShaPath = $shaPath
        ExtractDir = $extractDir
        ExtractTmp = $extractTmp
        DigestMarker = $digestMarker
    }
}

function Test-CachedAsset {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform,
        [Parameter(Mandatory = $true)]
        [string]$Tag
    )

    $assetInfo = Get-AssetInfo -Platform $Platform -Tag $Tag
    if ((Test-Path $assetInfo.ZipPath) -and (Test-Path $assetInfo.ShaPath)) {
        return $true
    }

    if ((Test-Path $assetInfo.ExtractDir) -and (Test-Path $assetInfo.DigestMarker)) {
        return $true
    }

    return $false
}

function Get-TagSortKey {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Tag
    )

    $parts = [regex]::Matches($Tag, '\d+|[^\d]+')
    if ($parts.Count -eq 0) {
        return $Tag
    }

    $builder = New-Object System.Text.StringBuilder
    foreach ($part in $parts) {
        if ($part.Value -match '^\d+$') {
            [void]$builder.AppendFormat('{0:D12}', [int64]$part.Value)
        }
        else {
            [void]$builder.Append($part.Value.ToLowerInvariant())
        }
    }

    return $builder.ToString()
}

function Find-CachedTag {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform
    )

    $preferredTag = Get-SelectedTag -Platform $Platform
    if ($preferredTag -and (Test-CachedAsset -Platform $Platform -Tag $preferredTag)) {
        return $preferredTag
    }

    $cacheRoot = Get-CacheRoot
    if (-not (Test-Path $cacheRoot)) {
        return $null
    }

    $candidates = Get-ChildItem -LiteralPath $cacheRoot -Directory |
        Where-Object { $_.Name -ne ".state" } |
        Sort-Object @{ Expression = { Get-TagSortKey $_.Name }; Descending = $true }, @{ Expression = { $_.Name }; Descending = $true }

    foreach ($candidate in $candidates) {
        if (Test-CachedAsset -Platform $Platform -Tag $candidate.Name) {
            return $candidate.Name
        }
    }

    return $null
}

function Get-LocalSelectedTag {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform
    )

    $selectedTag = Get-SelectedTag -Platform $Platform
    if ($selectedTag -and (Test-CachedAsset -Platform $Platform -Tag $selectedTag)) {
        return $selectedTag
    }

    return Find-CachedTag -Platform $Platform
}

function Ensure-CachedAssetReady {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform,
        [Parameter(Mandatory = $true)]
        [string]$Tag
    )

    $assetInfo = Get-AssetInfo -Platform $Platform -Tag $Tag
    New-Item -ItemType Directory -Force -Path $assetInfo.ReleaseDir | Out-Null

    if ((-not (Test-Path $assetInfo.ZipPath)) -or (-not (Test-Path $assetInfo.ShaPath))) {
        throw "Cached asset is incomplete."
    }

    $expectedSha = (Get-Content -LiteralPath $assetInfo.ShaPath -TotalCount 1).Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)[0].ToLowerInvariant()
    $actualSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $assetInfo.ZipPath).Hash.ToLowerInvariant()
    if ($actualSha -ne $expectedSha) {
        throw "Cached asset checksum mismatch."
    }

    if ((Test-Path $assetInfo.DigestMarker) -and ((Get-Content -LiteralPath $assetInfo.DigestMarker -TotalCount 1) -eq $expectedSha)) {
        return $assetInfo.ExtractDir
    }

    if (Test-Path $assetInfo.ExtractTmp) {
        Remove-Item -LiteralPath $assetInfo.ExtractTmp -Recurse -Force
    }

    if (Test-Path $assetInfo.ExtractDir) {
        Remove-Item -LiteralPath $assetInfo.ExtractDir -Recurse -Force
    }

    New-Item -ItemType Directory -Force -Path $assetInfo.ExtractTmp | Out-Null
    Expand-Archive -LiteralPath $assetInfo.ZipPath -DestinationPath $assetInfo.ExtractTmp -Force
    Set-Content -LiteralPath (Join-Path $assetInfo.ExtractTmp ".archive-sha256") -Value $expectedSha -NoNewline
    Move-Item -Force $assetInfo.ExtractTmp $assetInfo.ExtractDir
    return $assetInfo.ExtractDir
}

function Ensure-RemoteAssetReady {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform,
        [Parameter(Mandatory = $true)]
        [string]$Tag
    )

    $assetInfo = Get-AssetInfo -Platform $Platform -Tag $Tag
    $repo = Get-Repo
    $expandedAssetsUrl = "https://github.com/$repo/releases/expanded_assets/$Tag"
    New-Item -ItemType Directory -Force -Path $assetInfo.ReleaseDir | Out-Null

    $expandedAssets = (Invoke-GitHubRequest -Uri $expandedAssetsUrl).Content
    if (-not $expandedAssets.Contains("$($assetInfo.AssetBase).zip")) {
        Write-UnsupportedPlatform
        exit 20
    }

    if (-not (Test-Path $assetInfo.ZipPath)) {
        Download-File -Uri "https://github.com/$repo/releases/download/$Tag/$($assetInfo.AssetBase).zip" -OutputPath $assetInfo.ZipPath
    }

    if (-not (Test-Path $assetInfo.ShaPath)) {
        Download-File -Uri "https://github.com/$repo/releases/download/$Tag/$($assetInfo.AssetBase).sha256" -OutputPath $assetInfo.ShaPath
    }

    $expectedSha = (Get-Content -LiteralPath $assetInfo.ShaPath -TotalCount 1).Split(" ", [System.StringSplitOptions]::RemoveEmptyEntries)[0].ToLowerInvariant()
    $actualSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $assetInfo.ZipPath).Hash.ToLowerInvariant()

    if ($actualSha -ne $expectedSha) {
        Download-File -Uri "https://github.com/$repo/releases/download/$Tag/$($assetInfo.AssetBase).zip" -OutputPath $assetInfo.ZipPath
        $actualSha = (Get-FileHash -Algorithm SHA256 -LiteralPath $assetInfo.ZipPath).Hash.ToLowerInvariant()
        if ($actualSha -ne $expectedSha) {
            throw "Checksum verification failed for $($assetInfo.ZipPath)"
        }
    }

    if ((Test-Path $assetInfo.DigestMarker) -and ((Get-Content -LiteralPath $assetInfo.DigestMarker -TotalCount 1) -eq $expectedSha)) {
        return $assetInfo.ExtractDir
    }

    if (Test-Path $assetInfo.ExtractTmp) {
        Remove-Item -LiteralPath $assetInfo.ExtractTmp -Recurse -Force
    }

    if (Test-Path $assetInfo.ExtractDir) {
        Remove-Item -LiteralPath $assetInfo.ExtractDir -Recurse -Force
    }

    New-Item -ItemType Directory -Force -Path $assetInfo.ExtractTmp | Out-Null
    Expand-Archive -LiteralPath $assetInfo.ZipPath -DestinationPath $assetInfo.ExtractTmp -Force
    Set-Content -LiteralPath (Join-Path $assetInfo.ExtractTmp ".archive-sha256") -Value $expectedSha -NoNewline
    Move-Item -Force $assetInfo.ExtractTmp $assetInfo.ExtractDir
    return $assetInfo.ExtractDir
}

function Resolve-AssetDir {
    param(
        [Parameter(Mandatory = $true)]
        [string]$Platform,
        [Parameter(Mandatory = $true)]
        [bool]$UpdateCache
    )

    if ($UpdateCache) {
        Assert-RemoteAccessAllowed -Platform $Platform -Context "update"
        try {
            $tag = Get-LatestReleaseTag
            $extractDir = Ensure-RemoteAssetReady -Platform $Platform -Tag $tag
        }
        catch {
            Write-RemoteFailureAndExit -Platform $Platform -Message "Unable to refresh the cached release from GitHub Release."
        }
        Reset-RemoteFailures -Platform $Platform
        Set-SelectedTag -Platform $Platform -Tag $tag
        return $extractDir
    }

    $cachedTag = Find-CachedTag -Platform $Platform
    if ($cachedTag) {
        $extractDir = Ensure-CachedAssetReady -Platform $Platform -Tag $cachedTag
        Set-SelectedTag -Platform $Platform -Tag $cachedTag
        return $extractDir
    }

    Assert-RemoteAccessAllowed -Platform $Platform -Context "no-cache"
    try {
        $tag = Get-LatestReleaseTag
        $extractDir = Ensure-RemoteAssetReady -Platform $Platform -Tag $tag
    }
    catch {
        Write-RemoteFailureAndExit -Platform $Platform -Message "Unable to fetch the initial cached release from GitHub Release."
    }
    Reset-RemoteFailures -Platform $Platform
    Set-SelectedTag -Platform $Platform -Tag $tag
    return $extractDir
}

function Get-Launcher {
    param(
        [Parameter(Mandatory = $true)]
        [string]$ExtractDir
    )

    $batLauncher = Get-ChildItem -LiteralPath $ExtractDir -Recurse -File -Filter *.bat |
        Where-Object { $_.FullName -match '[\\/]bin[\\/]' } |
        Sort-Object FullName |
        Select-Object -First 1

    if ($batLauncher) {
        return $batLauncher.FullName
    }

    $genericLauncher = Get-ChildItem -LiteralPath $ExtractDir -Recurse -File |
        Where-Object { $_.FullName -match '[\\/]bin[\\/]' } |
        Sort-Object FullName |
        Select-Object -First 1

    if ($genericLauncher) {
        return $genericLauncher.FullName
    }

    throw "Unable to find a CLI launcher under $ExtractDir"
}

function Write-Usage {
    @"
Usage:
  run_latest_release.bat [--print-cache-path]
  run_latest_release.bat [--print-platform]
  run_latest_release.bat [--print-latest-tag]
  run_latest_release.bat [--prepare-only]
  run_latest_release.bat [--print-launcher]
  run_latest_release.bat [--update-cache]
  run_latest_release.bat [--reset-remote-failures]
  run_latest_release.bat -- [dexclub-cli args...]

Environment:
  DEXCLUB_CLI_GITHUB_REPO   Override the GitHub repository (default: DexClub/dexclub-cli)
  DEXCLUB_CLI_CACHE_DIR     Override the cache root
"@
}

$platform = Get-Platform
$cacheRoot = Get-CacheRoot
$updateCache = $false
$prepareOnly = $false
$printLauncher = $false
$resetRemoteFailures = $false
$remainingArgs = @()

$index = 0
while ($index -lt $args.Count) {
    switch ($args[$index]) {
        "--help" {
            Write-Output (Write-Usage)
            exit 0
        }
        "-h" {
            Write-Output (Write-Usage)
            exit 0
        }
        "--print-cache-path" {
            Write-Output $cacheRoot
            exit 0
        }
        "--print-platform" {
            Write-Output $platform
            exit 0
        }
        "--print-latest-tag" {
            $localTag = Get-LocalSelectedTag -Platform $platform
            if ($localTag) {
                Write-Output $localTag
                exit 0
            }
            exit 1
        }
        "--prepare-only" {
            $prepareOnly = $true
            $index++
            continue
        }
        "--print-launcher" {
            $printLauncher = $true
            $index++
            continue
        }
        "--update-cache" {
            $updateCache = $true
            $index++
            continue
        }
        "--refresh-cache" {
            $updateCache = $true
            $index++
            continue
        }
        "--reset-remote-failures" {
            $resetRemoteFailures = $true
            $index++
            continue
        }
        "--" {
            if ($index + 1 -lt $args.Count) {
                $remainingArgs = $args[($index + 1)..($args.Count - 1)]
            }
            break
        }
        default {
            throw "Unknown option: $($args[$index])"
        }
    }
}

if ($resetRemoteFailures) {
    Reset-RemoteFailures -Platform $platform
}

$extractDir = Resolve-AssetDir -Platform $platform -UpdateCache $updateCache

if ($prepareOnly) {
    Write-Output $extractDir
    exit 0
}

$launcher = Get-Launcher -ExtractDir $extractDir
if ($printLauncher) {
    Write-Output $launcher
    exit 0
}

& $launcher @remainingArgs
$exitCode = $LASTEXITCODE
if ($null -ne $exitCode) {
    exit $exitCode
}
