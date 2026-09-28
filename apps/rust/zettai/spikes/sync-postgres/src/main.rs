use postgres::{Client, NoTls};

fn main() -> Result<(), postgres::Error> {
    let mut client = Client::connect("host=localhost user=zettai password=zettai dbname=zettai", NoTls)?;

    client.batch_execute(
        "CREATE TABLE IF NOT EXISTS spike_events (
            id BIGSERIAL PRIMARY KEY,
            entity_id TEXT NOT NULL,
            payload JSONB NOT NULL
        )",
    )?;
    client.execute("DELETE FROM spike_events", &[])?;

    client.execute(
        "INSERT INTO spike_events (entity_id, payload) VALUES ($1, ($2::text)::jsonb)",
        &[&"uberto/book", &r#"{"type":"ListCreated"}"#],
    )?;

    for row in client.query("SELECT entity_id, payload::text FROM spike_events", &[])? {
        let entity_id: &str = row.get(0);
        let payload: &str = row.get(1);
        println!("読み戻し: {entity_id} / {payload}");
    }

    // トランザクション（第 10 章で使う形）
    let mut tx = client.transaction()?;
    tx.execute(
        "INSERT INTO spike_events (entity_id, payload) VALUES ($1, ($2::text)::jsonb)",
        &[&"uberto/shopping", &r#"{"type":"ListCreated"}"#],
    )?;
    tx.rollback()?;

    let n: i64 = client.query_one("SELECT count(*) FROM spike_events", &[])?.get(0);
    println!("ロールバック後の件数: {n}");

    client.batch_execute("DROP TABLE spike_events")?;
    println!("同期の postgres クレートで INSERT / SELECT / トランザクションが書けた");
    Ok(())
}
