package net.matsudamper.sesamilock.ui.add

import net.matsudamper.sesamilock.sesame.core.key.KeyImportError

internal fun KeyImportError.message(): String = when (this) {
    KeyImportError.EMPTY -> "鍵データを入力してください"
    KeyImportError.UNRECOGNIZED_FORMAT -> "対応していない形式です。従来形式の共有 QR の URL（ssm://...）か、鍵の JSON を入力してください"
    KeyImportError.ENCRYPTED_TOKEN_QR -> "暗号化された共有 QR（10 分間有効の形式）は読み込めません。公式アプリで従来形式の QR を発行してください"
    KeyImportError.INVALID_ENCODING -> "鍵データを読み取れませんでした"
    KeyImportError.UNSUPPORTED_MODEL -> "この機種には対応していません（対応: SESAME 3 / 4 / 5 / 6 / miwa 系のドア錠）"
    KeyImportError.INVALID_UUID -> "UUID の形式が正しくありません"
    KeyImportError.INVALID_KEY_FORMAT -> "鍵の形式が正しくありません"
    KeyImportError.SERVER_SIGNED_GUEST_KEY -> "サーバー署名が必要なゲスト鍵です。オーナーまたはマネージャーの鍵を使ってください"
}
