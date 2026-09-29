//! 3 案の PostgreSQL アダプタ。**ここにだけライフタイムが出る。**

use crate::{a_scope, b_argument, c_lazy, detail_of, Error, Event};
use postgres::{Client, Transaction};
use std::cell::RefCell;
use std::sync::Mutex;

fn store_err(e: &postgres::Error) -> Error {
    Error::Store(detail_of(e))
}

/// テーブルを作る。
pub fn create_table(client: &mut Client, table: &str) -> Result<(), Error> {
    client
        .batch_execute(&format!(
            "CREATE TABLE IF NOT EXISTS {table} (
                id BIGSERIAL PRIMARY KEY, key TEXT NOT NULL, payload TEXT NOT NULL)"
        ))
        .map_err(|e| store_err(&e))
}

pub fn drop_table(client: &mut Client, table: &str) {
    let _ = client.batch_execute(&format!("DROP TABLE IF EXISTS {table}"));
}

pub fn count(client: &mut Client, table: &str) -> i64 {
    client
        .query_one(&format!("SELECT count(*) FROM {table}"), &[])
        .expect("数えられる")
        .get(0)
}

// ---------------------------------------------------------------------------
// 案 A: スコープ貸し
// ---------------------------------------------------------------------------

/// トランザクションの中でだけ生きる口。**`'a` はここに閉じる。**
struct PgTx<'a> {
    tx: RefCell<Transaction<'a>>,
    table: String,
    /// **わざと失敗させるための注入。** n 件目の append で落とす。
    fail_at: Option<usize>,
    written: RefCell<usize>,
}

impl a_scope::TxStore for PgTx<'_> {
    fn events_of(&self, key: &str) -> Result<Vec<Event>, Error> {
        let rows = self
            .tx
            .borrow_mut()
            .query(
                &format!(
                    "SELECT payload FROM {} WHERE key = $1 ORDER BY id",
                    self.table
                ),
                &[&key],
            )
            .map_err(|e| store_err(&e))?;
        Ok(rows.iter().map(|r| Event(r.get::<_, String>(0))).collect())
    }

    fn append(&self, key: &str, events: &[Event]) -> Result<(), Error> {
        for event in events {
            *self.written.borrow_mut() += 1;
            if Some(*self.written.borrow()) == self.fail_at {
                return Err(Error::Store("わざと落とした".to_string()));
            }
            self.tx
                .borrow_mut()
                .execute(
                    &format!("INSERT INTO {} (key, payload) VALUES ($1, $2)", self.table),
                    &[&key, &event.0],
                )
                .map_err(|e| store_err(&e))?;
        }
        Ok(())
    }
}

pub struct ScopeStore {
    client: Mutex<Client>,
    table: String,
    pub fail_at: Option<usize>,
}

impl ScopeStore {
    pub fn new(client: Client, table: &str) -> Self {
        ScopeStore {
            client: Mutex::new(client),
            table: table.to_string(),
            fail_at: None,
        }
    }
}

impl a_scope::Transactional for ScopeStore {
    fn in_transaction(
        &self,
        work: &dyn Fn(&dyn a_scope::TxStore) -> Result<Vec<Event>, Error>,
    ) -> Result<Vec<Event>, Error> {
        let mut client = self.client.lock().expect("毒されていない");
        let tx = client.transaction().map_err(|e| store_err(&e))?;
        let pg = PgTx {
            tx: RefCell::new(tx),
            table: self.table.clone(),
            fail_at: self.fail_at,
            written: RefCell::new(0),
        };
        // **失敗したらここで pg が drop される → ロールバック。**
        let result = work(&pg)?;
        pg.tx.into_inner().commit().map_err(|e| store_err(&e))?;
        Ok(result)
    }
}

// ---------------------------------------------------------------------------
// 案 B: 引数で回す
// ---------------------------------------------------------------------------

pub struct ArgStore {
    pub table: String,
    pub fail_at: Option<usize>,
    written: RefCell<usize>,
}

impl ArgStore {
    pub fn new(table: &str) -> Self {
        ArgStore {
            table: table.to_string(),
            fail_at: None,
            written: RefCell::new(0),
        }
    }
}

impl b_argument::StoreIn<Transaction<'_>> for ArgStore {
    fn events_of(&self, ctx: &mut Transaction<'_>, key: &str) -> Result<Vec<Event>, Error> {
        let rows = ctx
            .query(
                &format!(
                    "SELECT payload FROM {} WHERE key = $1 ORDER BY id",
                    self.table
                ),
                &[&key],
            )
            .map_err(|e| store_err(&e))?;
        Ok(rows.iter().map(|r| Event(r.get::<_, String>(0))).collect())
    }

    fn append(&self, ctx: &mut Transaction<'_>, key: &str, events: &[Event]) -> Result<(), Error> {
        for event in events {
            *self.written.borrow_mut() += 1;
            if Some(*self.written.borrow()) == self.fail_at {
                return Err(Error::Store("わざと落とした".to_string()));
            }
            ctx.execute(
                &format!("INSERT INTO {} (key, payload) VALUES ($1, $2)", self.table),
                &[&key, &event.0],
            )
            .map_err(|e| store_err(&e))?;
        }
        Ok(())
    }
}

// ---------------------------------------------------------------------------
// 案 C: 遅延
// ---------------------------------------------------------------------------

struct PgContext<'a> {
    tx: RefCell<Transaction<'a>>,
    table: String,
    fail_at: Option<usize>,
    written: RefCell<usize>,
}

impl c_lazy::TxContext for PgContext<'_> {
    fn events_of(&self, key: &str) -> Result<Vec<Event>, Error> {
        let rows = self
            .tx
            .borrow_mut()
            .query(
                &format!(
                    "SELECT payload FROM {} WHERE key = $1 ORDER BY id",
                    self.table
                ),
                &[&key],
            )
            .map_err(|e| store_err(&e))?;
        Ok(rows.iter().map(|r| Event(r.get::<_, String>(0))).collect())
    }

    fn append(&self, key: &str, events: &[Event]) -> Result<(), Error> {
        for event in events {
            *self.written.borrow_mut() += 1;
            if Some(*self.written.borrow()) == self.fail_at {
                return Err(Error::Store("わざと落とした".to_string()));
            }
            self.tx
                .borrow_mut()
                .execute(
                    &format!("INSERT INTO {} (key, payload) VALUES ($1, $2)", self.table),
                    &[&key, &event.0],
                )
                .map_err(|e| store_err(&e))?;
        }
        Ok(())
    }
}

pub struct LazyStore {
    client: Mutex<Client>,
    table: String,
    pub fail_at: Option<usize>,
}

impl LazyStore {
    pub fn new(client: Client, table: &str) -> Self {
        LazyStore {
            client: Mutex::new(client),
            table: table.to_string(),
            fail_at: None,
        }
    }

    /// 組み立てた計算を走らせる。**ここが境界。**
    pub fn run<T>(&self, action: c_lazy::HubAction<'_, T>) -> Result<T, Error> {
        let mut client = self.client.lock().expect("毒されていない");
        let tx = client.transaction().map_err(|e| store_err(&e))?;
        let ctx = PgContext {
            tx: RefCell::new(tx),
            table: self.table.clone(),
            fail_at: self.fail_at,
            written: RefCell::new(0),
        };
        let result = action(&ctx)?;
        ctx.tx.into_inner().commit().map_err(|e| store_err(&e))?;
        Ok(result)
    }
}
