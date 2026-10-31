# sesami-lock

CANDY HOUSE SESAME スマートロックを操作する個人利用の Android アプリ。
最優先の目的は「意図しない解錠を防ぐ」こと。トグル操作・状態の取り違え・重複送信・自動再送をしない。

## ビルド

JDK 25とAndroid SDKを用意して、以下を実行します。

```sh
./gradlew assembleDebug ktlintCheck detekt
```

## モジュール構成

| モジュール | 役割 |
|---|---|
| `:app` | Compose UI、ViewModel、BiometricPrompt、依存の組み立て |
| `:sesame:core` | `LockIntent`（LOCK / UNLOCK のみ）、状態モデル、`LockTransport` ポート、`LockCommandCoordinator`、鍵取り込み、診断。Android 非依存 |
| `:sesame:ble` | 公式 SDK による BLE 経路 `SesameBleTransport` |
| `:sesame:cloud` | Web API による クラウド経路 `SesameCloudTransport` |
| `:sesame:credential` | Android Keystore（AES-GCM）で暗号化した鍵情報の保存 `EncryptedLockRepository` |
| `:vendor:sesame-sdk` | 公式 SDK `sesameSdk` のソース固定取り込み（commit `9b1e8a8`） |

`:app` 以外はアプリに依存しないライブラリとして使える。`LockTransport` を実装すれば別の経路も同じ安全制御に載せられる。

## 安全設計

- 操作は `LockIntent.LOCK` / `LockIntent.UNLOCK` の 2 つだけ。状態を見て反転させる `toggle()` と `cmd=88` は使わない（`NoToggleUsageTest` で検査）
- 施錠はタップ 1 回。解錠は「600ms 長押し → 確認ダイアログ → 端末認証」の後に 1 回だけ送る
- 解錠の送信直前に、確認済み・端末認証済み・認証から 10 秒以内・アプリが前面・接続先一致・経路の認証済み・同じ鍵の未完了操作なし、をまとめて確認する
- 鍵ごとに未完了の操作は 1 件まで。連打や二重コールバックで複数の命令は出ない
- 送信後はタイムアウトや 5xx でも再送しない。BLE が失敗してもクラウドへ自動で切り替えない
- 未完了の要求はメモリにだけ持つ。プロセスが終われば消え、後から実行されることはない
- 命令の受理（ACK）と物理状態の確認を分ける。受理後に目標状態が一定時間保たれたときだけ「確認済み」と表示する
- SDK の `Unlocked` は「施錠範囲外」を含むため、解錠は既定では「施錠範囲外 / 動作中」と表示し「解錠（確認済み）」にはしない
- 古い接続世代・古い時刻の状態通知は新しい状態を上書きしない
- 鍵情報は Keystore で暗号化して `noBackupFilesDir` に保存し、バックアップ・端末移行の対象外。画面は `FLAG_SECURE`
- 診断記録はメモリ内のみで、鍵・API キー・署名・完全な UUID を含まない

## 鍵の登録

- BLE: 公式アプリの「鍵をシェア」で従来形式（無期限）の QR を発行し、その URL（`ssm://UI?t=sk&...`）を貼り付ける。エクスポート済みの鍵 JSON も読み込める。暗号化トークン形式の QR とサーバー署名が必要なゲスト鍵は非対応
- クラウド: SESAME Biz 開発者ページの API キー、SESAME の UUID と秘密鍵を入力する（Hub 3 が必要）

対応機種: SESAME 3 / 4 / 5 / 5 Pro / 5 US / 6 / 6 Pro / 6 Pro SlidingDoor / miwa。Bot / Bike / Touch / Hub は操作対象にしない。

## 実機で未検証の事項

実機での確認はまだしていない。次の点は実機で確かめてから使うこと。最初の試験はドアを開けた状態で、物理鍵を手元に置いて行う。

- 従来形式 QR のバイト配置（`productType(1) + secretKey(16) + publicKey(OS2:64 / OS3:4) + keyIndex(2) + UUID(16)`）と BLE ログイン
- SESAME 5 系の `isStop` フラグの意味。施錠の確認はこれが「停止」を表す前提
- 解錠の厳格判定（`UnlockVerificationPolicy.StoppedNearUnlockPosition`）の許容値。既定は無効
- Web API 署名のバイト列（公式コード例どおり Unix 秒の UInt32 LE の [1..3]）とクラウド状態の鮮度
- 各タイムアウト値（`LockSafetyConfig.Default`）
