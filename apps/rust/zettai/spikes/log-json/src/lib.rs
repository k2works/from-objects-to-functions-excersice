//! `tracing` が要求を既定で満たすか。
//!
//! 要求は「**記録が構造を持ち、出力先を差し替えられる**」。

#[cfg(test)]
mod tests {
    use std::sync::{Arc, Mutex};
    use tracing::info;
    use tracing_subscriber::fmt::MakeWriter;

    /// 出力先を差し替えるための入れ物。
    #[derive(Clone, Default)]
    struct Collected(Arc<Mutex<Vec<u8>>>);

    impl std::io::Write for Collected {
        fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
            self.0.lock().unwrap().extend_from_slice(buf);
            Ok(buf.len())
        }
        fn flush(&mut self) -> std::io::Result<()> {
            Ok(())
        }
    }

    impl<'a> MakeWriter<'a> for Collected {
        type Writer = Collected;
        fn make_writer(&'a self) -> Self::Writer {
            self.clone()
        }
    }

    #[test]
    fn tracing_writes_structured_records_to_a_swappable_sink() {
        let sink = Collected::default();
        let subscriber = tracing_subscriber::fmt()
            .json()
            .with_writer(sink.clone())
            .finish();

        tracing::subscriber::with_default(subscriber, || {
            info!(user = "uberto", list = "book", appended = 2, "追記した");
        });

        let out = String::from_utf8(sink.0.lock().unwrap().clone()).unwrap();
        println!("tracing: {out}");
        assert!(out.contains(r#""user":"uberto""#), "実際: {out}");
        assert!(out.contains(r#""appended":2"#));
    }
}
