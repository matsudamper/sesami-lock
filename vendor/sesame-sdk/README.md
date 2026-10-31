# sesame-sdk (vendored)

CANDY HOUSE 公式 Android SDK の `sesameSdk` モジュールを、再現性のためにソース固定で取り込んだもの。

- 取り込み元: https://github.com/CANDY-HOUSE/SesameSDK_Android_with_DemoApp
- 固定コミット: `9b1e8a882030a4df76e0b260ff4bafa0580f8cd9`
- ライセンス: MIT（`LICENSE` 参照）

`src/` と `consumer-rules.pro` は取り込み元から無改変。`build.gradle.kts` のみ本リポジトリのビルド構成に合わせて書き直している。
更新するときは固定コミットを差し替え、`src/` を丸ごと置き換える。
アプリ側から直接参照せず、`:sesame:ble` モジュール経由で利用する。
