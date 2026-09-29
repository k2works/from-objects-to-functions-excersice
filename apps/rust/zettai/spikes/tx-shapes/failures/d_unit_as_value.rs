// 案 D: 単位を「値として返す」。Kotlin 版の `runInTransaction` を素直に写すとこうなる。
//
//   let unit = store.begin()?;   // トランザクションを受け取る
//   let before = unit.events_of("k")?;
//   unit.append("k", &events)?;
//   unit.commit()?;
//
// 書けるか。
use postgres::{Client, NoTls, Transaction};
use std::sync::{Mutex, MutexGuard};

struct Store {
    client: Mutex<Client>,
}

struct Unit<'a> {
    // ロックと、そのロックの中身を借りたトランザクションを、同じ箱に入れたい
    guard: MutexGuard<'a, Client>,
    tx: Transaction<'a>,
}

impl Store {
    fn begin(&self) -> Result<Unit<'_>, postgres::Error> {
        let mut guard = self.client.lock().unwrap();
        let tx = guard.transaction()?;
        Ok(Unit { guard, tx })
    }
}

fn main() {
    let store = Store {
        client: Mutex::new(Client::connect("host=localhost", NoTls).unwrap()),
    };
    let _ = store.begin();
}
