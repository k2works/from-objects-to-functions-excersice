pub mod Adapter.Postgres.PostgresStore {

    import java.lang.Object
    import java.lang.System
    import java.util.Objects
    import java.sql.Connection
    import java.sql.ResultSet
    import java.sql.SQLException
    import java.util.Properties
    import java.util.UUID
    import org.postgresql.Driver

    use Domain.Errors.ZettaiError
    use Domain.Events.ToDoListEvent
    use Application.EventStore.{EventStore, Versioned}

    pub type alias Config = { url = String, user = String, password = String }

    def failure(error: SQLException): ZettaiError =
        ZettaiError.StorageFailure(error.getMessage())

    /// 接続先は環境変数 ZETTAI_DB_URL で決める。無ければ docker-compose.yml の zettai-db。
    pub def configFromEnv(): Config \ IO = {
        let fromEnv = System.getenv("ZETTAI_DB_URL");
        let url = if (isNull(fromEnv)) "jdbc:postgresql://localhost:5432/zettai" else fromEnv;
        { url = url, user = "zettai", password = "zettai" }
    }

    def isNull(value: String): Bool =
        Objects.isNull(unchecked_cast(value as Object))

    /// 英小文字・数字・下線だけの名前だけを、テーブル名として許す。
    pub def validTableName(name: String): Bool =
        not String.isEmpty(name) and String.forAll(c -> Char.isLowerCase(c) or Char.isDigit(c) or c == '_', name)

    pub def freshTableName(): String \ IO =
        "events_${String.replace(src = "-", dst = "_", UUID.randomUUID().toString())}"

    /// JDBC の DriverManager は Flix の読み込む jar を見つけられないので、ドライバを直接生成する。
    pub def connect(config: Config): Connection \ IO = {
        let props = new Properties();
        props.setProperty("user", config#user);
        props.setProperty("password", config#password);
        new Driver().connect(config#url, props)
    }

    /// 接続を開いて処理し、必ず閉じる。SQL の例外は値としての失敗にする。
    def withConnection(config: Config, f: Connection -> Result[ZettaiError, a] \ IO): Result[ZettaiError, a] \ IO =
        try {
            let conn = connect(config);
            let result = try {
                f(conn)
            } catch {
                case ex: SQLException => Err(failure(ex))
            };
            conn.close();
            result
        } catch {
            case ex: SQLException => Err(failure(ex))
        }

    pub def execute(config: Config, sql: String): Result[ZettaiError, Unit] \ IO =
        withConnection(config, conn -> {
            let st = conn.createStatement();
            st.execute(sql);
            Ok(())
        })

    pub def dropTable(config: Config, table: String): Result[ZettaiError, Unit] \ IO =
        if (validTableName(table)) execute(config, "drop table if exists ${table}")
        else Err(ZettaiError.StorageFailure("invalid table name: ${table}"))

    def readRows(rs: ResultSet): List[Adapter.Postgres.Codec.EventRow] \ IO =
        if (rs.next()) {
            let row = {
                kind = rs.getString("kind"),
                user = rs.getString("user_name"),
                list = rs.getString("list_name"),
                description = rs.getString("description"),
                status = rs.getString("status")
            };
            row :: readRows(rs)
        } else {
            Nil
        }

    /// 渡された接続で、イベントを版つきで読む。接続の開閉とトランザクションは呼び出し側が決める。
    def readEvents(conn: Connection, table: String): Result[ZettaiError, Versioned] \ IO =
        try {
            let st = conn.prepareStatement("select kind, user_name, list_name, description, status from ${table} order by version");
            let rows = readRows(st.executeQuery());
            Result.map(events -> { events = events, version = List.length(events) }, Traversable.traverse(Adapter.Postgres.Codec.decode, rows))
        } catch {
            case ex: SQLException => Err(failure(ex))
        }

    def load(config: Config, table: String): Result[ZettaiError, Versioned] \ IO =
        withConnection(config, conn -> readEvents(conn, table))

    def currentVersion(conn: Connection, table: String): Int32 \ IO = {
        let rs = conn.prepareStatement("select coalesce(max(version), 0) from ${table}").executeQuery();
        rs.next();
        rs.getInt(1)
    }

    /// 一意制約の違反（SQLState 23505）は、別の書き込みに先を越されたということ。
    /// その時点の版は分からないので、少なくとも expected + 1 であることだけを返す。
    pub def conflictOrFailure(error: SQLException, expected: Int32): ZettaiError \ IO =
        if (error.getSQLState() == "23505")
            ZettaiError.VersionConflict(expected, expected + 1)
        else
            failure(error)

    /// 渡された接続で、版を確かめてから書く。コミットは呼び出し側が決める。
    def insertEvents(conn: Connection, table: String, expected: Int32, events: List[ToDoListEvent]): Result[ZettaiError, Unit] \ IO =
        try {
            let actual = currentVersion(conn, table);
            if (actual != expected) {
                Err(ZettaiError.VersionConflict(expected, actual))
            } else {
                try {
                    let st = conn.prepareStatement("insert into ${table} (version, kind, user_name, list_name, description, status) values (?, ?, ?, ?, ?, ?)");
                    List.forEach(entry -> match entry {
                        case (event, version) => {
                            let row = Adapter.Postgres.Codec.encode(event);
                            st.setInt(1, version);
                            st.setString(2, row#kind);
                            st.setString(3, row#user);
                            st.setString(4, row#list);
                            st.setString(5, row#description);
                            st.setString(6, row#status);
                            st.addBatch()
                        }
                    }, List.zip(events, List.range(expected + 1, expected + 1 + List.length(events))));
                    st.executeBatch();
                    Ok(())
                } catch {
                    case ex: SQLException => Err(conflictOrFailure(ex, expected))
                }
            }
        } catch {
            case ex: SQLException => Err(failure(ex))
        }

    /// 1 回の書き込みを、1 つのトランザクションにする。
    def append(config: Config, table: String, expected: Int32, events: List[ToDoListEvent]): Result[ZettaiError, Unit] \ IO =
        withConnection(config, conn -> {
            conn.setAutoCommit(false);
            let result = insertEvents(conn, table, expected, events);
            match result {
                case Ok(_)  => conn.commit()
                case Err(_) => conn.rollback()
            };
            result
        })

    /// 1 つの接続とトランザクションの中で、効果 EventLog を処理する。
    /// 成功したときだけコミットし、失敗したときは、中で書いたものをすべて取り消す。
    pub def transactional(config: Config, table: String, f: Unit -> Result[ZettaiError, a] \ Application.Process.EventLog): Result[ZettaiError, a] \ IO =
        withConnection(config, conn -> {
            conn.setAutoCommit(false);
            let result = run {
                f()
            } with handler Application.Process.EventLog {
                def load(resume) = resume(readEvents(conn, table))
                def append(expected, events, resume) = resume(insertEvents(conn, table, expected, events))
            };
            match result {
                case Ok(_)  => conn.commit()
                case Err(_) => conn.rollback()
            };
            result
        })

    /// イベントの保管場所を開く。テーブルが無ければ作る。
    pub def open(config: Config, table: String): Result[ZettaiError, EventStore[IO]] \ IO =
        if (not validTableName(table))
            Err(ZettaiError.StorageFailure("invalid table name: ${table}"))
        else
            match execute(config, "create table if not exists ${table} (seq bigserial primary key, version integer not null unique, kind text not null, user_name text not null, list_name text not null, description text not null, status text not null)") {
                case Err(e) => Err(e)
                case Ok(_)  => Ok({
                    load = () -> load(config, table),
                    append = (expected, events) -> append(config, table, expected, events)
                })
            }

}
