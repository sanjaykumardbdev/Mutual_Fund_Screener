$ErrorActionPreference = "Stop"

$securePassword = Read-Host "Oracle MUTUAL_FUND_APP password" -AsSecureString
$passwordPointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
$mavenExitCode = 0
$bootstrapPasswordPointer = [IntPtr]::Zero
$bootstrapPassword = $null
$bootstrapSecurePassword = $null
$setBootstrapPassword = $false

try {
    if ([string]::IsNullOrWhiteSpace($env:DB_USERNAME)) {
        $env:DB_USERNAME = "MUTUAL_FUND_APP"
    }
    if ([string]::IsNullOrWhiteSpace($env:DB_URL)) {
        $env:DB_URL = "jdbc:oracle:thin:@//localhost:1521/ORCLPDB"
    }
    $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($passwordPointer)
    if (-not [string]::IsNullOrWhiteSpace($env:BOOTSTRAP_ADMIN_USERNAME) -and
        -not [string]::IsNullOrWhiteSpace($env:BOOTSTRAP_ADMIN_EMAIL) -and
        [string]::IsNullOrWhiteSpace($env:BOOTSTRAP_ADMIN_PASSWORD)) {
        $bootstrapSecurePassword =
            Read-Host "Initial administrator password (12-72 characters)" -AsSecureString
        $bootstrapPasswordPointer =
            [Runtime.InteropServices.Marshal]::SecureStringToBSTR($bootstrapSecurePassword)
        $env:BOOTSTRAP_ADMIN_PASSWORD =
            [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bootstrapPasswordPointer)
        $setBootstrapPassword = $true
    }

    Push-Location $PSScriptRoot
    try {
        & mvn spring-boot:run
        $mavenExitCode = $LASTEXITCODE
    }
    finally {
        Remove-Item Env:DB_PASSWORD -ErrorAction SilentlyContinue
        if ($setBootstrapPassword) {
            Remove-Item Env:BOOTSTRAP_ADMIN_PASSWORD -ErrorAction SilentlyContinue
        }
        Pop-Location
    }
}
finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($passwordPointer)
    $securePassword.Dispose()
    if ($bootstrapPasswordPointer -ne [IntPtr]::Zero) {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bootstrapPasswordPointer)
    }
    if ($null -ne $bootstrapSecurePassword) {
        $bootstrapSecurePassword.Dispose()
    }
}

if ($mavenExitCode -ne 0) {
    exit $mavenExitCode
}
