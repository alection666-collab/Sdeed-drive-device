using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Diagnostics;
using Nefarius.ViGEm.Client;
using Nefarius.ViGEm.Client.Targets;
using Nefarius.ViGEm.Client.Targets.Xbox360;

// SMART WHEEL: этап 2 - приёмник UDP-пакетов от телефона.
// Пакет (16 байт, little-endian): 'S','W', версия(1), тип(0), seq(uint32), время_мкс(uint32), угол_руля_градусы(float32)
const int Port = 9876;
// Угол телефона (в градусах), при котором стик отклонён на максимум. Можно задать: dotnet run -- 60
float maxAngle = args.Length > 0 && float.TryParse(args[0], System.Globalization.NumberStyles.Float,
    System.Globalization.CultureInfo.InvariantCulture, out var ma) && ma > 5f ? ma : 90f;

Console.OutputEncoding = System.Text.Encoding.UTF8;
Console.WriteLine("SMART WHEEL server, этап 4");
Console.WriteLine($"Слушаю UDP-порт {Port}. Введите на телефоне один из этих адресов:");
foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
{
    if (ni.OperationalStatus != OperationalStatus.Up) continue;
    foreach (var ua in ni.GetIPProperties().UnicastAddresses)
        if (ua.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ua.Address))
            Console.WriteLine($"  {ua.Address}   ({ni.Name})");
}
Console.WriteLine();

static string Bar(float angle)
{
    const int width = 41;
    int c = width / 2;
    float a = Math.Clamp(angle, -90f, 90f);
    int pos = c + (int)Math.Round(a / 90f * c);
    var chars = new char[width];
    Array.Fill(chars, '-');
    chars[c] = '|';
    chars[pos] = '#';
    return new string(chars);
}

// --- Виртуальный геймпад Xbox 360 (драйвер ViGEmBus) ---
ViGEmClient? vigem = null;
IXbox360Controller? pad = null;
var padLock = new object();
try
{
    vigem = new ViGEmClient();
    pad = vigem.CreateXbox360Controller();
    pad.Connect();
    Console.WriteLine($"Виртуальный геймпад Xbox 360 создан. Полный стик = {maxAngle:F0}° наклона телефона.");
}
catch (Exception ex)
{
    pad = null;
    Console.WriteLine("ВИРТУАЛЬНЫЙ ГЕЙМПАД НЕ СОЗДАН: " + ex.Message);
    Console.WriteLine("Установите драйвер ViGEmBus (см. README) и перезапустите программу.");
    Console.WriteLine("Пока программа работает как обычный приёмник угла.");
}
Console.WriteLine();

void SetStick(short v)
{
    if (pad == null) return;
    lock (padLock) { pad.SetAxisValue(Xbox360Axis.LeftThumbX, v); }
}

var st = new State();
var gapWatch = Stopwatch.StartNew();
double lastArrival = 0;

using var reporter = new Timer(_ =>
{
    long w = Interlocked.Exchange(ref st.Window, 0);
    double gap = Interlocked.Exchange(ref st.MaxGapUs, 0) / 1000.0;
    if (w == 0) { SetStick(0); Console.WriteLine("Нет данных от телефона, стик в центре..."); }
    else Console.WriteLine($"{w,4} пак/с | пауза {gap,5:F1} мс | потеряно {st.Lost} | рывков>50мс {st.Spikes} | угол {st.Value,7:F1}° | ось {st.Axis,6} [{Bar(st.Value)}]");
}, null, 1000, 1000);

using var udp = new UdpClient(Port);
var remote = new IPEndPoint(IPAddress.Any, 0);
uint last = 0;
bool first = true;

while (true)
{
    var data = udp.Receive(ref remote);
    if (data.Length < 16 || data[0] != (byte)'S' || data[1] != (byte)'W' || data[2] != 1) continue;

    udp.Send(data, data.Length, remote); // эхо обратно: телефон по нему меряет задержку

    uint seq = BitConverter.ToUInt32(data, 4);
    st.Value = BitConverter.ToSingle(data, 12);
    short axis = (short)Math.Clamp((int)Math.Round(st.Value / maxAngle * 32767f), -32767, 32767);
    st.Axis = axis;
    SetStick(axis);

    bool newSession = first || (seq < last && last - seq > 100);
    if (newSession)
    {
        first = false; st.Lost = 0; st.Spikes = 0; last = seq; // "Начать отправку" на телефоне обнуляет счётчики
    }
    else if (seq > last)
    {
        if (seq > last + 1) st.Lost += seq - last - 1; // пропущенные пакеты
        last = seq;
    }

    double now = gapWatch.Elapsed.TotalMilliseconds * 1000.0;
    if (lastArrival > 0)
    {
        long g = (long)(now - lastArrival);
        if (g > Interlocked.Read(ref st.MaxGapUs)) Interlocked.Exchange(ref st.MaxGapUs, g);
        if (g > 50_000) Interlocked.Increment(ref st.Spikes); // рывок: пауза больше 50 мс
    }
    lastArrival = now;
    Interlocked.Increment(ref st.Window);
}

class State
{
    public long Window;
    public long Lost;
    public long Spikes;
    public long MaxGapUs;
    public float Value;
    public short Axis;
}
