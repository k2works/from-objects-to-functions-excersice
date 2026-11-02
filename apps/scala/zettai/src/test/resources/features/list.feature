# language: ja
機能: ToDo リストを見る

  シナリオ: リストの項目が見える
    前提 利用者 "uberto" のリスト "book" に項目 "write chapter" がある
    もし 利用者 "uberto" がリスト "book" を見る
    ならば 項目 "write chapter" が見える

  シナリオ: 存在しないリストは見つからない
    前提 利用者 "uberto" のリスト "book" に項目 "write chapter" がある
    もし 利用者 "uberto" がリスト "missing" を見る
    ならば リストは見つからない
