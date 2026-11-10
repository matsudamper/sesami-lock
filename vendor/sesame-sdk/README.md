# sesame-sdk (vendored)

CANDY HOUSE 公式 Android SDK の `sesameSdk` モジュールを、サブモジュールで取り込んでビルドする Gradle モジュール。

- 取り込み元: https://github.com/CANDY-HOUSE/SesameSDK_Android_with_DemoApp
- サブモジュール: https://github.com/matsudamper/SesameSDK_Android_with_DemoApp の `sesami-lock-patch` ブランチ（取り込み元の fork）
- パッチの土台にした取り込み元のコミット: `9b1e8a882030a4df76e0b260ff4bafa0580f8cd9`
- ライセンス: MIT（`upstream/LICENSE` 参照）

`upstream/` がサブモジュール。`build.gradle.kts` は本リポジトリのビルド構成に合わせて書き直しており、`upstream/sesameSdk` のソース・`AndroidManifest.xml`・`consumer-rules.pro` をそのまま使う。
パッチは `sesami-lock-patch` ブランチに安全上必要な最小限のものだけをコミットし、該当行に `sesami-lock patch` と記している。

## パッチ一覧
- `sesameSdk/src/main/java/co/candyhouse/sesame/ble/os3/base/CHSesameOS3.kt`: GATT 切断ごとに接続世代を進め、切断前に `sendCommand` の待ち行列へ積まれたコマンドは再接続後に送らずに破棄する。応答待ちのまま時間切れになった解錠が、後から別の接続で送られるのを防ぐため

## 更新手順

fork の `sesami-lock-patch` ブランチに取り込み元の master をマージして push する。

```sh
git -C vendor/sesame-sdk/upstream remote add candyhouse https://github.com/CANDY-HOUSE/SesameSDK_Android_with_DemoApp.git
```

```sh
git -C vendor/sesame-sdk/upstream fetch candyhouse master
```

```sh
git -C vendor/sesame-sdk/upstream checkout sesami-lock-patch
```

```sh
git -C vendor/sesame-sdk/upstream merge candyhouse/master
```

```sh
git -C vendor/sesame-sdk/upstream push origin sesami-lock-patch
```

本リポジトリでサブモジュールの参照を更新してコミットする。

```sh
git add vendor/sesame-sdk/upstream
```

アプリ側から直接参照せず、`:sesame:ble` モジュール経由で利用する。
