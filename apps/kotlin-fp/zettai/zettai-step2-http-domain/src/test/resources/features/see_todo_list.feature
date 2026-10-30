# language: ja
機能: ToDo リストを見る

  シナリオ: ToDo リストの項目を見る
    前提 ユーザー "uberto" がリスト "book" に次の項目を持っている
      | write chapter |
      | publish book  |
      | read <today> & "FP" |
    もし ユーザーがリスト "book" を見る
    ならば 項目は次のとおりである
      | write chapter |
      | publish book  |
      | read <today> & "FP" |

  シナリオ: 存在しない ToDo リストは見つからない
    前提 ユーザー "uberto" はリストを持っていない
    もし ユーザーがリスト "missing" を見る
    ならば リストは見つからない
