# Generates docs/banner.png from the screenshots in docs/screenshots/.
# Run from the repo root:  powershell -File scripts/make-banner.ps1
# Requires Windows (System.Drawing); the output is committed so CI never needs this script.

Add-Type -AssemblyName System.Drawing

$ErrorActionPreference = "Stop"

$W = 1920
$H = 640
$bmp = New-Object System.Drawing.Bitmap($W, $H)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
$g.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
$g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

function New-RoundRect([single]$x, [single]$y, [single]$w, [single]$h, [single]$r) {
    $p = New-Object System.Drawing.Drawing2D.GraphicsPath
    $d = 2 * $r
    $p.AddArc($x, $y, $d, $d, 180, 90)
    $p.AddArc(($x + $w - $d), $y, $d, $d, 270, 90)
    $p.AddArc(($x + $w - $d), ($y + $h - $d), $d, $d, 0, 90)
    $p.AddArc($x, ($y + $h - $d), $d, $d, 90, 90)
    $p.CloseFigure()
    return $p
}

# --- Background: dark navy diagonal gradient ---------------------------------------------
$rect = New-Object System.Drawing.Rectangle(0, 0, $W, $H)
$bg = New-Object System.Drawing.Drawing2D.LinearGradientBrush(
    $rect,
    [System.Drawing.Color]::FromArgb(255, 11, 15, 26),
    [System.Drawing.Color]::FromArgb(255, 23, 32, 58),
    [System.Drawing.Drawing2D.LinearGradientMode]::ForwardDiagonal)
$g.FillRectangle($bg, $rect)

# Soft accent glow behind the phones.
foreach ($scale in 1.0, 0.7, 0.45) {
    $gw = 760 * $scale
    $gh = 560 * $scale
    $gx = 1560 - $gw / 2
    $gy = 330 - $gh / 2
    $glowPath = New-Object System.Drawing.Drawing2D.GraphicsPath
    $glowPath.AddEllipse($gx, $gy, $gw, $gh)
    $glow = New-Object System.Drawing.Drawing2D.PathGradientBrush($glowPath)
    $glow.CenterColor = [System.Drawing.Color]::FromArgb(26, 124, 156, 255)
    $glow.SurroundColors = @([System.Drawing.Color]::FromArgb(0, 124, 156, 255))
    $g.FillEllipse($glow, $gx, $gy, $gw, $gh)
}

# --- Wordmark ---------------------------------------------------------------------------
$fontTitle = New-Object System.Drawing.Font("Segoe UI", 108, [System.Drawing.FontStyle]::Bold, [System.Drawing.GraphicsUnit]::Pixel)
$brushFg = [System.Drawing.Brushes]::White
$g.DrawString("OCDroid", $fontTitle, $brushFg, 116, 208)

# Accent underline below the wordmark.
$accent = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 124, 156, 255))
$g.FillRectangle($accent, 120, 344, 190, 9)

# Tagline.
$fontTag = New-Object System.Drawing.Font("Segoe UI", 38, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$brushMuted = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 168, 179, 199))
$g.DrawString("The official OpenCode web client, natively on Android.", $fontTag, $brushMuted, 120, 386)

# Feature pills.
$fontPill = New-Object System.Drawing.Font("Segoe UI", 24, [System.Drawing.FontStyle]::Regular, [System.Drawing.GraphicsUnit]::Pixel)
$penPill = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 42, 54, 83), 2)
$fillPill = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 17, 24, 39))
$labels = @("MIT License", "Android 8.0+", "Zero telemetry")
$px = 120.0
foreach ($label in $labels) {
    $size = $g.MeasureString($label, $fontPill)
    $pw = $size.Width + 44
    $path = New-RoundRect $px 486 $pw 52 26
    $g.FillPath($fillPill, $path)
    $g.DrawPath($penPill, $path)
    $g.DrawString($label, $fontPill, $brushMuted, ($px + 22), 498)
    $px += $pw + 18
}

# --- Phone trio on the right --------------------------------------------------------------
$shots = @(
    @{ file = "docs/screenshots/dark-chat.jpg";  x = 1210; y = 156 },
    @{ file = "docs/screenshots/light-cmd.jpg";  x = 1460; y = 106 },
    @{ file = "docs/screenshots/dark-tabs.jpg";  x = 1710; y = 156 }
)
$framePen = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 66, 86, 128), 3)
$frameFill = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 15, 21, 35))
$PW = 176.0
$PH = $PW * 2296 / 1080

foreach ($shot in $shots) {
    $img = [System.Drawing.Image]::FromFile((Resolve-Path $shot.file))
    $fx = $shot.x
    $fy = $shot.y

    $outer = New-RoundRect $fx $fy $PW ($PH + 18) 26
    $g.FillPath($frameFill, $outer)
    $g.DrawPath($framePen, $outer)

    # Screen inset with rounded clip.
    $inset = 8.0
    $sx = $fx + $inset
    $sy = $fy + $inset
    $sw = $PW - 2 * $inset
    $sh = $PH
    $screen = New-RoundRect $sx $sy $sw $sh 18
    $g.SetClip($screen)
    $destRect = New-Object System.Drawing.RectangleF($sx, $sy, $sw, $sh)
    $g.DrawImage($img, $destRect)
    $g.ResetClip()

    $img.Dispose()
}

# --- Save ----------------------------------------------------------------------------------
$outDir = "docs"
if (-not (Test-Path $outDir)) { New-Item -ItemType Directory -Path $outDir | Out-Null }
$bmp.Save("docs/banner.png", [System.Drawing.Imaging.ImageFormat]::Png)
$g.Dispose()
$bmp.Dispose()
Write-Output "docs/banner.png written"
