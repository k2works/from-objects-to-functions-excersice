# スパイク: フォームの POST と 302（Unit 6 / スパイク 3）

**相手が返した形で確かめる**（Unit 5 Try 2）。第 9 章では自前の JSON を
自分の出力だけで往復させ、`jsonb` の正規化で落ちた。同じ失敗を繰り返さない。

```bash
cargo run &
curl -s -X POST -d 'user=uberto&newname=reading' http://localhost:8099/echo
curl -s -X POST --data-urlencode 'newname=読む 本&"<x>"' http://localhost:8099/echo
curl -si -X POST -d 'newname=x' http://localhost:8099/redirect | head -3
```

## 見つかったこと

### 1. ボディはパーセントエンコードされて届く

```text
body="user=&newname=%E8%AA%AD%E3%82%80+%E6%9C%AC%26%22%3Cx%3E%22"
```

送ったのは `読む 本&"<x>"`。**空白は `+`、それ以外は `%XX`** になる。

**第 4 章から使っている `description_in` は `+` しか戻していない。**

```text
Some("%E8%AA%AD%E3%82%80 %E6%9C%AC%26%22%3Cx%3E%22")
```

ASCII だけの説明（`review chapter`）を使っていたので、**受け入れテストは通り続けていた**。
第 11 章で直す。

### 2. `&` と `=` は `%26` / `%3D` になるので、先に分割してよい

値の中に `&` があっても `%26` で届くため、`split('&')` が壊れない。
**分割してから戻す**順序で正しい。

### 3. 302 は `Location` ヘッダと `StatusCode(302)` で書ける

```text
HTTP/1.1 302 Found
```

`curl -L` は 302 のあとに POST を再送する（なでしこ3 版 Unit 6 で踏んだ）。
**テストでは `-L` を使わない。**

### 4. `Content-Type` は `application/x-www-form-urlencoded`

```text
headers=["Host: ...", "Content-Length: 27", "Content-Type: application/x-www-form-urlencoded"]
```
