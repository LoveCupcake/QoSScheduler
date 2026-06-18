param(
    [switch]$InstallDeps = $false
)

$ErrorActionPreference = "Stop"
$ServerPath = Join-Path -Path $PSScriptRoot -ChildPath "server"

Write-Host "=============================================" -ForegroundColor Cyan
Write-Host "🚀 Khởi động QoS Scheduler Web Admin Server" -ForegroundColor Cyan
Write-Host "=============================================" -ForegroundColor Cyan

# Di chuyển vào thư mục server
Set-Location -Path $ServerPath

# Cài đặt thư viện nếu chưa có hoặc nếu user truyền cờ -InstallDeps
if (-not (Test-Path "node_modules") -or $InstallDeps) {
    Write-Host "[*] Đang cài đặt thư viện (npm install)..." -ForegroundColor Yellow
    npm install
    Write-Host "[+] Cài đặt hoàn tất!" -ForegroundColor Green
}

# Chạy server
Write-Host "[*] Đang chạy Node.js Server..." -ForegroundColor Yellow
Write-Host "    👉 Truy cập: http://localhost:3000 (hoặc port bạn đã cấu hình)" -ForegroundColor Gray
Write-Host "---------------------------------------------" -ForegroundColor Cyan

try {
    # Nếu trong package.json có script start thì đổi thành npm start cũng được, ở đây dùng node server.js cho chắc
    node server.js
} catch {
    Write-Host "[!] Đã xảy ra lỗi khi chạy server: $_" -ForegroundColor Red
}
