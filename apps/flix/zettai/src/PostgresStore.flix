mod PostgresStore {

    import java.lang.Object
    import java.lang.System
    import java.util.Objects
    import java.sql.Connection
    import java.sql.ResultSet
    import java.sql.SQLException
    import java.util.Properties
    import java.util.UUID
    import org.postgresql.Driver

    use Errors.ZettaiError
    use Events.ToDoListEvent
    use EventStore.EventStore

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
    def connect(config: Config): Connection \ IO = {
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

    def readRows(rs: ResultSet): List[Codec.EventRow] \ IO =
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

    def load(config: Config, table: String): Result[ZettaiError, List[ToDoListEvent]] \ IO =
        match withConnection(config, conn -> {
            let st = conn.prepareStatement("select kind, user_name, list_name, description, status from ${table} order by seq");
            Ok(readRows(st.executeQuery()))
        }) {
            case Ok(rows) => Traversable.traverse(Codec.decode, rows)
            case Err(e)   => Err(e)
        }

    def append(config: Config, table: String, events: List[ToDoListEvent]): Result[ZettaiError, Unit] \ IO =
        withConnection(config, conn -> {
            conn.setAutoCommit(false);
            let st = conn.prepareStatement("insert into ${table} (kind, user_name, list_name, description, status) values (?, ?, ?, ?, ?)");
            List.forEach(event -> {
                let row = Codec.encode(event);
                st.setString(1, row#kind);
                st.setString(2, row#user);
                st.setString(3, row#list);
                st.setString(4, row#description);
                st.setString(5, row#status);
                st.addBatch()
            }, events);
            st.executeBatch();
            conn.commit();
            Ok(())
        })

    /// イベントの保管場所を開く。テーブルが無ければ作る。
    pub def open(config: Config, table: String): Result[ZettaiError, EventStore[IO]] \ IO =
        if (not validTableName(table))
            Err(ZettaiError.StorageFailure("invalid table name: ${table}"))
        else
            match execute(config, "create table if not exists ${table} (seq bigserial primary key, kind text not null, user_name text not null, list_name text not null, description text not null, status text not null)") {
                case Err(e) => Err(e)
                case Ok(_)  => Ok({
                    load = () -> load(config, table),
                    append = events -> append(config, table, events)
                })
            }

}
