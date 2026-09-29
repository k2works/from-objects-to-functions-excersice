//! 関数型 DI の 3 案を、**第 5 章のクロージャ合成まで**書いて比べる。
//!
//! 第 4 章だけで決めると第 5 章で書き直しになるので、各案で
//! 「状態から状態への関数を合成してモノイドにする」ところまで書く。

#[derive(Debug, Clone, PartialEq, Eq, Default)]
pub struct ToDoList {
    pub items: Vec<String>,
}

pub type Fetched = Option<ToDoList>;

// ---------------------------------------------------------------------------
// 案 A: `impl Fn` を引数に取る（静的ディスパッチ・ジェネリクスの糖衣）
// ---------------------------------------------------------------------------
pub mod a_impl_fn {
    use super::*;

    /// ユースケース。依存を関数値として受け取る。
    pub fn items_of(fetch: impl Fn(&str, &str) -> Fetched, user: &str, name: &str) -> Vec<String> {
        fetch(user, name).map(|l| l.items).unwrap_or_default()
    }

    /// 第 5 章: 状態から状態への関数を合成する。
    pub fn compose(
        f: impl Fn(ToDoList) -> ToDoList,
        g: impl Fn(ToDoList) -> ToDoList,
    ) -> impl Fn(ToDoList) -> ToDoList {
        move |s| g(f(s))
    }

    /// 単位元。
    pub fn identity() -> impl Fn(ToDoList) -> ToDoList {
        |s| s
    }

    /// **並びを畳み込んで 1 つにする。** ここが案 A の限界を試す箇所。
    pub fn fold_all(fs: Vec<Box<dyn Fn(ToDoList) -> ToDoList>>) -> impl Fn(ToDoList) -> ToDoList {
        move |s| fs.iter().fold(s, |acc, f| f(acc))
    }
}

// ---------------------------------------------------------------------------
// 案 B: `Box<dyn Fn>` を持つ（動的ディスパッチ）
// ---------------------------------------------------------------------------
pub mod b_box_dyn {
    use super::*;

    /// 依存 1 つ分の型。**clippy に名前を付けろと言われた**（type_complexity）。
    /// 付けたほうが記事も読みやすい。
    pub type Fetcher = Box<dyn Fn(&str, &str) -> Fetched>;

    pub struct Hub {
        pub fetch: Fetcher,
    }

    pub fn items_of(hub: &Hub, user: &str, name: &str) -> Vec<String> {
        (hub.fetch)(user, name).map(|l| l.items).unwrap_or_default()
    }

    pub type Transform = Box<dyn Fn(ToDoList) -> ToDoList>;

    pub fn compose(f: Transform, g: Transform) -> Transform {
        Box::new(move |s| g(f(s)))
    }

    pub fn identity() -> Transform {
        Box::new(|s| s)
    }

    /// 並びを畳み込んで 1 つにする。**同じ型に戻るので繰り返せる。**
    pub fn fold_all(fs: Vec<Transform>) -> Transform {
        fs.into_iter().fold(identity(), compose)
    }
}

// ---------------------------------------------------------------------------
// 案 C: ジェネリック型パラメータ + トレイト境界
// ---------------------------------------------------------------------------
pub mod c_trait {
    use super::*;

    pub trait ListFetcher {
        fn fetch(&self, user: &str, name: &str) -> Fetched;
    }

    pub fn items_of<F: ListFetcher>(f: &F, user: &str, name: &str) -> Vec<String> {
        f.fetch(user, name).map(|l| l.items).unwrap_or_default()
    }

    pub trait Transform {
        fn apply(&self, s: ToDoList) -> ToDoList;
    }

    pub struct Composed<F, G>(pub F, pub G);

    impl<F: Transform, G: Transform> Transform for Composed<F, G> {
        fn apply(&self, s: ToDoList) -> ToDoList {
            self.1.apply(self.0.apply(s))
        }
    }

    pub struct Identity;

    impl Transform for Identity {
        fn apply(&self, s: ToDoList) -> ToDoList {
            s
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn add(word: &'static str) -> impl Fn(ToDoList) -> ToDoList {
        move |mut s| {
            s.items.push(word.to_string());
            s
        }
    }

    #[test]
    fn a_composes_two() {
        let f = a_impl_fn::compose(add("x"), add("y"));
        assert_eq!(f(ToDoList::default()).items, vec!["x", "y"]);
    }

    #[test]
    fn a_folds_a_sequence() {
        let fs: Vec<Box<dyn Fn(ToDoList) -> ToDoList>> =
            vec![Box::new(add("x")), Box::new(add("y")), Box::new(add("z"))];
        let f = a_impl_fn::fold_all(fs);
        assert_eq!(f(ToDoList::default()).items, vec!["x", "y", "z"]);
    }

    #[test]
    fn a_injects_a_dependency() {
        let items = a_impl_fn::items_of(
            |_u, _n| {
                Some(ToDoList {
                    items: vec!["a".into()],
                })
            },
            "uberto",
            "book",
        );
        assert_eq!(items, vec!["a"]);
    }

    #[test]
    fn b_folds_a_sequence() {
        let fs: Vec<b_box_dyn::Transform> =
            vec![Box::new(add("x")), Box::new(add("y")), Box::new(add("z"))];
        let f = b_box_dyn::fold_all(fs);
        assert_eq!(f(ToDoList::default()).items, vec!["x", "y", "z"]);
    }

    #[test]
    fn b_identity_is_a_unit() {
        let left = b_box_dyn::compose(b_box_dyn::identity(), Box::new(add("x")));
        let right = b_box_dyn::compose(Box::new(add("x")), b_box_dyn::identity());
        assert_eq!(left(ToDoList::default()), right(ToDoList::default()));
    }

    #[test]
    fn c_composes_with_types() {
        use c_trait::Transform;
        struct Add(&'static str);
        impl Transform for Add {
            fn apply(&self, mut s: ToDoList) -> ToDoList {
                s.items.push(self.0.to_string());
                s
            }
        }
        let f = c_trait::Composed(Add("x"), c_trait::Composed(Add("y"), Add("z")));
        assert_eq!(f.apply(ToDoList::default()).items, vec!["x", "y", "z"]);
    }
}
