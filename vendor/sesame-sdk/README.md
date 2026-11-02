# sesame-sdk (vendored)

CANDY HOUSE 公式 Android SDK の `sesameSdk` モジュールを、再現性のためにソース固定で取り込んだもの。

- 取り込み元: https://github.com/CANDY-HOUSE/SesameSDK_Android_with_DemoApp
- 固定コミット: `9b1e8a882030a4df76e0b260ff4bafa0580f8cd9`
- ライセンス: MIT（`LICENSE` 参照）

`consumer-rules.pro` は取り込み元から無改変。`build.gradle.kts` は本リポジトリのビルド構成に合わせて書き直している。
`src/` には安全上必要な最小限のパッチだけを当てており、該当行に `sesami-lock patch` と記している。

## パッチ一覧
- `os3/base/CHSesameOS3.kt`: GATT 切断ごとに接続世代を進め、切断前に `sendCommand` の待ち行列へ積まれたコマンドは再接続後に送らずに破棄する。応答待ちのまま時間切れになった解錠が、後から別の接続で送られるのを防ぐため
更新するときは固定コミットを差し替え、`src/` を丸ごと置き換える。
アプリ側から直接参照せず、`:sesame:ble` モジュール経由で利用する。
