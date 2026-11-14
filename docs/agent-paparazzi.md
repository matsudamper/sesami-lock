# Paparazzi

## 共通ルール

### UI 変更時
- スナップショットのケースを追加/更新して撮影する
- 撮影結果はセッションのチャットに貼る
- 画像なしで UI 作業完了にしない

## リポジトリ固有ルール

### 対象
- `:widget` の Glance ウィジェット。`LockWidgetSnapshotTest` で Glance の内容を RemoteViews にして撮影する
- Glance の `@Preview` は Paparazzi で撮れないため、ケースはテストに列挙する

### 画像の扱い
- スナップショット画像はコミットしない

### 実行
- `./gradlew test` では Paparazzi は自動除外

```bash
./gradlew :widget:recordPaparazziDebug
./gradlew :widget:recordPaparazziDebug -Dpaparazzi.filter="compact_locked"
```
