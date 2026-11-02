package zettai

import cats.effect.IO
import cats.syntax.all.*
import org.typelevel.doobie.*
import org.typelevel.doobie.implicits.*
import java.sql.SQLException
import java.util.UUID

/** PostgreSQL に置くイベントストア。ConnectionIO を組み合わせ、1 回の書き込みを 1 つのトランザクションにする。 */
object PostgresStore:

  final case class Config(url: String, user: String, password: String)

  /** 1 件のイベントを表す行。知らない種類の行は読めない。 */
  private final case class Row(
      version: Int,
      kind: String,
      userName: String,
      listName: String,
      description: String,
      status: String
  )

  def transactor(config: Config): Transactor[IO] =
    Transactor.fromDriverManager[IO]("org.postgresql.Driver", config.url, config.user, config.password, None)

  /** 英小文字・数字・下線だけの名前だけを、テーブル名として許す。 */
  def validTableName(name: String): Boolean =
    name.nonEmpty && name.forall(c => c.isLower || c.isDigit || c == '_')

  def freshTableName: IO[String] =
    IO(s"events_${UUID.randomUUID().toString.replace("-", "_")}")

  private def encode(version: Int, event: ToDoListEvent): Row =
    event match
      case ToDoListEvent.ListCreated(user, name) =>
        Row(version, "ListCreated", user.name, name.name, "", "")
      case ToDoListEvent.ItemAdded(user, name, item) =>
        Row(version, "ItemAdded", user.name, name.name, item.description, item.status.toString)
      case ToDoListEvent.ItemStatusChanged(user, name, description, status) =>
        Row(version, "ItemStatusChanged", user.name, name.name, description, status.toString)

  /** 読めない行は想定外の失敗。業務の失敗ではないので、IO の失敗にする。 */
  private def decode(row: Row): ToDoListEvent =
    val user = User(row.userName)
    val name = ListName(row.listName)
    def status = ToDoStatus.valueOf(row.status)
    row.kind match
      case "ListCreated"       => ToDoListEvent.ListCreated(user, name)
      case "ItemAdded"         => ToDoListEvent.ItemAdded(user, name, ToDoItem(row.description, status))
      case "ItemStatusChanged" => ToDoListEvent.ItemStatusChanged(user, name, row.description, status)
      case other               => throw new IllegalStateException(s"知らない種類のイベントです: $other")

  private def requireValid(table: String): IO[Fragment] =
    if validTableName(table) then IO.pure(Fragment.const(table))
    else IO.raiseError(new IllegalArgumentException("テーブル名に使えない文字が含まれています"))

  def dropTable(xa: Transactor[IO], table: String): IO[Unit] =
    requireValid(table).flatMap(t => (fr"drop table if exists" ++ t).update.run.transact(xa).void)

  /** テーブルを（無ければ）作り、そのテーブルに読み書きするストアを返す。 */
  def open(xa: Transactor[IO], table: String): IO[EventStore] =
    requireValid(table).flatMap { t =>
      val create =
        fr"create table if not exists" ++ t ++
          fr"(version int primary key, kind text not null, user_name text not null, list_name text not null, description text not null, status text not null)"

      val currentVersion: ConnectionIO[Int] =
        (fr"select coalesce(max(version), 0) from" ++ t).query[Int].unique

      val readAll: ConnectionIO[Versioned] =
        (fr"select version, kind, user_name, list_name, description, status from" ++ t ++ fr"order by version")
          .query[Row]
          .to[List]
          .map(rows => Versioned(rows.size, rows.map(decode)))

      def insert(rows: List[Row]): ConnectionIO[Int] =
        Update[Row](
          s"insert into $table (version, kind, user_name, list_name, description, status) values (?, ?, ?, ?, ?, ?)"
        ).updateMany(rows)

      /** 版の確認と挿入を、1 つのトランザクションで行う。 */
      def appendAt(expected: Int, events: List[ToDoListEvent]): ConnectionIO[Either[ZettaiError, Unit]] =
        currentVersion.flatMap { actual =>
          if actual != expected then (Left(ZettaiError.VersionConflict(expected, actual)): Either[ZettaiError, Unit]).pure[ConnectionIO]
          else insert(events.zipWithIndex.map((e, i) => encode(expected + i + 1, e))).as(Right(()))
        }

      def append(expected: Int, events: List[ToDoListEvent]): IO[Either[ZettaiError, Unit]] =
        appendAt(expected, events).transact(xa).recoverWith {
          // 版の確認を通ったあとで別の書き手に先を越された場合は、主キーの一意制約が止める
          case e: SQLException if e.getSQLState == "23505" =>
            currentVersion.transact(xa).map(actual => Left(ZettaiError.VersionConflict(expected, actual)))
        }

      create.update.run.transact(xa).as(EventStore(readAll.transact(xa), append))
    }
