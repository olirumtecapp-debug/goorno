$ErrorActionPreference = "Stop"
$ProjectPath = "D:\GoOrNo"
$AppName = "GoOrNo"
$PackageId = "com.creativeam.goorno"
$AppUrl = "https://goorno-app.vercel.app"
$IconPath = "D:\GoOrNo\client\public\icon-512.png"
$PrimaryColor = "#073B32"
$AccentColor = "#7C9303"

$androidRoot = Join-Path $ProjectPath "android"
$appRoot = Join-Path $androidRoot "app"
$mainRes = Join-Path $appRoot "src\main\res"
$pkgSubPath = $PackageId -replace '\.', '\'
$javaDir = Join-Path $appRoot "src\main\java\$pkgSubPath"

$dirsToCreate = @(
    $androidRoot,
    $appRoot,
    $javaDir,
    (Join-Path $mainRes "layout"),
    (Join-Path $mainRes "values"),
    (Join-Path $mainRes "xml"),
    (Join-Path $mainRes "mipmap-mdpi"),
    (Join-Path $mainRes "mipmap-hdpi"),
    (Join-Path $mainRes "mipmap-xhdpi"),
    (Join-Path $mainRes "mipmap-xxhdpi"),
    (Join-Path $mainRes "mipmap-xxxhdpi"),
    (Join-Path $ProjectPath ".github\workflows")
)

foreach ($d in $dirsToCreate) {
    if (-not (Test-Path $d)) {
        New-Item -ItemType Directory -Path $d -Force | Out-Null
    }
}

# 1. settings.gradle
$settingsGradle = @"
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "GoOrNo"
include ':app'
"@
[System.IO.File]::WriteAllText((Join-Path $androidRoot "settings.gradle"), $settingsGradle, [System.Text.UTF8Encoding]::new($false))

# 2. build.gradle (raiz)
$rootBuildGradle = @"
plugins {
    id 'com.android.application' version '8.2.2' apply false
}

tasks.register('clean', Delete) {
    delete rootProject.buildDir
}
"@
[System.IO.File]::WriteAllText((Join-Path $androidRoot "build.gradle"), $rootBuildGradle, [System.Text.UTF8Encoding]::new($false))

# 3. gradle.properties
$gradleProps = @"
org.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8
android.useAndroidX=true
android.enableJetifier=true
android.nonTransitiveRClass=true
"@
[System.IO.File]::WriteAllText((Join-Path $androidRoot "gradle.properties"), $gradleProps, [System.Text.UTF8Encoding]::new($false))

# 4. app/build.gradle
$appBuildGradle = @"
plugins {
    id 'com.android.application'
}

android {
    namespace '$PackageId'
    compileSdk 34

    defaultConfig {
        applicationId "$PackageId"
        minSdk 24
        targetSdk 34
        versionCode 1
        versionName "1.0.0"
    }

    buildTypes {
        release {
            minifyEnabled false
            proguardFiles getDefaultProguardFile('proguard-android-optimize.txt'), 'proguard-rules.pro'
        }
    }
    compileOptions {
        sourceCompatibility JavaVersion.VERSION_17
        targetCompatibility JavaVersion.VERSION_17
    }
}

dependencies {
    implementation 'androidx.appcompat:appcompat:1.6.1'
    implementation 'com.google.android.material:material:1.11.0'
    implementation 'androidx.swiperefreshlayout:swiperefreshlayout:1.1.0'
    implementation 'androidx.webkit:webkit:1.10.0'
}
"@
[System.IO.File]::WriteAllText((Join-Path $appRoot "build.gradle"), $appBuildGradle, [System.Text.UTF8Encoding]::new($false))

[System.IO.File]::WriteAllText((Join-Path $appRoot "proguard-rules.pro"), "# Proguard", [System.Text.UTF8Encoding]::new($false))

# 5. AndroidManifest.xml with AccessibilityService + SYSTEM_ALERT_WINDOW
$manifest = @"
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    <uses-permission android:name="android.permission.MODIFY_AUDIO_SETTINGS" />
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />

    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:roundIcon="@mipmap/ic_launcher_round"
        android:supportsRtl="true"
        android:hardwareAccelerated="true"
        android:usesCleartextTraffic="true"
        android:theme="@style/Theme.App">

        <activity
            android:name=".MainActivity"
            android:exported="true"
            android:screenOrientation="portrait"
            android:configChanges="orientation|screenSize|screenLayout|keyboardHidden"
            android:windowSoftInputMode="adjustResize">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>

        <service
            android:name=".GoOrNoAccessibilityService"
            android:exported="true"
            android:label="GoOrNo Copiloto (Uber / 99 / inDrive)"
            android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE">
            <intent-filter>
                <action android:name="android.accessibilityservice.AccessibilityService" />
            </intent-filter>
            <meta-data
                android:name="android.accessibilityservice"
                android:resource="@xml/accessibility_service_config" />
        </service>
    </application>

</manifest>
"@
[System.IO.File]::WriteAllText((Join-Path $appRoot "src\main\AndroidManifest.xml"), $manifest, [System.Text.UTF8Encoding]::new($false))

# 6. res/xml/accessibility_service_config.xml
$accXml = @"
<?xml version="1.0" encoding="utf-8"?>
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeWindowStateChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:description="@string/accessibility_desc"
    android:notificationTimeout="120" />
"@
[System.IO.File]::WriteAllText((Join-Path $mainRes "xml\accessibility_service_config.xml"), $accXml, [System.Text.UTF8Encoding]::new($false))

# 7. Res values
$strings = @"
<resources>
    <string name="app_name">GoOrNo</string>
    <string name="accessibility_desc">GoOrNo Copiloto: Lê automaticamente os cards de chamada da Uber, 99 e inDrive na tela para calcular R$/km, R$/hora, Lucro Líquido e abrir o Street View de segurança.</string>
</resources>
"@
[System.IO.File]::WriteAllText((Join-Path $mainRes "values\strings.xml"), $strings, [System.Text.UTF8Encoding]::new($false))

$colors = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <color name="primary">#073B32</color>
    <color name="primary_dark">#04241E</color>
    <color name="accent">#7C9303</color>
    <color name="background">#04241E</color>
</resources>
"@
[System.IO.File]::WriteAllText((Join-Path $mainRes "values\colors.xml"), $colors, [System.Text.UTF8Encoding]::new($false))

$styles = @"
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.App" parent="Theme.MaterialComponents.DayNight.NoActionBar">
        <item name="colorPrimary">@color/primary</item>
        <item name="colorPrimaryDark">@color/primary_dark</item>
        <item name="colorAccent">@color/accent</item>
        <item name="android:statusBarColor">@color/primary_dark</item>
        <item name="android:navigationBarColor">@color/primary_dark</item>
    </style>
</resources>
"@
[System.IO.File]::WriteAllText((Join-Path $mainRes "values\styles.xml"), $styles, [System.Text.UTF8Encoding]::new($false))

# 8. Layout activity_main.xml
$layout = @"
<?xml version="1.0" encoding="utf-8"?>
<RelativeLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="match_parent"
    android:background="@color/background">

    <ProgressBar
        android:id="@+id/progress_bar"
        style="?android:attr/progressBarStyleHorizontal"
        android:layout_width="match_parent"
        android:layout_height="4dp"
        android:layout_alignParentTop="true"
        android:max="100"
        android:visibility="gone"
        android:indeterminate="false"
        android:progressDrawable="@android:drawable/progress_horizontal"
        android:elevation="10dp" />

    <androidx.swiperefreshlayout.widget.SwipeRefreshLayout
        android:id="@+id/swipe_refresh"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:layout_below="@id/progress_bar">

        <WebView
            android:id="@+id/webview"
            android:layout_width="match_parent"
            android:layout_height="match_parent" />

    </androidx.swiperefreshlayout.widget.SwipeRefreshLayout>

</RelativeLayout>
"@
[System.IO.File]::WriteAllText((Join-Path $mainRes "layout\activity_main.xml"), $layout, [System.Text.UTF8Encoding]::new($false))

# 9. Copiar icones
if (Test-Path $IconPath) {
    $densities = @("mipmap-mdpi", "mipmap-hdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi")
    foreach ($density in $densities) {
        $destDir = Join-Path $mainRes $density
        Copy-Item -Path $IconPath -Destination (Join-Path $destDir "ic_launcher.png") -Force
        Copy-Item -Path $IconPath -Destination (Join-Path $destDir "ic_launcher_round.png") -Force
    }
}

Write-Output "Android base structure created!"
