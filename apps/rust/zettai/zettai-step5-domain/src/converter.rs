//! 双方向変換（第 12 章）。
//!
//! **第 9 章は書き出しと読み込みが別々でした。** `to_json` と `from_json` が
//! 別の関数で、片方だけ直せます。実際に片方だけ直しました（第 9 章で
//! `jsonb` の正規化に対応したとき、読み側だけを直しています）。
//!
//! **1 つにまとめます。** 対であることが型に出ます。
//!
//! ## プロファンクタ
//!
//! `Converter<A, B>` は **A を受け取って B を出し、B を受け取って A に戻す**
//! 対です。入力側と出力側で向きが逆なので、写せる方向も逆になります。
//!
//! | 操作 | 何をするか |
//! | :--- | :--- |
//! | `contramap` | **入力側**を写す。`Converter<A, B>` → `Converter<A2, B>` |
//! | `map` | **出力側**を写す。`Converter<A, B>` → `Converter<A, B2>` |
//!
//! 両方を一度にやるのが `dimap` です。**これがプロファンクタ。**

use crate::ZettaiError;

/// 書き出す側。**clippy に名前を付けろと言われた**（`type_complexity`。3 件目）。
pub type Write<A, B> = Box<dyn Fn(&A) -> B>;

/// 読み込む側。**失敗しうる**ので `Result` が要る。向きが揃っていない。
pub type Read<A, B> = Box<dyn Fn(&B) -> Result<A, ZettaiError>>;

/// 書き出しと読み込みの対。
///
/// **同じ値から両方を作る**ので、片方だけ直すことができません。
pub struct Converter<A, B> {
    write: Write<A, B>,
    read: Read<A, B>,
}

impl<A: 'static, B: 'static> Converter<A, B> {
    pub fn new(
        write: impl Fn(&A) -> B + 'static,
        read: impl Fn(&B) -> Result<A, ZettaiError> + 'static,
    ) -> Self {
        Converter {
            write: Box::new(write),
            read: Box::new(read),
        }
    }

    pub fn write(&self, value: &A) -> B {
        (self.write)(value)
    }

    pub fn read(&self, encoded: &B) -> Result<A, ZettaiError> {
        (self.read)(encoded)
    }

    /// **入力側を写す。** 向きが逆なので、写す関数も逆向きに要る。
    pub fn contramap<A2: 'static>(
        self,
        to: impl Fn(&A2) -> A + 'static,
        from: impl Fn(A) -> A2 + 'static,
    ) -> Converter<A2, B> {
        Converter {
            write: Box::new(move |a2| (self.write)(&to(a2))),
            read: Box::new(move |b| (self.read)(b).map(&from)),
        }
    }

    /// **出力側を写す。**
    pub fn map<B2: 'static>(
        self,
        to: impl Fn(B) -> B2 + 'static,
        from: impl Fn(&B2) -> B + 'static,
    ) -> Converter<A, B2> {
        Converter {
            write: Box::new(move |a| to((self.write)(a))),
            read: Box::new(move |b2| (self.read)(&from(b2))),
        }
    }

    /// 両側を一度に写す。**これがプロファンクタ。**
    pub fn dimap<A2: 'static, B2: 'static>(
        self,
        to_a: impl Fn(&A2) -> A + 'static,
        from_a: impl Fn(A) -> A2 + 'static,
        to_b: impl Fn(B) -> B2 + 'static,
        from_b: impl Fn(&B2) -> B + 'static,
    ) -> Converter<A2, B2> {
        self.contramap(to_a, from_a).map(to_b, from_b)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn digits() -> Converter<i64, String> {
        Converter::new(
            |n: &i64| n.to_string(),
            |s: &String| {
                s.parse::<i64>().map_err(|_| ZettaiError::StoreUnavailable {
                    detail: format!("数として読めない: {s}"),
                })
            },
        )
    }

    #[test]
    fn a_value_survives_the_round_trip() {
        let c = digits();
        assert_eq!(c.read(&c.write(&42)).unwrap(), 42);
    }

    #[test]
    fn a_broken_input_is_a_failure() {
        assert!(digits().read(&"いろは".to_string()).is_err());
    }

    /// **入力側を写しても、対であることは保たれる。**
    #[test]
    fn contramap_keeps_the_pairing() {
        let c = digits().contramap(|n: &i32| *n as i64, |n| n as i32);
        assert_eq!(c.read(&c.write(&7)).unwrap(), 7);
    }

    /// **出力側を写しても、対であることは保たれる。**
    #[test]
    fn map_keeps_the_pairing() {
        let c = digits().map(
            |s| format!("<{s}>"),
            |s: &String| s.trim_matches(|ch| ch == '<' || ch == '>').to_string(),
        );
        assert_eq!(c.write(&5), "<5>");
        assert_eq!(c.read(&"<5>".to_string()).unwrap(), 5);
    }
}
