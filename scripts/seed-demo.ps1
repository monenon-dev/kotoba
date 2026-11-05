# Creates a demo account, deck and cards through the public API. Safe to run again.
# Design: docs/design/06-deploy-render.md
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1 -BaseUrl <server address>
#
# The password is asked for at run time and is never stored. Automated runs can set KOTOBA_SEED_PASSWORD.
# Works on Windows PowerShell 5.1 and PowerShell 7. Messages stay in ASCII because 5.1 reads scripts
# without a BOM as ANSI; the Japanese data lives in seed-demo-data.json.
param(
    [Parameter(Mandatory = $true)][string]$BaseUrl,
    [string]$Email = 'demo@example.com',
    [int]$WakeTimeoutSeconds = 420
)

$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
# Windows PowerShell 5.1 may not enable TLS 1.2 by default.
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$BaseUrl = $BaseUrl.TrimEnd('/')
if ($BaseUrl -notmatch '^https://' -and $BaseUrl -notmatch '^http://(localhost|127\.0\.0\.1)(:\d+)?$') {
    throw 'BaseUrl must use https:// (http:// is allowed only for localhost).'
}

function Read-SeedPassword {
    if ($env:KOTOBA_SEED_PASSWORD) { return $env:KOTOBA_SEED_PASSWORD }
    $secure = Read-Host -Prompt 'Demo account password (8-72 characters)' -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    }
}

# Returns @{ Status = <int>; Body = <object or $null> }. Never throws on an HTTP error status.
# Bytes are handled directly: 5.1 would otherwise send and read non-ASCII text in the wrong encoding.
function Invoke-Api {
    param(
        [string]$Method,
        [string]$Path,
        [object]$Body,
        [string]$Token,
        [int]$TimeoutSec = 120
    )
    $request = @{
        Uri             = "$BaseUrl$Path"
        Method          = $Method
        TimeoutSec      = $TimeoutSec
        UseBasicParsing = $true
        Headers         = @{ Accept = 'application/json' }
    }
    if ($Token) { $request.Headers.Authorization = "Bearer $Token" }
    if ($null -ne $Body) {
        $request.ContentType = 'application/json; charset=utf-8'
        $request.Body = [Text.Encoding]::UTF8.GetBytes(($Body | ConvertTo-Json -Depth 5))
    }

    # Waits and retries only when the server asks for it (rate limit or temporarily unavailable).
    for ($attempt = 1; $true; $attempt++) {
        try {
            $response = Invoke-WebRequest @request
            $status = [int]$response.StatusCode
            $text = [Text.Encoding]::UTF8.GetString($response.RawContentStream.ToArray())
        } catch {
            if ($null -eq $_.Exception.Response) { throw }
            $status = [int]$_.Exception.Response.StatusCode
            $text = ''
        }
        if (($status -ne 429 -and $status -ne 503) -or $attempt -ge 4) { break }
        Write-Host "Server returned $status, retrying in 15 seconds..."
        Start-Sleep -Seconds 15
    }

    $parsed = $null
    if ($text) {
        try { $parsed = $text | ConvertFrom-Json } catch { $parsed = $null }
    }
    return @{ Status = $status; Body = $parsed }
}

# A sleeping server may answer with something that is not our JSON, so only status UP counts as ready.
function Wait-ForServer {
    $deadline = (Get-Date).AddSeconds($WakeTimeoutSeconds)
    Write-Host "Waking the server (up to $WakeTimeoutSeconds seconds)..."
    while ((Get-Date) -lt $deadline) {
        try {
            $health = Invoke-Api -Method GET -Path '/api/health' -TimeoutSec 60
            if ($health.Status -eq 200 -and $health.Body.status -eq 'UP') {
                Write-Host 'Server is up.'
                return
            }
        } catch {
            # Connection errors are expected while the server is starting.
        }
        Start-Sleep -Seconds 5
    }
    throw "Server did not report UP within $WakeTimeoutSeconds seconds."
}

$dataPath = Join-Path $PSScriptRoot 'seed-demo-data.json'
$data = Get-Content -Path $dataPath -Raw -Encoding UTF8 | ConvertFrom-Json

$password = Read-SeedPassword
if ($password.Length -lt 8 -or $password.Length -gt 72) {
    throw 'Password must be 8-72 characters.'
}

Wait-ForServer

$credentials = @{ email = $Email; password = $password }
$signup = Invoke-Api -Method POST -Path '/api/auth/signup' -Body $credentials
if ($signup.Status -eq 201) {
    Write-Host "Created account $Email"
} elseif ($signup.Status -eq 409) {
    Write-Host "Account $Email already exists, logging in."
} else {
    throw "Signup failed with status $($signup.Status)."
}

$login = Invoke-Api -Method POST -Path '/api/auth/login' -Body $credentials
if ($login.Status -ne 200) {
    throw "Login failed with status $($login.Status). Check the password for the existing account."
}
$token = $login.Body.accessToken

$decks = Invoke-Api -Method GET -Path '/api/decks?size=100' -Token $token
if ($decks.Status -ne 200) { throw "Listing decks failed with status $($decks.Status)." }
$deck = @($decks.Body.content | Where-Object { $_.name -eq $data.deck.name }) | Select-Object -First 1
if ($null -eq $deck) {
    $createdDeck = Invoke-Api -Method POST -Path '/api/decks' -Token $token -Body @{
        name        = $data.deck.name
        description = $data.deck.description
    }
    if ($createdDeck.Status -ne 201) { throw "Creating the deck failed with status $($createdDeck.Status)." }
    $deck = $createdDeck.Body
    Write-Host "Created deck $($deck.id)"
} else {
    Write-Host "Deck $($deck.id) already exists."
}

$existing = Invoke-Api -Method GET -Path "/api/decks/$($deck.id)/cards?size=100" -Token $token
if ($existing.Status -ne 200) { throw "Listing cards failed with status $($existing.Status)." }
$existingTexts = @($existing.Body.content | ForEach-Object { $_.text })

# One request at a time: free instances have little CPU and a low inbound rate limit.
$created = 0
foreach ($card in $data.cards) {
    if ($existingTexts -contains $card.text) { continue }
    $result = Invoke-Api -Method POST -Path "/api/decks/$($deck.id)/cards" -Token $token -Body @{
        text    = $card.text
        reading = $card.reading
        meaning = $card.meaning
        example = $card.example
    }
    if ($result.Status -ne 201) { throw "Creating a card failed with status $($result.Status)." }
    $created++
}

$total = $existingTexts.Count + $created
Write-Host "Done. email=$Email deck=$($deck.id) cards=$total created=$created"
