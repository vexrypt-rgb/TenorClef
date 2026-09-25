$ErrorActionPreference = "Stop"
$root = "C:\Users\redfa\Documents\MinecraftDev\altoclef\vendor\tungsten-1.16.1\src\main\java\kaptainwutax\tungsten"

function Replace-Once($path, $old, $new, $label) {
  $text = [System.IO.File]::ReadAllText($path)
  if ($text.Contains($new.Trim())) {
    Write-Host "$label already applied"
    return $true
  }
  if (-not $text.Contains($old)) {
    Write-Host "$label ANCHOR MISSING"
    return $false
  }
  $idx = $text.IndexOf($old)
  $text2 = $text.Substring(0, $idx) + $new + $text.Substring($idx + $old.Length)
  [System.IO.File]::WriteAllText($path, $text2)
  Write-Host "$label patched"
  return $true
}

# Detect newline style from BlockShapeChecker
$bsc = Join-Path $root "helpers\BlockShapeChecker.java"
$raw = [System.IO.File]::ReadAllText($bsc)
$nl = if ($raw.Contains("`r`n")) { "`r`n" } else { "`n" }
Write-Host ("newline=" + ($(if ($nl -eq "`r`n") {"CRLF"} else {"LF"})))

$oldH = "	public static double getBlockHeight(BlockPos pos, WorldView world) {$nl		BlockState state = world.getBlockState(pos);"
$newH = "	public static double getBlockHeight(BlockPos pos, WorldView world) {$nl		if (world == null || pos == null) return 0;$nl		BlockState state = world.getBlockState(pos);"
[void](Replace-Once $bsc $oldH $newH "BSC-height")

$oldV = "	public static double getShapeVolume(BlockPos pos, WorldView world) {$nl		BlockState state = world.getBlockState(pos);"
$newV = "	public static double getShapeVolume(BlockPos pos, WorldView world) {$nl		if (world == null || pos == null) return 0;$nl		BlockState state = world.getBlockState(pos);"
[void](Replace-Once $bsc $oldV $newV "BSC-vol")

$rh = Join-Path $root "helpers\render\RenderHelper.java"
$rawRh = [System.IO.File]::ReadAllText($rh)
if (-not $rawRh.Contains("import kaptainwutax.tungsten.TungstenModDataContainer;")) {
  $impOld = "import kaptainwutax.tungsten.TungstenMod;"
  $impNew = "import kaptainwutax.tungsten.TungstenMod;$nl" + "import kaptainwutax.tungsten.TungstenModDataContainer;"
  if (-not $rawRh.Contains($impOld)) {
    # try TungstenModRenderContainer as nearby import anchor
    $impOld = "import kaptainwutax.tungsten.TungstenModRenderContainer;"
    $impNew = "import kaptainwutax.tungsten.TungstenModDataContainer;$nl" + "import kaptainwutax.tungsten.TungstenModRenderContainer;"
  }
  [void](Replace-Once $rh $impOld $impNew "RH-import")
}
$oldR = "	public static void renderPathSoFar(BlockNode n) {$nl		TungstenModRenderContainer.RENDERERS.clear();$nl		Vec3d currentPos = n.getPos(true);"
$newR = "	public static void renderPathSoFar(BlockNode n) {$nl		if (TungstenModDataContainer.world == null) return;$nl		TungstenModRenderContainer.RENDERERS.clear();$nl		Vec3d currentPos = n.getPos(true);"
[void](Replace-Once $rh $oldR $newR "RH-render")

$pf = Join-Path $root "path\PathFinder.java"
$oldP = "        if(active.get() || thread != null)return;$nl        active.set(true);"
$newP = "        if(active.get() || thread != null)return;$nl        if (world instanceof net.minecraft.world.World) {$nl            TungstenModDataContainer.world = (net.minecraft.world.World) world;$nl        }$nl        if (player != null) {$nl            TungstenModDataContainer.player = player;$nl        }$nl        active.set(true);"
[void](Replace-Once $pf $oldP $newP "PF-bind")

$tm = Join-Path $root "TungstenMod.java"
$oldT = "        ClientTickEvents.START_CLIENT_TICK.register((a) -> {$nl        	$nl        	boolean isRunning = TungstenModDataContainer.PATHFINDER.active.get() || TungstenModDataContainer.EXECUTOR.isRunning();"
$newT = "        ClientTickEvents.START_CLIENT_TICK.register((a) -> {$nl        	if (mc != null) {$nl        		TungstenModDataContainer.player = mc.player;$nl        		TungstenModDataContainer.world = mc.world;$nl        		TungstenModDataContainer.gameRenderer = mc.gameRenderer;$nl        	}$nl        	$nl        	boolean isRunning = TungstenModDataContainer.PATHFINDER.active.get() || TungstenModDataContainer.EXECUTOR.isRunning();"
[void](Replace-Once $tm $oldT $newT "TM-tick")

$bridge = "C:\Users\redfa\Documents\MinecraftDev\altoclef\src\main\java\adris\altoclef\movement\TungstenBridge.java"
$rawB = [System.IO.File]::ReadAllText($bridge)
$nlB = if ($rawB.Contains("`r`n")) { "`r`n" } else { "`n" }
$oldB = "            pathfinderFind.invoke(pathfinder, mc.world, target, mc.player);"
$newB = "            try { dataClass.getField(""world"").set(null, mc.world); dataClass.getField(""player"").set(null, mc.player); } catch (Throwable ignored) {}$nlB            pathfinderFind.invoke(pathfinder, mc.world, target, mc.player);"
[void](Replace-Once $bridge $oldB $newB "Bridge-bind")

Write-Host "VERIFY"
Select-String -Path $bsc -Pattern "world == null" | ForEach-Object { "BSC:" + $_.LineNumber + ":" + $_.Line.Trim() }
Select-String -Path $rh -Pattern "world == null" | ForEach-Object { "RH:" + $_.LineNumber + ":" + $_.Line.Trim() }
Select-String -Path $pf -Pattern "TungstenModDataContainer.world =" | ForEach-Object { "PF:" + $_.LineNumber + ":" + $_.Line.Trim() }
Select-String -Path $tm -Pattern "TungstenModDataContainer.world = mc.world" | ForEach-Object { "TM:" + $_.LineNumber + ":" + $_.Line.Trim() }
Select-String -Path $bridge -Pattern "dataClass.getField" | ForEach-Object { "BR:" + $_.LineNumber + ":" + $_.Line.Trim() }