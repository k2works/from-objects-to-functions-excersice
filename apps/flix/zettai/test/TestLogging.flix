mod TestLogging {

    use Domain.{User, ListName, ToDoStatus}
    use Errors.ZettaiError
    use Commands.ToDoListCommand.{CreateToDoList, AddToDoItem, ChangeItemStatus, RenameToDoList}
    use Logs.LogRecord

    def uberto(): User = User.User("uberto")

    def book(): ListName = ListName.ListName("book")

    /// Logger 効果を、領域の中の Ref に集めるハンドラ。出力先をテストが握る。
    def collect(rc: Region[r], f: Unit -> a \ (Logger + r)): (a, List[String]) \ r = {
        let lines = Ref.fresh(rc, Nil);
        let result = run {
            f()
        } with handler Logger {
            def log(_severity, message, resume) = {
                Ref.put(Ref.get(lines) ::: (ToString.toString(message) :: Nil), lines);
                resume(())
            }
        };
        (result, Ref.get(lines))
    }

    /// 領域の内側で、集めた行だけを取り出す。
    def linesOf(rc: Region[r], f: Unit -> a \ (Logger + r)): List[String] \ r = {
        let (_, lines) = collect(rc, f);
        lines
    }

    /// 領域の内側で、結果の値だけを取り出す。
    def valueOf(rc: Region[r], f: Unit -> a \ (Logger + r)): a \ r = {
        let (value, _) = collect(rc, f);
        value
    }

    /// 呼ぶたびに step ずつ進む時計。所要時間が step になる。
    def steppingClock(rc: Region[r], step: Int64): (Unit -> Int64 \ r) \ r = {
        let now = Ref.fresh(rc, 1000i64);
        () -> {
            let t = Ref.get(now);
            Ref.put(t + step, now);
            t
        }
    }

    def decode(line: String): Result[String, LogRecord] =
        Result.flatMap(Logs.fromJson, Json.parse(line))

    def decodeAll(lines: List[String]): List[Result[String, LogRecord]] =
        List.map(decode, lines)

    @Test
    def aReadIsLoggedWithItsOutcomeAndDuration(): Unit \ Assert = {
        let lines = region rc {
            let hub = Logging.observeHub(steppingClock(rc, 7i64), Hub.inMemory(Map#{}));
            linesOf(rc, () -> hub#getList(uberto(), book()))
        };
        Assert.assertEq(
            expected = List#{Ok(Logs.LogRecord.LogRecord("hub", "getList", "ListNotFound", Some("uberto"), Some("book"), 7))},
            decodeAll(lines)
        )
    }

    @Test
    def aSuccessfulReadIsLoggedAsOk(): Unit \ Assert = {
        let lists = Map#{uberto() => List#{Domain.ToDoList.ToDoList(book(), Nil)}};
        let lines = region rc {
            let hub = Logging.observeHub(steppingClock(rc, 3i64), Hub.inMemory(lists));
            linesOf(rc, () -> hub#getList(uberto(), book()))
        };
        Assert.assertEq(
            expected = List#{Ok(Logs.LogRecord.LogRecord("hub", "getList", "ok", Some("uberto"), Some("book"), 3))},
            decodeAll(lines)
        )
    }

    @Test
    def eachCommandIsLoggedByKindUserAndListOnly(): Unit \ Assert = {
        let lines = region rc {
            let commands = CommandHub.commandHub(EventStore.inMemory(rc));
            let observed = Logging.observeCommandHub(steppingClock(rc, 1i64), commands);
            linesOf(rc, () -> List.map(observed#execute, List#{
                CreateToDoList(uberto(), book()),
                AddToDoItem(uberto(), book(), Domain.todo("secret item")),
                ChangeItemStatus(uberto(), book(), "secret item", ToDoStatus.Blocked),
                RenameToDoList(uberto(), book(), ListName.ListName("shop"))
            }))
        };
        Assert.assertEq(
            expected = List#{
                Ok(Logs.LogRecord.LogRecord("command", "CreateToDoList", "ok", Some("uberto"), Some("book"), 1)),
                Ok(Logs.LogRecord.LogRecord("command", "AddToDoItem", "ok", Some("uberto"), Some("book"), 1)),
                Ok(Logs.LogRecord.LogRecord("command", "ChangeItemStatus", "InvalidTransition", Some("uberto"), Some("book"), 1)),
                Ok(Logs.LogRecord.LogRecord("command", "RenameToDoList", "ok", Some("uberto"), Some("book"), 1))
            },
            decodeAll(lines)
        );
        // 項目の説明は、どの行にも現れない
        Assert.assertFalse(List.exists(line -> String.contains(substr = "secret", line), lines))
    }

    /// 呼ぶたびに失敗する、読み書きのストア。失敗の理由に、漏らしたくない値が入っている。
    def failingStore(): EventStore.EventStore[{}] = {
        load = () -> Err(ZettaiError.StorageFailure("password=hunter2 host=db.internal")),
        append = (_expected, _events) -> Err(ZettaiError.StorageFailure("password=hunter2 host=db.internal"))
    }

    @Test
    def aStorageFailureIsLoggedByCategoryNotByReason(): Unit \ Assert = {
        let lines = region rc {
            let store = Logging.observeStore(steppingClock(rc, 5i64), failingStore());
            linesOf(rc, () -> {
                let _ = store#load();
                store#append(0, Nil)
            })
        };
        Assert.assertEq(
            expected = List#{
                Ok(Logs.LogRecord.LogRecord("store", "load", "StorageFailure", None, None, 5)),
                Ok(Logs.LogRecord.LogRecord("store", "append", "StorageFailure", None, None, 5))
            },
            decodeAll(lines)
        );
        Assert.assertFalse(List.exists(line -> String.contains(substr = "hunter2", line) or String.contains(substr = "db.internal", line), lines))
    }

    @Test
    def everyKindOfFailureHasAFixedCategoryName(): Unit \ Assert = {
        let u = uberto();
        let l = book();
        Assert.assertEq(expected = "ListNotFound", Logging.category(ZettaiError.ListNotFound(u, l)));
        Assert.assertEq(expected = "ListAlreadyExists", Logging.category(ZettaiError.ListAlreadyExists(u, l)));
        Assert.assertEq(expected = "ItemNotFound", Logging.category(ZettaiError.ItemNotFound(u, l, "x")));
        Assert.assertEq(expected = "InvalidTransition", Logging.category(ZettaiError.InvalidTransition("x", ToDoStatus.Todo, ToDoStatus.Done)));
        Assert.assertEq(expected = "StorageFailure", Logging.category(ZettaiError.StorageFailure("anything")));
        Assert.assertEq(expected = "VersionConflict", Logging.category(ZettaiError.VersionConflict(1, 2)));
        Assert.assertEq(expected = "InvalidInput", Logging.category(ZettaiError.InvalidInput(Nil)))
    }

    @Test
    def decoratingDoesNotChangeTheResult(): Unit \ Assert = {
        let lists = Map#{uberto() => List#{Domain.ToDoList.ToDoList(book(), List#{Domain.todo("a")})}};
        let plain = Hub.inMemory(lists);
        let observed = region rc {
            let hub = Logging.observeHub(steppingClock(rc, 1i64), plain);
            valueOf(rc, () -> hub#getList(uberto(), book()))
        };
        Assert.assertEq(expected = plain#getList(uberto(), book()), observed)
    }

    def notFoundApp(_request: Http.Request): Http.Response =
        { status = 404, contentType = "text/plain", body = "missing" }

    @Test
    def aRequestIsLoggedByMethodAndStatusClassOnly(): Unit \ Assert = {
        
        let lines = region rc {
            let app = Logging.observeApp(steppingClock(rc, 2i64), notFoundApp);
            linesOf(rc, () -> app({ method = "GET", path = "/todo/secret-user/secret-list", body = "secret body" }))
        };
        Assert.assertEq(
            expected = List#{Ok(Logs.LogRecord.LogRecord("http", "GET", "4xx", None, None, 2))},
            decodeAll(lines)
        );
        // 経路にも本文にも利用者の入力が入る。行には、どちらも現れない
        Assert.assertFalse(List.exists(line -> String.contains(substr = "secret", line), lines))
    }

    @Test
    def readingTheCurrentStateTouchesTheStoreOnce(): Unit \ Assert = {
        let lines = region rc {
            let clock = steppingClock(rc, 1i64);
            let store = Logging.observeStore(clock, EventStore.inMemory(rc));
            let commands = Logging.observeCommandHub(clock, CommandHub.commandHub(store));
            linesOf(rc, () -> commands#current())
        };
        // ストアの読み出し 1 回と、コマンド側の 1 回。結果を捨てて、もう一度呼んではいけない
        Assert.assertEq(
            expected = List#{
                Ok(Logs.LogRecord.LogRecord("store", "load", "ok", None, None, 1)),
                Ok(Logs.LogRecord.LogRecord("command", "current", "ok", None, None, 3))
            },
            decodeAll(lines)
        )
    }

}
