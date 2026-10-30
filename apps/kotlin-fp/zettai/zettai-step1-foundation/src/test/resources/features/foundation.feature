# language: ja
機能: 同じ仕様を異なる実行経路で検証する

  シナリオアウトライン: 単語の得点を求める
    前提 実行経路が "<経路>" である
    もし 単語 "Kotlin" の得点を求める
    ならば 得点は 6 である

    @domain
    例: domain
      | 経路   |
      | domain |

    @http
    例: HTTP
      | 経路   |
      | HTTP   |
