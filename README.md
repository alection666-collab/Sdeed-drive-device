# SMART WHEEL

Смартфон как руль для Windows. Сейчас: этап 4 (виртуальный геймпад Xbox 360).

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

## Этап 4: виртуальный геймпад
1. Установите драйвер ViGEmBus: страница https://github.com/nefarius/ViGEmBus/releases ->
   файл `ViGEmBus_1.22.0_x64_x86_arm64.exe` -> запустить, нажимать «Далее». Перезагрузка не требуется, если Windows не попросит.
2. В папке `server`: `dotnet run` (или `dotnet run -- 60`, где 60 - градусов наклона до полного отклонения стика).
3. Проверка: Win+R -> `joy.cpl` -> «Controller (Xbox 360 for Windows)» -> Свойства.
   Наклоняйте телефон: ось X должна двигаться.
