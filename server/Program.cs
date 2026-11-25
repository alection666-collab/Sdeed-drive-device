using System.Net;
using System.Net.NetworkInformation;
using System.Net.Sockets;
using System.Diagnostics;

// SMART WHEEL: этап 2 - приёмник UDP-пакетов от телефона.
// Пакет (16 байт, little-endian): 'S','W', версия(1), тип(0), seq(uint32), время_мкс(uint32), значение(float32)
const int Port = 9876;

Console.OutputEncoding = System.Text.Encoding.UTF8;
Console.WriteLine("SMART WHEEL server, этап 2");
Console.WriteLine($"Слушаю UDP-порт {Port}. Введите на телефоне один из этих адресов:");
foreach (var ni in NetworkInterface.GetAllNetworkInterfaces())
{
    if (ni.OperationalStatus != OperationalStatus.Up) continue;
    foreach (var ua in ni.GetIPProperties().UnicastAddresses)
        if (ua.Address.AddressFamily == AddressFamily.InterNetwork && !IPAddress.IsLoopback(ua.Address))
            Console.WriteLine($"  {ua.Address}   ({ni.Name})");
}
Console.WriteLine();

var st = new State();
var gapWatch = Stopwatch.StartNew();
double lastArrival = 0;

using var reporter = new Timer(_ =>
{
    long w = Interlocked.Exchange(ref st.Window, 0);
    double gap = Interlocked.Exchange(ref st.MaxGapUs, 0) / 1000.0;
    if (w == 0) Console.WriteLine("Нет данных от телефона...");
    else Console.WriteLine($"{w,4} пак/с | потеряно: {st.Lost} | макс. пауза: {gap:F1} мс | значение: {st.Value:F2}");
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

    if (first || seq < last) first = false;            // новая сессия
    else if (seq > last + 1) st.Lost += seq - last - 1; // пропущенные пакеты
    last = seq;

    double now = gapWatch.Elapsed.TotalMilliseconds * 1000.0;
    if (lastArrival > 0)
    {
        long g = (long)(now - lastArrival);
        if (g > Interlocked.Read(ref st.MaxGapUs)) Interlocked.Exchange(ref st.MaxGapUs, g);
    }
    lastArrival = now;
    Interlocked.Increment(ref st.Window);
}

class State
{
    public long Window;
    public long Lost;
    public long MaxGapUs;
    public float Value;
}
