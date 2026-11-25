# SMART WHEEL

Смартфон как руль для Windows. Сейчас: этап 2 (проверка связи).

- `android/` - приложение для телефона (Kotlin, Jetpack Compose)
- `server/` - Windows-приёмник (.NET 8)
- `.github/workflows/android.yml` - автосборка APK на GitHub

## Сервер (Windows)
1. `winget install Microsoft.DotNet.SDK.8`
2. В папке `server`: `dotnet run`
3. При запросе брандмауэра разрешите доступ в частных сетях.
   Или (PowerShell от администратора):
   `netsh advfirewall firewall add rule name="SmartWheel" dir=in action=allow protocol=UDP localport=9876`

## Телефон
GitHub -> вкладка Actions -> Build Android -> Artifacts -> smartwheel-debug-apk.
Распакуйте zip, установите APK, введите IP компьютера, нажмите «Начать отправку».
