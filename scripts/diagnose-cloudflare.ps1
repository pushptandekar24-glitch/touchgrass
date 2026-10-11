<#
.SYNOPSIS
    Safe diagnostic script for Cloudflare Workers AI @cf/meta/llama-3.2-3b-instruct.
    Prints only structural metadata (HTTP status, JSON keys, node types, success flag, counts).
    Never prints or leaks API tokens, Account IDs, authorization headers, or generated content.
#>

[CmdletBinding()]
param(
    [Parameter(Mandatory = $false)]
    [string]$AccountId,

    [Parameter(Mandatory = $false)]
    [string]$ApiToken,

    [Parameter(Mandatory = $false)]
    [string]$Model = "@cf/meta/llama-3.2-3b-instruct"
)

if (-not $AccountId) {
    $AccountId = Read-Host "Enter Cloudflare Account ID"
}

if (-not $ApiToken) {
    $SecureToken = Read-Host "Enter Cloudflare API Token (input hidden)" -AsSecureString
    $BSTR = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureToken)
    $ApiToken = [System.Runtime.InteropServices.Marshal]::PtrToStringAuto($BSTR)
}

$url = "https://api.cloudflare.com/client/v4/accounts/$AccountId/ai/run/$Model"
Write-Host "`n=== Cloudflare Workers AI Diagnostic ===" -ForegroundColor Cyan
Write-Host "Target: POST /client/v4/accounts/<REDACTED>/ai/run/$Model"

$body = @{
    messages = @(
        @{ role = "system"; content = "You are WildStep. Return ONLY valid JSON: {`"title`": `"Test`"}" },
        @{ role = "user"; content = "Generate a test mission" }
    )
    temperature = 0.2
    max_tokens = 256
} | ConvertTo-Json -Depth 5

try {
    $response = Invoke-WebRequest -Uri $url -Method Post -Headers @{
        "Authorization" = "Bearer $ApiToken"
        "Content-Type" = "application/json"
    } -Body $body -UseBasicParsing

    Write-Host "`n[HTTP Response]" -ForegroundColor Green
    Write-Host "HTTP Status: " $response.StatusCode
    Write-Host "Content-Type: " $response.Headers["Content-Type"]

    $json = $response.Content | ConvertFrom-Json
    Write-Host "`n[Top-Level Envelope]" -ForegroundColor Green
    Write-Host "Is Object: True"
    Write-Host "Top-level keys: " ($json.PSObject.Properties.Name -join ", ")
    Write-Host "Success flag: " $json.success
    Write-Host "Errors count: " ($json.errors.Count)
    Write-Host "Messages count: " ($json.messages.Count)

    Write-Host "`n[Result Node Structure]" -ForegroundColor Green
    if ($null -eq $json.result) {
        Write-Host "result: null / missing" -ForegroundColor Yellow
    } elseif ($json.result -is [string]) {
        Write-Host "result is scalar string (Length=$($json.result.Length))" -ForegroundColor Cyan
    } elseif ($json.result -is [PSCustomObject]) {
        Write-Host "result is Object with keys: " ($json.result.PSObject.Properties.Name -join ", ")
        foreach ($prop in $json.result.PSObject.Properties) {
            $val = $prop.Value
            if ($null -eq $val) {
                Write-Host "  -> $($prop.Name): null"
            } elseif ($val -is [string]) {
                Write-Host "  -> $($prop.Name): String (Length=$($val.Length))"
            } elseif ($val -is [PSCustomObject]) {
                Write-Host "  -> $($prop.Name): Object (keys: $($val.PSObject.Properties.Name -join ', '))"
            } elseif ($val -is [System.Collections.IEnumerable]) {
                Write-Host "  -> $($prop.Name): Array (count: $($val.Count))"
            } else {
                Write-Host "  -> $($prop.Name): Type=$($val.GetType().Name)"
            }
        }
    } else {
        Write-Host "result has unexpected type: " $json.result.GetType().FullName
    }

    if ($json.errors.Count -gt 0) {
        Write-Host "`n[Sanitized Error Messages]" -ForegroundColor Red
        foreach ($err in $json.errors) {
            Write-Host "  -> Code: $($err.code), Message: $($err.message)"
        }
    }

} catch {
    Write-Host "`n[Request Failed]" -ForegroundColor Red
    Write-Host "Error: " $_.Exception.Message
    if ($_.Exception.Response) {
        Write-Host "HTTP Status Code: " $_.Exception.Response.StatusCode.value__
    }
}
