# Zettai Kotlin FP

Kotlin、Arrow、kotlinx.coroutines を使う Zettai 連載の動作確認済み実装です。

## 実行環境

- JDK 25
- Gradle Wrapper 9.7.0
- Docker（Testcontainers の PostgreSQL 結合テスト用）

```bash
nix develop .#kotlin-fp
cd apps/kotlin-fp/zettai
bash ./gradlew check
```

Windows では `gradlew.bat check` を使います。`check` は Kotest、Cucumber、Testcontainers を含む単一の品質ゲートです。

Nix を使わない場合は `java -version` が 25 を示すこと、`docker version` で daemon に接続できることを確認してください。初回は Gradle の依存関係と PostgreSQL image を取得するため、ネットワーク接続が必要です。
