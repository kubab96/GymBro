# Sztuczna waga: nadaje z komputera rozgloszenia BLE z danymi producenta.
# Uzycie:
#   sztuczna-waga.ps1 -Mode counter -Seconds 80           # ramka zmienia sie co -StepMs
#   sztuczna-waga.ps1 -Mode weigh -Kg 84.05             # zero -> narastanie -> stabilny -> cisza
param(
    [string]$Mode = "counter",
    [int]$Seconds = 60,
    [int]$StepMs = 300,
    [double]$Kg = 84.05,
    [int]$CompanyId = 0x5BC0,
    [int]$StableMs = 4000,
    [switch]$NoStableFlag
)

Add-Type -AssemblyName System.Runtime.WindowsRuntime
[void][Windows.Storage.Streams.IBuffer, Windows.Storage.Streams, ContentType = WindowsRuntime]
[void][Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher,Windows.Devices.Bluetooth, ContentType = WindowsRuntime]
[void][Windows.Devices.Bluetooth.Advertisement.BluetoothLEManufacturerData, Windows.Devices.Bluetooth, ContentType = WindowsRuntime]
[void][Windows.Storage.Streams.DataWriter, Windows.Storage.Streams, ContentType = WindowsRuntime]

function Publish([byte[]]$bytes, [int]$ms) {
    $pub = New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher
    $md = New-Object Windows.Devices.Bluetooth.Advertisement.BluetoothLEManufacturerData
    $md.CompanyId = [uint16]$CompanyId
    $md.Data = [System.Runtime.InteropServices.WindowsRuntime.WindowsRuntimeBufferExtensions]::AsBuffer($bytes)
    # PowerShell 5.1 widzi liste WinRT jako goly obiekt COM - wolamy Add przez interfejs.
    $advT = [Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisement, Windows.Devices.Bluetooth, ContentType = WindowsRuntime]
    $adv = [Windows.Devices.Bluetooth.Advertisement.BluetoothLEAdvertisementPublisher].GetProperty('Advertisement').GetValue($pub)
    $list = $advT.GetProperty('ManufacturerData').GetValue($adv)
    $coll = [System.Collections.Generic.ICollection[Windows.Devices.Bluetooth.Advertisement.BluetoothLEManufacturerData]]
    [void]$coll.GetMethod('Add').Invoke($list, [object[]]@($md.PSObject.BaseObject))
    $pub.Start()
    Start-Sleep -Milliseconds $ms
    $st = $pub.Status
    $pub.Stop()
    return $st
}

function Frame([double]$kg, [int]$flags) {
    $raw = [int][math]::Round($kg * 100)
    $imp = if ($raw -gt 0) { 0x1388 } else { 0 }
    return [byte[]]@(
        (($raw -shr 8) -band 0xFF), ($raw -band 0xFF),
        (($imp -shr 8) -band 0xFF), ($imp -band 0xFF),
        0, 0, $flags, 0, 0, 0, 0, 0, 0)
}

if ($Mode -eq "counter") {
    $end = (Get-Date).AddSeconds($Seconds)
    $n = 0
    while ((Get-Date) -lt $end) {
        $n++
        $b = [byte[]]@(0xEE, (($n -shr 8) -band 0xFF), ($n -band 0xFF), 0, 0, 0, 0x24, 0, 0, 0, 0, 0, 0)
        $st = Publish $b $StepMs
        Write-Output ((Get-Date).AddMilliseconds(-$StepMs).ToString("HH:mm:ss.fff") + " ramka " + $n.ToString("X4") + " " + $st)
    }
    Write-Output ("nadano roznych ramek: " + $n)
}
elseif ($Mode -eq "weigh") {
    $stableFlag = if ($NoStableFlag) { 0x24 } else { 0x25 }
    Write-Output ((Get-Date).ToString("HH:mm:ss.fff") + " waga obudzona, 0.00")
    [void](Publish (Frame 0 0x24) 1500)
    Write-Output ((Get-Date).ToString("HH:mm:ss.fff") + " wejscie na wage")
    foreach ($f in 0.35, 0.7, 0.93, 1.04, 0.99, 1.003) {
        [void](Publish (Frame ($Kg * $f) 0x24) 350)
    }
    Write-Output ((Get-Date).ToString("HH:mm:ss.fff") + " wynik ustalony: " + $Kg)
    [void](Publish (Frame $Kg $stableFlag) $StableMs)
    Write-Output ((Get-Date).ToString("HH:mm:ss.fff") + " waga gasnie")
}
