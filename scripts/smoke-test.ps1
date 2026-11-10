# Compatibility alias: all tests use temporary fictitious worlds and loopback HTTP.
$ErrorActionPreference = 'Stop'
& (Join-Path $PSScriptRoot 'test.ps1')
