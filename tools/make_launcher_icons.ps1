# Regenerates every launcher icon and the Play Store icon from the icon master art:
#   art/spider_icon_master.webp  (square, full-bleed art with a thin light rim and black rounded corners)
#
# Windows only (WPF imaging, which reads WebP through the Windows WebP codec). Run from the repo:
#   powershell -NoProfile -ExecutionPolicy Bypass -File tools/make_launcher_icons.ps1
#
# The rim is trimmed and the black corners are cut away; behind the art the left half is the art's
# green and the right half its red, so any launcher mask (circle, squircle, rounded square) shows
# no black. Android 8+ gets an adaptive icon whose opaque foreground puts the art over the visible
# 72 dp of the 108 dp layer; Android 7 gets a rounded-square and a round icon with transparent
# corners. app/src/main/ic_launcher-playstore.png is the 512x512 Play Store listing icon.
#
# -Out writes the same tree (app\src\main\...) under another folder instead of the repository.
param([string]$Out = '')
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName PresentationCore, WindowsBase
$root = Split-Path -Parent $PSScriptRoot
$master = Join-Path $root 'art\spider_icon_master.webp'
$target = if ($Out) { $Out } else { $root }
$res = Join-Path $target 'app\src\main\res'

$stream = [IO.File]::OpenRead($master)
$decoder = [Windows.Media.Imaging.BitmapDecoder]::Create($stream,
    [Windows.Media.Imaging.BitmapCreateOptions]::None, [Windows.Media.Imaging.BitmapCacheOption]::OnLoad)
$stream.Close()
$frame = $decoder.Frames[0]
$size = $frame.PixelWidth
if ($frame.PixelHeight -ne $size) { throw 'the master must be square' }

# Rim: about 0.8% of the side on every edge; corner radius of the art about 15.5% of the trimmed side.
$trim = [int][Math]::Round($size * 0.008)
$inner = $size - 2 * $trim
$art = New-Object Windows.Media.Imaging.CroppedBitmap($frame, (New-Object Windows.Int32Rect($trim, $trim, $inner, $inner)))
$art.Freeze()
$radiusShare = 0.155

# The art's own colours next to the corners: left half green, right half red.
$stride = $size * 4
$pixels = New-Object byte[] ($stride * $size)
$frame.CopyPixels($pixels, $stride, 0)
function Sample([int]$x, [int]$y) {
    $i = $y * $stride + $x * 4
    return @($pixels[$i + 2], $pixels[$i + 1], $pixels[$i])
}
function Mix($a, $b) {
    return [Windows.Media.Color]::FromRgb([byte](($a[0] + $b[0]) / 2), [byte](($a[1] + $b[1]) / 2), [byte](($a[2] + $b[2]) / 2))
}
$c = [int]($size * 0.075)
$green = Mix (Sample $c $c) (Sample $c ($size - 1 - $c))
$red = Mix (Sample ($size - 1 - $c) $c) (Sample ($size - 1 - $c) ($size - 1 - $c))
"Backdrop green #{0:X2}{1:X2}{2:X2}, red #{3:X2}{4:X2}{5:X2}" -f $green.R, $green.G, $green.B, $red.R, $red.G, $red.B

# Draws one square PNG of $px pixels. $artPx: side of the art, centred. $backdrop: fill the square
# with the green/red halves. $shape: 'square' (art corners rounded), 'rounded' or 'circle' (the
# whole icon cut to that shape, transparent outside).
function Write-Icon([string]$path, [int]$px, [double]$artPx, [bool]$backdrop, [string]$shape) {
    $visual = New-Object Windows.Media.DrawingVisual
    [Windows.Media.RenderOptions]::SetBitmapScalingMode($visual, [Windows.Media.BitmapScalingMode]::HighQuality)
    $dc = $visual.RenderOpen()
    $full = New-Object Windows.Rect(0, 0, $px, $px)
    if ($shape -eq 'circle') {
        $dc.PushClip((New-Object Windows.Media.EllipseGeometry($full)))
    } elseif ($shape -eq 'rounded') {
        $margin = $px * 0.04
        $box = New-Object Windows.Rect($margin, $margin, ($px - 2 * $margin), ($px - 2 * $margin))
        $dc.PushClip((New-Object Windows.Media.RectangleGeometry($box, ($px * 0.16), ($px * 0.16))))
    }
    if ($backdrop) {
        $dc.DrawRectangle((New-Object Windows.Media.SolidColorBrush($green)), $null, (New-Object Windows.Rect(0, 0, ($px / 2), $px)))
        $dc.DrawRectangle((New-Object Windows.Media.SolidColorBrush($red)), $null, (New-Object Windows.Rect(($px / 2), 0, ($px / 2), $px)))
    }
    $offset = ($px - $artPx) / 2
    $artRect = New-Object Windows.Rect($offset, $offset, $artPx, $artPx)
    $radius = $artPx * $radiusShare
    $dc.PushClip((New-Object Windows.Media.RectangleGeometry($artRect, $radius, $radius)))
    $dc.DrawImage($art, $artRect)
    $dc.Pop()
    if ($shape -ne 'square') { $dc.Pop() }
    $dc.Close()
    $bitmap = New-Object Windows.Media.Imaging.RenderTargetBitmap($px, $px, 96, 96, [Windows.Media.PixelFormats]::Pbgra32)
    $bitmap.Render($visual)
    $encoder = New-Object Windows.Media.Imaging.PngBitmapEncoder
    $encoder.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($bitmap))
    $out = [IO.File]::Create($path)
    try { $encoder.Save($out) } finally { $out.Close() }
}

foreach ($entry in @(@('mdpi', 1), @('hdpi', 1.5), @('xhdpi', 2), @('xxhdpi', 3), @('xxxhdpi', 4))) {
    $density = $entry[0]; $scale = $entry[1]
    $dir = Join-Path $res "mipmap-$density"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $legacy = [int][Math]::Round(48 * $scale)
    Write-Icon (Join-Path $dir 'ic_launcher.png') $legacy ($legacy * 0.92) $true 'rounded'
    Write-Icon (Join-Path $dir 'ic_launcher_round.png') $legacy ($legacy * 1.0) $true 'circle'
    $layer = [int][Math]::Round(108 * $scale)
    Write-Icon (Join-Path $dir 'ic_launcher_foreground.png') $layer (72 * $scale) $true 'square'
    "{0}: legacy {1}px, adaptive foreground {2}px (art {3}px)" -f $density, $legacy, $layer, (72 * $scale)
}
Write-Icon (Join-Path $target 'app\src\main\ic_launcher-playstore.png') 512 512 $true 'square'
'Play Store icon: app\src\main\ic_launcher-playstore.png (512x512)'
