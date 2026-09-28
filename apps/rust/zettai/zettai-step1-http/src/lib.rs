//! Zettai の最初の段階。
//!
//! 第 1 章ではドメインをまだ作らない。テストに開発をガイドさせるという
//! 前提を置き、そのための道具が言語に揃っていることを確かめるだけにする。

pub mod bowling;
pub mod bowling_oo;

use std::path::PathBuf;
use std::sync::atomic::{AtomicUsize, Ordering};

/// ToDo リストの名前。
///
/// 第 1 章ではまだ何も守らない。第 11 章で検証が入る。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ListName(pub String);

impl ListName {
    pub fn new(name: &str) -> Self {
        ListName(name.to_string())
    }
}

/// テストごとに違う書き込み先を返す。
///
/// なでしこ3 版では記録先を 1 つに決め打ちしていて、並列に走るテストが
/// 同じファイルに書いて件数が混ざった（Unit 7 で踏んだ）。
/// **共有する書き込み先は、最初からテストごとに分ける。**
///
/// 依存クレートは要らない。プロセス ID と連番で足りる。
pub fn unique_path(label: &str) -> PathBuf {
    static COUNTER: AtomicUsize = AtomicUsize::new(0);
    let n = COUNTER.fetch_add(1, Ordering::SeqCst);
    let pid = std::process::id();
    std::env::temp_dir().join(format!("zettai_{label}_{pid}_{n}"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn list_name_keeps_what_it_was_given() {
        assert_eq!(ListName::new("book"), ListName("book".to_string()));
    }

    #[test]
    fn unique_path_differs_every_call() {
        let a = unique_path("x");
        let b = unique_path("x");
        assert_ne!(a, b, "同じラベルでも呼ぶたびに違う先を返す");
    }
}
