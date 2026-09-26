package zettai.domain

/** ToDo リストを取り出す。見つからなければ null。 */
typealias ToDoListFetcher = (User, ListName) -> ToDoList?

/**
 * ドメインの入口。
 *
 * アダプタを関数の型で受け取る。インターフェースを定義しないので、
 * 呼ぶ側はラムダを渡すだけでよく、テスト用の実装クラスが要らない。
 */
class ToDoListHub(private val fetchList: ToDoListFetcher) {

    fun getList(user: User, listName: ListName): ToDoList? = fetchList(user, listName)
}
