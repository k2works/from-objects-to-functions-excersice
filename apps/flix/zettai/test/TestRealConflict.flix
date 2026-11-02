mod TestRealConflict {

    import java.lang.Runnable
    import java.lang.Thread
    import java.util.Objects
    import java.util.concurrent.atomic.AtomicReference

    use Domain.{User, ListName}
    use Domain.Errors.ZettaiError
    use Domain.Events.ToDoListEvent

    def uberto(): User = User.User("uberto")

    /// 本当に競合させる。時間や運には頼らず、手順を組む。
    ///
    /// 1. 接続 A が版 1 を挿入し、コミットしない
    /// 2. 別スレッド B が append(0, ...) を呼ぶ。版の確認は A が未コミットなので通り、
    ///    挿入で A のロック待ちになる（一意制約）
    /// 3. B が待っている（まだ終わっていない）ことを確かめてから、A がコミットする
    /// 4. B は一意制約違反（23505）になり、VersionConflict が返る
    ///
    /// 3 で B が終わっていないことを確かめるので、「版の確認で断られた」経路とは区別できる。
    @Test
    def aRealRaceEndsInAVersionConflictThroughTheUniqueConstraint(): Unit \ {Assert, IO} = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        let table = Adapter.Postgres.PostgresStore.freshTableName();
        match Adapter.Postgres.PostgresStore.open(config, table) {
            case Err(e)    => Assert.assertEq(expected = "opened", ToString.toString(e))
            case Ok(store) => {
                let a = Adapter.Postgres.PostgresStore.connect(config);
                a.setAutoCommit(false);
                a.prepareStatement("insert into ${table} (version, kind, user_name, list_name, description, status) values (1, 'ListCreated', 'uberto', 'winner', '', '')").executeUpdate();
                let result = new AtomicReference();
                let late = ToDoListEvent.ListCreated(uberto(), ListName.ListName("loser")) :: Nil;
                let task = new Runnable {
                    def $run(_this: Runnable): Unit \ IO =
                        result.set(ToString.toString(store#append(0, late)))
                };
                let b = new Thread(task);
                b.start();
                // B がロック待ちになるのを待つ。待たなくても結果は同じだが、待った上で「まだ終わっていない」を確かめる
                Thread.sleep(1000i64);
                let stillWaiting = Objects.isNull(result.get());
                a.commit();
                b.join();
                a.close();
                Assert.assertTrue(stillWaiting);
                Assert.assertEq(expected = "Err(VersionConflict(0, 1))", result.get());
                // 負けた書き込みは何も残さない。勝った A のイベントだけが残る
                match store#load() {
                    case Ok(read) => {
                        Assert.assertEq(expected = 1, read#version);
                        Assert.assertEq(expected = ToDoListEvent.ListCreated(uberto(), ListName.ListName("winner")) :: Nil, read#events)
                    }
                    case Err(e)   => Assert.assertEq(expected = "loaded", ToString.toString(e))
                }
            }
        };
        let _ = Adapter.Postgres.PostgresStore.dropTable(config, table);
        ()
    }

}
