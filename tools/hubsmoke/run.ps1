$ErrorActionPreference = 'Stop'

$here = Split-Path -Parent $PSCommandPath
Set-Location $here

# The meta workspace has a top-level go.work that does not include this module.
# Force module mode to make `go run` work out-of-the-box.
$env:GOWORK = 'off'

go run . @args

