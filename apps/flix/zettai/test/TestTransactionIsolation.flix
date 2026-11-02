mod TestTransactionIsolation {

    import java.sql.Connection
    import java.sql.SQLException

    /// 2 つのトランザクションが、どちらも先に数を読んでから、書く。
    /// 時間や並行実行には頼らず、手順を明示して競合を再現する。
    def bothReadThenBothWrite(config: Adapter.Postgres.PostgresStore.Config, table: String): Int32 \ IO = {
        let a = Adapter.Postgres.PostgresStore.connect(config);
        let b = Adapter.Postgres.PostgresStore.connect(config);
        a.setAutoCommit(false);
        b.setAutoCommit(false);
        let readA = count(a, table);
        let readB = count(b, table);
        insert(a, table, readA + 1);
        a.commit();
        insert(b, table, readB + 1);
        b.commit();
        a.close();
        b.close();
        count(Adapter.Postgres.PostgresStore.connect(config), table)
    }

    def count(conn: Connection, table: String): Int32 \ IO = {
        let rs = conn.prepareStatement("select count(*) from ${table}").executeQuery();
        rs.next();
        rs.getInt(1)
    }

    def insert(conn: Connection, table: String, version: Int32): Unit \ IO = {
        let st = conn.prepareStatement("insert into ${table} (version) values (?)");
        st.setInt(1, version);
        st.executeUpdate();
        ()
    }

    @Test
    def aTransactionAloneDoesNotStopTwoStaleWritersFromBothCommitting(): Unit \ {Assert, IO} = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        let table = Adapter.Postgres.PostgresStore.freshTableName();
        let _ = Adapter.Postgres.PostgresStore.execute(config, "create table ${table} (version integer not null)");
        // 版に制約が無いと、どちらも「1 番目の書き込み」としてコミットできてしまう
        Assert.assertEq(expected = 2, bothReadThenBothWrite(config, table));
        let _ = Adapter.Postgres.PostgresStore.dropTable(config, table);
        ()
    }

    @Test
    def aUniqueVersionMakesTheSecondStaleWriterFail(): Unit \ {Assert, IO} = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        let table = Adapter.Postgres.PostgresStore.freshTableName();
        let _ = Adapter.Postgres.PostgresStore.execute(config, "create table ${table} (version integer not null unique)");
        let outcome = try {
            Ok(bothReadThenBothWrite(config, table))
        } catch {
            case ex: SQLException => Err(ex.getSQLState())
        };
        Assert.assertEq(expected = Err("23505"), outcome);
        let _ = Adapter.Postgres.PostgresStore.dropTable(config, table);
        ()
    }

    @Test
    def aUniqueViolationBecomesAVersionConflict(): Unit \ {Assert, IO} = {
        let violation = new SQLException("duplicate key", "23505");
        let other = new SQLException("syntax error", "42601");
        Assert.assertEq(expected = Domain.Errors.ZettaiError.VersionConflict(3, 4), Adapter.Postgres.PostgresStore.conflictOrFailure(violation, 3));
        Assert.assertEq(expected = Domain.Errors.ZettaiError.StorageFailure("syntax error"), Adapter.Postgres.PostgresStore.conflictOrFailure(other, 3))
    }

    @Test
    def writingAheadOfTheCurrentVersionIsRefused(): Unit \ {Assert, IO} = {
        let config = Adapter.Postgres.PostgresStore.configFromEnv();
        let table = Adapter.Postgres.PostgresStore.freshTableName();
        match Adapter.Postgres.PostgresStore.open(config, table) {
            case Ok(store) => Assert.assertEq(
                expected = Err(Domain.Errors.ZettaiError.VersionConflict(5, 0)),
                store#append(5, Domain.Events.ToDoListEvent.ListCreated(Domain.User.User("u"), Domain.ListName.ListName("l")) :: Nil))
            case Err(e)    => Assert.assertEq(expected = "opened", ToString.toString(e))
        };
        let _ = Adapter.Postgres.PostgresStore.dropTable(config, table);
        ()
    }

}
