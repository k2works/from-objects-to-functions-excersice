# スパイク

**Unit 1 で後戻りの効かない判断をするために書いたコードです。** 捨てずに残しています。

判断の記録は ADR にあります。ここにあるのは**その判断の根拠になった実物**です。

| ディレクトリ | 何を確かめたか | 結果 | ADR |
| :--- | :--- | :--- | :--- |
| `sync-postgres/` | 同期の `postgres` クレートで書けるか。**Unit 5 でエラーの包み方とテストの分離も** | 書けた。`Display` は `"db error"` だけで `source()` を辿る要あり | [ADR-024](../../../../docs/adr/ADR-024-no-async.md) |
| `http-tiny_http/` | 縦串が書けるか。依存 5 クレート・ビルド 7 秒 | 採用 | [ADR-025](../../../../docs/adr/ADR-025-tiny-http.md) |
| `http-rouille/` | 同上。依存 123 クレート・ビルド 58 秒 | 採らず | 同上 |
| `http-axum/` | 同上。依存 61 クレート・ビルド 70 秒。**tokio 必須** | 採らず | 同上 |
| `di-shapes/` | 関数型 DI の 3 案が、第 5 章のクロージャ合成まで書けるか | 案 A と案 C は書けない | [ADR-030](../../../../docs/adr/ADR-030-functional-di-shape.md) |
| `prop-frameworks/` | 性質テストの既製品。依存 38 / 21 クレート、ビルド 26.77 / 12.82 秒 | **入れず自作**（[ADR-017](../../../../docs/adr/ADR-017-own-property-testing.md) を踏襲） | 第 5 章 |
| `match-exhaustiveness/` | `match` の網羅が遷移表の穴をどこまで見るか | **枝の有無だけ。行き先の誤りは見ない** | 第 6 章 |
| `error-crates/` | 失敗の型に既製品を入れるか。依存 +10 / +1、ビルド +12.29 / +2.15 秒 | **入れず自前の `enum`**（[ADR-031](../../../../docs/adr/ADR-031-own-error-enum.md)） | 第 7 章 |
| `tx-shapes/` | トランザクションの境界。3 案 + 落ちる 2 形 | **案 A スコープ貸し。** ADR-011 の決定 2 は移植できない（E0478） | 第 10 章 |

## workspace から外してある

`Cargo.toml` の `exclude` に入れています。理由は 2 つです。

- **`sync-postgres` は DB が要る。** CI で走らせるものではない
- **HTTP の 3 つは同じポートを使う。** 並列に走らせられない

`just check` の対象外です。動かすときは個別に入って `cargo run` します。

```bash
docker compose up -d zettai-db   # sync-postgres のときだけ
cd spikes/sync-postgres && cargo run
```
