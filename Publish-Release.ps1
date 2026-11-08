param([Parameter(Mandatory)][string]$Apk,[Parameter(Mandatory)][string]$NotesFile,[string]$Repository='amosshestakov/VladOS',[string]$AndroidHome=$env:ANDROID_HOME,[string]$BuildToolsVersion='35.0.0',[string]$Gh='gh')
$ErrorActionPreference='Stop'
if(!$AndroidHome){$AndroidHome=$env:ANDROID_SDK_ROOT}
if(!$AndroidHome){throw 'Set ANDROID_HOME'}
$bt=Join-Path $AndroidHome "build-tools/$BuildToolsVersion";$win=$env:OS -eq 'Windows_NT';$exe=if($win){'.exe'}else{''}
$apkPath=(Resolve-Path -LiteralPath $Apk).Path;$notesPath=(Resolve-Path -LiteralPath $NotesFile).Path
$badging=& (Join-Path $bt "aapt$exe") dump badging $apkPath
if($LASTEXITCODE){throw 'Cannot inspect APK'}
$line=$badging|Select-Object -First 1
if($line -notmatch "name='ru.ilya.alitahome' versionCode='(\d+)' versionName='([^']+)'"){throw 'Unexpected APK package'}
$code=[int]$Matches[1];$version=$Matches[2]
if($version -notmatch '^\d+\.\d+\.\d+$'){throw 'Release version must use X.Y.Z'}
$tag='v'+$version
$signer=& (Join-Path $bt $(if($win){'apksigner.bat'}else{'apksigner'})) verify --print-certs $apkPath
if($LASTEXITCODE){throw 'APK signature is invalid'}
if(($signer -join "`n") -notmatch 'certificate SHA-256 digest: f37cc90fcd9417befec38765bc59e32ab780610010a4bbbd67e3cb137b7cf58b'){throw 'APK is signed by a different key; installed TVs will reject it.'}
$stage=Join-Path ([IO.Path]::GetTempPath()) ('vlados-release-'+[guid]::NewGuid().ToString('N'));New-Item -ItemType Directory $stage|Out-Null
$name="VladOS-$version.apk";Copy-Item -LiteralPath $apkPath -Destination (Join-Path $stage $name)
$metadata=[ordered]@{packageName='ru.ilya.alitahome';versionCode=$code;versionName=$version;apkUrl="https://github.com/$Repository/releases/download/$tag/$name";size=(Get-Item -LiteralPath $apkPath).Length;sha256=(Get-FileHash -LiteralPath $apkPath -Algorithm SHA256).Hash.ToLowerInvariant();notes=[IO.File]::ReadAllText($notesPath)}
$json=Join-Path $stage 'update.json';[IO.File]::WriteAllText($json,($metadata|ConvertTo-Json -Depth 5),[Text.UTF8Encoding]::new($false))
# A draft keeps TVs from seeing an incomplete upload. Tag must already be pushed.
& $Gh release create $tag (Join-Path $stage $name) $json --repo $Repository --verify-tag --draft --title "VladOS $version" --notes-file $notesPath
if($LASTEXITCODE){throw 'Draft creation/upload failed; nothing was published to TVs.'}
& $Gh release edit $tag --repo $Repository --draft=false --latest
if($LASTEXITCODE){throw 'Release remains a draft. Check it on GitHub before publishing.'}
Write-Output "Published https://github.com/$Repository/releases/tag/$tag"
