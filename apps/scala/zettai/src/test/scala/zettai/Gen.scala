package zettai

/** 乱数ではなく、小さな範囲のすべてのパターンを並べる。 */
object Gen:

  def universe: List[ToDoListEvent] =
    val users = List(User("uberto"), User("alice"))
    val names = List(ListName("book"), ListName("food"))
    val items = List(ToDoItem.todo("a"), ToDoItem.todo("b"))
    val created = for u <- users; n <- names yield ToDoListEvent.ListCreated(u, n)
    val added   = for u <- users; n <- names; i <- items yield ToDoListEvent.ItemAdded(u, n, i)
    created ::: added

  def ofLength[A](k: Int, xs: List[A]): List[List[A]] =
    if k <= 0 then List(Nil)
    else for x <- xs; rest <- ofLength(k - 1, xs) yield x :: rest

  /** 長さ 0 から n までの、すべての出来事の列。 */
  def eventListsUpTo(n: Int): List[List[ToDoListEvent]] =
    (0 to n).toList.flatMap(k => ofLength(k, universe))
