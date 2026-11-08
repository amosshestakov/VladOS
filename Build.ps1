param([switch]$Unsigned,[string]$AndroidHome=$env:ANDROID_HOME,[string]$JdkHome=$env:JAVA_HOME,[string]$BuildToolsVersion='35.0.0')
$ErrorActionPreference='Stop'
if(!$AndroidHome){$AndroidHome=$env:ANDROID_SDK_ROOT}
if(!$AndroidHome -or !$JdkHome){throw 'Set ANDROID_HOME and JAVA_HOME (JDK17). Install platforms;android-28 and build-tools;35.0.0.'}
$project=$PSScriptRoot
$bt=Join-Path $AndroidHome "build-tools/$BuildToolsVersion"
$androidJar=Join-Path $AndroidHome 'platforms/android-28/android.jar'
$out=Join-Path $project 'dist'
$work=Join-Path ([IO.Path]::GetTempPath()) ('vlados-'+[guid]::NewGuid().ToString('N'))
New-Item -ItemType Directory $work,$out,(Join-Path $work 'classes'),(Join-Path $work 'dex') -Force|Out-Null
$win=$env:OS -eq 'Windows_NT';$exe=if($win){'.exe'}else{''};$d8=if($win){'d8.bat'}else{'d8'}
$env:JAVA_HOME=$JdkHome;$env:PATH=(Join-Path $JdkHome 'bin')+[IO.Path]::PathSeparator+$env:PATH
& (Join-Path $bt "aapt$exe") package -f -M (Join-Path $project 'app/src/main/AndroidManifest.xml') -S (Join-Path $project 'app/src/main/res') -A (Join-Path $project 'app/src/main/assets') -I $androidJar -F (Join-Path $work 'resources.apk')
if($LASTEXITCODE){throw 'Resource compilation failed'}
$sources=@(Get-ChildItem (Join-Path $project 'app/src/main/java') -Recurse -Filter '*.java'|ForEach-Object FullName)
& (Join-Path $JdkHome "bin/javac$exe") -encoding UTF-8 -source 8 -target 8 -classpath $androidJar -d (Join-Path $work 'classes') @sources
if($LASTEXITCODE){throw 'Java compilation failed'}
$classes=@(Get-ChildItem (Join-Path $work 'classes') -Recurse -Filter '*.class'|ForEach-Object FullName)
& (Join-Path $bt $d8) --min-api 23 --lib $androidJar --output (Join-Path $work 'dex') @classes
if($LASTEXITCODE){throw 'DEX compilation failed'}
$unsignedApk=Join-Path $work 'unsigned.apk';Copy-Item -LiteralPath (Join-Path $work 'resources.apk') -Destination $unsignedApk
& (Join-Path $JdkHome "bin/jar$exe") uf $unsignedApk -C (Join-Path $work 'dex') classes.dex
if($LASTEXITCODE){throw 'DEX packaging failed'}
$aligned=Join-Path $work 'aligned.apk'
& (Join-Path $bt "zipalign$exe") -f 4 $unsignedApk $aligned
if($LASTEXITCODE){throw 'APK alignment failed'}
if($Unsigned){Copy-Item -LiteralPath $aligned -Destination (Join-Path $out 'VladOS-unsigned.apk') -Force;Write-Output 'Unsigned build: dist/VladOS-unsigned.apk';exit 0}
foreach($name in 'VLADOS_KEYSTORE','VLADOS_STORE_PASSWORD','VLADOS_KEY_ALIAS','VLADOS_KEY_PASSWORD'){if(![Environment]::GetEnvironmentVariable($name)){throw "Set $name locally; signing secrets must not be committed."}}
$apk=Join-Path $out 'VladOS.apk'
& (Join-Path $JdkHome "bin/java$exe") -jar (Join-Path $bt 'lib/apksigner.jar') sign --ks $env:VLADOS_KEYSTORE --ks-key-alias $env:VLADOS_KEY_ALIAS --ks-pass env:VLADOS_STORE_PASSWORD --key-pass env:VLADOS_KEY_PASSWORD --out $apk $aligned
if($LASTEXITCODE){throw 'Signing failed'}
& (Join-Path $JdkHome "bin/java$exe") -jar (Join-Path $bt 'lib/apksigner.jar') verify --verbose $apk
if($LASTEXITCODE){throw 'Signature verification failed'}
& (Join-Path $bt "zipalign$exe") -c 4 $apk
if($LASTEXITCODE){throw 'Alignment verification failed'}
Get-FileHash -LiteralPath $apk -Algorithm SHA256|Select-Object Hash
