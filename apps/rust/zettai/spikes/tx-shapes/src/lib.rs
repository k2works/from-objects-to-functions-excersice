//! トランザクションの境界をどう表すか（Unit 6 / ゲート 1）。
//!
//! 問題は 1 つ。**「読む → 判断する → 書く」を 1 つの単位にしたい。**
//! いまは `?` が 3 回並んでいるだけで、途中で失敗すると半分だけ残る。
//!
//! Rust 固有の事情が 3 つある。
//!
//! 1. `Client::transaction(&mut self) -> Transaction<'_>` は `&mut Client` を借りる
//! 2. `Transaction` は **drop でロールバック**、`commit(self)` で確定する
//! 3. `dyn Any` のダウンキャストは `'static` を要求する。`Transaction<'a>` は `'static` でない
//!
//! 3 を踏むと、Kotlin 版 ADR-011 の決定 2（**空の `TxContext` + ダウンキャスト**）が
//! そのままでは移植できない。

use postgres::{Client, NoTls};

pub const CONN: &str = "host=localhost user=zettai password=zettai dbname=zettai";

/// 失敗の理由。実物の `ZettaiError` を真似た最小の形。
#[derive(Debug, PartialEq, Eq)]
pub enum Error {
    Store(String),
    Rejected(&'static str),
}

/// 起きたこと。最小の形。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Event(pub String);

/// 判断。**ドメインの純粋な部分。** どの案でも共通で、変わらない。
pub fn decide(before: &[Event], want: &str) -> Result<Vec<Event>, Error> {
    if before.iter().any(|e| e.0 == want) {
        return Err(Error::Rejected("もうある"));
    }
    Ok(vec![Event(want.to_string())])
}

// ===========================================================================
// 案 A: 店が単位を貸す（スコープ貸し）
// ===========================================================================

/// 案 A のドメイン側。**トランザクションの中でだけ触れる口。**
pub mod a_scope {
    use super::*;

    /// 単位の中で使える操作。**`postgres` を知らない。**
    pub trait TxStore {
        fn events_of(&self, key: &str) -> Result<Vec<Event>, Error>;
        fn append(&self, key: &str, events: &[Event]) -> Result<(), Error>;
    }

    /// 単位を貸す側。**戻り値を固定するとオブジェクト安全になる。**
    pub trait Transactional {
        fn in_transaction(
            &self,
            work: &dyn Fn(&dyn TxStore) -> Result<Vec<Event>, Error>,
        ) -> Result<Vec<Event>, Error>;
    }

    /// 境界。**ドメインの 1 関数に見える。ライフタイム注釈が 0。**
    pub fn handle(store: &dyn Transactional, key: &str, want: &str) -> Result<Vec<Event>, Error> {
        store.in_transaction(&|tx| {
            let before = tx.events_of(key)?;
            let new_events = decide(&before, want)?;
            tx.append(key, &new_events)?;
            Ok([before, new_events].concat())
        })
    }
}

// ===========================================================================
// 案 B: 文脈を引数で回す
// ===========================================================================

pub mod b_argument {
    use super::*;

    /// 文脈の型に対してジェネリック。**ドメインは `C` の中身を知らない。**
    pub trait StoreIn<C: ?Sized> {
        fn events_of(&self, ctx: &mut C, key: &str) -> Result<Vec<Event>, Error>;
        fn append(&self, ctx: &mut C, key: &str, events: &[Event]) -> Result<(), Error>;
    }

    /// 境界。**commit は呼び手が持つ。**
    pub fn handle<C: ?Sized>(
        store: &dyn StoreIn<C>,
        ctx: &mut C,
        key: &str,
        want: &str,
    ) -> Result<Vec<Event>, Error> {
        let before = store.events_of(ctx, key)?;
        let new_events = decide(&before, want)?;
        store.append(ctx, key, &new_events)?;
        Ok([before, new_events].concat())
    }
}

// ===========================================================================
// 案 C: 遅延させる HubAction（ContextReader の移植）
// ===========================================================================

pub mod c_lazy {
    use super::*;

    /// 文脈。**空にはできない**（`dyn Any` が `'static` を要求するため）。
    /// ドメインの動詞を持たせる。
    pub trait TxContext {
        fn events_of(&self, key: &str) -> Result<Vec<Event>, Error>;
        fn append(&self, key: &str, events: &[Event]) -> Result<(), Error>;
    }

    /// 文脈を受け取って結果を返す、遅延した計算。
    pub type HubAction<'a, T> = Box<dyn Fn(&dyn TxContext) -> Result<T, Error> + 'a>;

    pub fn fetch<'a>(key: &'a str) -> HubAction<'a, Vec<Event>> {
        Box::new(move |ctx| ctx.events_of(key))
    }

    pub fn persist<'a>(key: &'a str, events: Vec<Event>) -> HubAction<'a, ()> {
        Box::new(move |ctx| ctx.append(key, &events))
    }

    /// 繋ぐ。**第 5 章の `compose` と同じ形。**
    pub fn and_then<'a, A: 'a, B: 'a>(
        a: HubAction<'a, A>,
        f: impl Fn(A) -> HubAction<'a, B> + 'a,
    ) -> HubAction<'a, B> {
        Box::new(move |ctx| {
            let value = a(ctx)?;
            f(value)(ctx)
        })
    }

    /// 境界。**計算を組み立てるだけで、まだ走らない。**
    pub fn handle<'a>(key: &'a str, want: &'a str) -> HubAction<'a, Vec<Event>> {
        and_then(fetch(key), move |before| {
            let new_events = match decide(&before, want) {
                Ok(events) => events,
                Err(e) => return Box::new(move |_ctx| Err(clone_error(&e))),
            };
            let all = [before.clone(), new_events.clone()].concat();
            and_then(persist(key, new_events), move |()| {
                let all = all.clone();
                Box::new(move |_ctx| Ok(all.clone()))
            })
        })
    }

    fn clone_error(e: &Error) -> Error {
        match e {
            Error::Store(s) => Error::Store(s.clone()),
            Error::Rejected(s) => Error::Rejected(s),
        }
    }
}

/// つなぐ。
pub fn connect() -> Result<Client, Error> {
    Client::connect(CONN, NoTls).map_err(|e| Error::Store(detail_of(&e)))
}

/// `postgres::Error` の `Display` は `"db error"` としか出さない（既知の制約 11）。
pub fn detail_of(e: &(dyn std::error::Error + 'static)) -> String {
    let mut parts = vec![e.to_string()];
    let mut cause = e.source();
    while let Some(c) = cause {
        parts.push(c.to_string());
        cause = c.source();
    }
    parts.join(": ")
}

/// テストごとのテーブル。
pub fn unique_table(label: &str) -> String {
    use std::sync::atomic::{AtomicUsize, Ordering};
    static COUNTER: AtomicUsize = AtomicUsize::new(0);
    let n = COUNTER.fetch_add(1, Ordering::SeqCst);
    format!("tx_shapes_{label}_{}_{n}", std::process::id())
}
pub mod pg;
