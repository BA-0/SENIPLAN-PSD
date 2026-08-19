Add-Type -AssemblyName System.Drawing
$pub = "c:\wamp64\www\SENI_PLAN\SENIPLAN-PSD\frontend\public"

$imgO = [System.Drawing.Image]::FromFile("$pub\login-bg.jpg")
$imgC = [System.Drawing.Image]::FromFile("$pub\login-bg-clean.jpg")
$W = 1376; $H = 768
$fmt = [System.Drawing.Imaging.PixelFormat]::Format32bppArgb

$bmpO = New-Object System.Drawing.Bitmap $W, $H, $fmt
$bmpC = New-Object System.Drawing.Bitmap $W, $H, $fmt
$g = [System.Drawing.Graphics]::FromImage($bmpO); $g.DrawImage($imgO, 0, 0, $W, $H); $g.Dispose()
$g = [System.Drawing.Graphics]::FromImage($bmpC); $g.DrawImage($imgC, 0, 0, $W, $H); $g.Dispose()

# Bande de travail resserree sur le lettrage : au-dela, la version clean a
# aussi re-peint la brume de l'horizon, ce qui polluerait le masque.
$rect = New-Object System.Drawing.Rectangle 392, 100, 630, 134
$ro = $bmpO.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadWrite, $fmt)
$rc = $bmpC.LockBits($rect, [System.Drawing.Imaging.ImageLockMode]::ReadOnly, $fmt)
$stride = $ro.Stride
$n = $stride * $rect.Height
$ao = New-Object byte[] $n
$ac = New-Object byte[] $n
[System.Runtime.InteropServices.Marshal]::Copy($ro.Scan0, $ao, 0, $n)
[System.Runtime.InteropServices.Marshal]::Copy($rc.Scan0, $ac, 0, $n)

# Seuils tires des percentiles mesures (p80=36.8, p90=62.9, p99=94.3).
$D0 = 36.0; $DHI = 78.0; $GAMMA = 0.75; $FORCE = 0.95
# Vert du bouton "Se connecter" (primary-500) et rouge de la vague (accent).
$GR = 45.0;  $GG = 122.0; $GB = 69.0
$RR = 237.0; $RG = 20.0;  $RB = 27.0
# La vague se separe des lettres par sa dominante rouge (R-G ~ +33 contre +11)
# et par sa position : elle est au-dessus du lettrage.
$SEUIL_RG = 22.0; $VAGUE_Y_MAX = 152

$mask = New-Object double[] ($rect.Width * $rect.Height)
$vague = New-Object bool[] ($rect.Width * $rect.Height)
$sumG = 0.0; $cntG = 0; $sumR = 0.0; $cntR = 0
$i = 0
for ($y = 0; $y -lt $rect.Height; $y++) {
  $row = $y * $stride
  $sy = $rect.Y + $y
  for ($x = 0; $x -lt $rect.Width; $x++) {
    $p = $row + $x * 4
    $lc = 0.299*$ac[$p+2] + 0.587*$ac[$p+1] + 0.114*$ac[$p]
    $lo = 0.299*$ao[$p+2] + 0.587*$ao[$p+1] + 0.114*$ao[$p]
    $m = ($lc - $lo - $D0) / ($DHI - $D0)
    if ($m -lt 0) { $m = 0.0 } elseif ($m -gt 1) { $m = 1.0 }
    if ($m -gt 0) { $m = [math]::Pow($m, $GAMMA) }
    $mask[$i] = $m
    $estVague = ($sy -le $VAGUE_Y_MAX) -and (($ao[$p+2] - $ao[$p+1]) -gt $SEUIL_RG)
    $vague[$i] = $estVague
    if ($m -ge 0.85) {
      if ($estVague) { $sumR += $lo; $cntR++ } else { $sumG += $lo; $cntG++ }
    }
    $i++
  }
}
$LrefG = if ($cntG -gt 0) { $sumG / $cntG } else { 120.0 }
$LrefR = if ($cntR -gt 0) { $sumR / $cntR } else { 120.0 }
$tot = 0; foreach ($m in $mask) { if ($m -gt 0.02) { $tot++ } }
"masque: $tot px teintes | lettres $cntG px (L=$([math]::Round($LrefG,1))) | vague $cntR px (L=$([math]::Round($LrefR,1)))"

$i = 0
for ($y = 0; $y -lt $rect.Height; $y++) {
  $row = $y * $stride
  for ($x = 0; $x -lt $rect.Width; $x++) {
    $p = $row + $x * 4
    $m = $mask[$i]; $estVague = $vague[$i]; $i++
    if ($m -le 0.02) { continue }
    $m = $m * $FORCE
    $L = 0.299*$ao[$p+2] + 0.587*$ao[$p+1] + 0.114*$ao[$p]
    if ($estVague) {
      $k = $L / $LrefR
      if ($k -lt 0.75) { $k = 0.75 } elseif ($k -gt 1.15) { $k = 1.15 }
      $tr = $RR; $tg = $RG; $tb = $RB
    } else {
      $k = $L / $LrefG
      if ($k -lt 0.70) { $k = 0.70 } elseif ($k -gt 1.35) { $k = 1.35 }
      $tr = $GR; $tg = $GG; $tb = $GB
    }
    $vb = $ao[$p]   * (1 - $m) + $tb * $k * $m
    $vg = $ao[$p+1] * (1 - $m) + $tg * $k * $m
    $vr = $ao[$p+2] * (1 - $m) + $tr * $k * $m
    $ao[$p]   = [byte][math]::Min(255, [math]::Max(0, [math]::Round($vb)))
    $ao[$p+1] = [byte][math]::Min(255, [math]::Max(0, [math]::Round($vg)))
    $ao[$p+2] = [byte][math]::Min(255, [math]::Max(0, [math]::Round($vr)))
  }
}
[System.Runtime.InteropServices.Marshal]::Copy($ao, 0, $ro.Scan0, $n)
$bmpO.UnlockBits($ro)
$bmpC.UnlockBits($rc)

$enc = [System.Drawing.Imaging.ImageCodecInfo]::GetImageEncoders() | Where-Object { $_.MimeType -eq 'image/jpeg' }
$prm = New-Object System.Drawing.Imaging.EncoderParameters 1
$prm.Param[0] = New-Object System.Drawing.Imaging.EncoderParameter ([System.Drawing.Imaging.Encoder]::Quality), 94L
$out = "$pub\login-bg-vert.jpg"
$bmpO.Save($out, $enc, $prm)
$bmpO.Dispose(); $bmpC.Dispose(); $imgO.Dispose(); $imgC.Dispose()
"ecrit: $out"
