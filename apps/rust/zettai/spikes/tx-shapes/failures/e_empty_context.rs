// ADR-011 の決定 2 を素直に写す。**文脈を空にして、使うときに取り出す。**
//
//   interface TxContext                      // 中身が空
//   require(context is JdbcContext)          // 使うときにダウンキャスト
//
// Rust で「中身が空のトレイト + ダウンキャスト」は `dyn Any` になる。書けるか。
use postgres::{Client, NoTls, Transaction};
use std::any::Any;

/// 空の文脈（ADR-011 の決定 2）。
trait TxContext: Any {
    fn as_any(&self) -> &dyn Any;
}

/// 接続を持つ具体。
struct PgContext<'a> {
    tx: Transaction<'a>,
}

impl<'a> TxContext for PgContext<'a> {
    fn as_any(&self) -> &dyn Any {
        self
    }
}

fn main() {
    let mut client = Client::connect("host=localhost", NoTls).unwrap();
    let tx = client.transaction().unwrap();
    let ctx = PgContext { tx };
    let _: &dyn TxContext = &ctx;
}
