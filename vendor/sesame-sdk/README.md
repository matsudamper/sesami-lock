# sesame-sdk (vendored)

CANDY HOUSE 公式 Android SDK の `sesameSdk` モジュールを、サブモジュールで取り込んでビルドする Gradle モジュール。

- 取り込み元: https://github.com/CANDY-HOUSE/SesameSDK_Android_with_DemoApp
- 固定コミット: `9b1e8a882030a4df76e0b260ff4bafa0580f8cd9`
- ライセンス: MIT（`upstream/LICENSE` 参照）

## 構成

| ID | パス | 内容 |
|---|---|---|
| 1 | `upstream/` | 取り込み元リポジトリのサブモジュール。無改変 |
| 2 | `patched/` | パッチを当てたソース。`upstream/sesameSdk/src/main/java` と同じ相対パスに置く |
| 3 | `build.gradle.kts` | 本リポジトリのビルド構成に合わせて書き直したもの |

ビルド時は `upstream/sesameSdk/src/main/java` から `patched/` にあるファイルと同じパスのものを除いてコピーし、`patched/` と合わせてコンパイルする。
`AndroidManifest.xml` と `consumer-rules.pro` は `upstream/sesameSdk` のものをそのまま使う。
パッチを当てた行には `sesami-lock patch` と記している。

## パッチ一覧
- `patched/co/candyhouse/sesame/ble/os3/base/CHSesameOS3.kt`: GATT 切断ごとに接続世代を進め、切断前に `sendCommand` の待ち行列へ積まれたコマンドは再接続後に送らずに破棄する。応答待ちのまま時間切れになった解錠が、後から別の接続で送られるのを防ぐため

## 更新手順

```sh
git -C vendor/sesame-sdk/upstream fetch origin
```

```sh
git -C vendor/sesame-sdk/upstream checkout <新しいコミット>
```

`patched/` の各ファイルは、新しいコミットの同じファイルにパッチを当て直して置き換える。差分は以下で確認できる。

```sh
git diff --no-index vendor/sesame-sdk/upstream/sesameSdk/src/main/java/co/candyhouse/sesame/ble/os3/base/CHSesameOS3.kt vendor/sesame-sdk/patched/co/candyhouse/sesame/ble/os3/base/CHSesameOS3.kt
```

最後にこの README の固定コミットを書き換え、`git add vendor/sesame-sdk/upstream` でサブモジュールの参照を更新する。

アプリ側から直接参照せず、`:sesame:ble` モジュール経由で利用する。
