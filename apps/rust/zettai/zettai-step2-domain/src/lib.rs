//! ドメイン（第 4 章からの段階）。**別のクレートにしてある。**
//!
//! 第 3 章でインフラから分けた（Unit 2 のゲート 2）。クレートを分けると、
//! `Cargo.toml` に書いていない相手は**呼ぼうとした時点でコンパイルが止まる**。
//! 2 対象は境界検査を書いて守ったが、この版は書いていない。
//!
//! 第 4 章で `ToDoListHub` が入り、**依存を関数値として受け取る**ように
//! なった。どこから取り出しどこへ保存するかを、ドメインは知らない。
//!
//! このクレートは依存を 1 つも持たない。

// **ワイルドカードで網羅の検査を無効にしない。**
// `_ =>` を 1 つ置くと、枝が足りなくてもコンパイラは止まらなくなる
// （spikes/match-exhaustiveness/）。
#![warn(clippy::wildcard_enum_match_arm)]

/// ToDo リストの名前。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ListName(pub String);

impl ListName {
    pub fn new(name: &str) -> Self {
        ListName(name.to_string())
    }
}

/// 利用者。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct User(pub String);

impl User {
    pub fn new(name: &str) -> Self {
        User(name.to_string())
    }
}

/// ToDo 項目。第 6 章で状態が加わる。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToDoItem {
    pub description: String,
}

impl ToDoItem {
    pub fn new(description: &str) -> Self {
        ToDoItem {
            description: description.to_string(),
        }
    }
}

/// ToDo リスト。
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToDoList {
    pub list_name: ListName,
    pub items: Vec<ToDoItem>,
}

/// 利用者のリストを取り出す（第 2 章から置いてある既定の中身）。
///
/// **見つからないことを `Option` で表す。** 第 7 章で `Result` に変わり、
/// 「なぜ見つからないか」を持つようになる。
///
/// 第 4 章からは、これは**ハブに渡す関数値の 1 つ**にすぎない。
/// 第 5 章でイベントの畳み込みになり、第 9 章で永続化される。
pub fn fetch_list(user: &User, list_name: &ListName) -> Option<ToDoList> {
    if user != &User::new("uberto") {
        return None;
    }
    match list_name.0.as_str() {
        "book" => Some(ToDoList {
            list_name: list_name.clone(),
            items: vec![
                ToDoItem::new("write chapter"),
                ToDoItem::new("insert code"),
                ToDoItem::new("publish book"),
            ],
        }),
        "shopping" => Some(ToDoList {
            list_name: list_name.clone(),
            items: vec![],
        }),
        _ => None,
    }
}

/// ユースケースの入口（第 4 章）。
///
/// **依存を関数値として受け取る。** どこから取り出し、どこへ保存するかを
/// ドメインは知らない。テストからは別の関数値を渡すだけで差し替えられる。
///
/// 型引数で持っているのは `impl Fn` と同じこと（静的ディスパッチ）。
/// **並びに入れるわけではないので、`Box` にしなくてよい**（[ADR-030]）。
/// 第 5 章のイベントの変換は事情が違い、そちらは `Box<dyn Fn>` になる。
pub struct ToDoListHub<F, S> {
    fetch: F,
    save: S,
}

impl<F, S> ToDoListHub<F, S>
where
    F: Fn(&User, &ListName) -> Option<ToDoList>,
    S: Fn(&User, &ToDoList),
{
    pub fn new(fetch: F, save: S) -> Self {
        ToDoListHub { fetch, save }
    }

    /// リストを見る。
    pub fn list_of(&self, user: &User, list_name: &ListName) -> Option<ToDoList> {
        (self.fetch)(user, list_name)
    }

    /// リストに項目を足す。**足した後のリストを返す。**
    ///
    /// 受け取ったリストを書き換えるのではなく、**足した新しいリストを作る**。
    /// もとのリストは変わらないので、第 5 章で「イベントを適用する関数」に
    /// そのまま化ける。
    /// コマンドを受け取り、起きたことを適用して保存する（第 6 章）。
    ///
    /// **判断は `execute_in` が持ち、ハブは配線だけを持つ。**
    /// 断られたら保存しない。
    pub fn handle(
        &self,
        user: &User,
        list_name: &ListName,
        command: ToDoListCommand,
    ) -> Result<ToDoList, Rejected> {
        let current = (self.fetch)(user, list_name);
        let events = execute_in(state_of_list(current.as_ref()), command)?;

        let before = current.unwrap_or(ToDoList {
            list_name: list_name.clone(),
            items: Vec::new(),
        });
        let after = fold_events(events)(before);

        (self.save)(user, &after);
        Ok(after)
    }

    pub fn add_item(&self, user: &User, list_name: &ListName, item: ToDoItem) -> Option<ToDoList> {
        let list = (self.fetch)(user, list_name)?;
        let updated = ToDoList {
            list_name: list.list_name,
            items: [list.items, vec![item]].concat(),
        };
        (self.save)(user, &updated);
        Some(updated)
    }
}

/// 起きたこと（第 5 章）。
///
/// **`enum` が言語にある。** Kotlin 版は sealed class、なでしこ3 版は
/// 辞書の `種類` キーで表した。ここは `enum` をそのまま使う。
///
/// `match` の網羅をコンパイラが見るので、種類を足したときに
/// **適用を書き忘れると止まる**。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ToDoListEvent {
    ListCreated { list_name: ListName },
    ItemAdded { item: ToDoItem },
}

/// 状態から状態への関数。**並びに入れるので `Box<dyn Fn>` に揃える**
/// （[ADR-030]）。
///
/// `impl Fn` では書くたびに別の型になり、同じ `Vec` に入らない。
pub type Transform = Box<dyn Fn(ToDoList) -> ToDoList>;

/// 何もしない変換。**合成の単位元。**
pub fn identity() -> Transform {
    Box::new(|list| list)
}

/// 2 つの変換を繋ぐ。**結果も変換なので、繰り返せる。**
pub fn compose(f: Transform, g: Transform) -> Transform {
    Box::new(move |list| g(f(list)))
}

/// イベント 1 つを、状態から状態への関数にする。
///
/// **イベントは自分の値を持つ。** 借りた値を閉じ込めると `'static` に
/// 足りない。保存して後から畳み込むので、持つほうが形に合う。
pub fn transform_for(event: ToDoListEvent) -> Transform {
    match event {
        ToDoListEvent::ListCreated { list_name } => Box::new(move |_previous| ToDoList {
            list_name: list_name.clone(),
            items: Vec::new(),
        }),
        ToDoListEvent::ItemAdded { item } => Box::new(move |list: ToDoList| ToDoList {
            list_name: list.list_name,
            items: [list.items, vec![item.clone()]].concat(),
        }),
    }
}

/// イベントの並びを 1 つの変換に畳み込む。
///
/// **単位元から始めて合成を繰り返すだけ。** これができるのは、
/// 合成の結果が同じ型に戻るからで、`impl Fn` では書けない。
pub fn fold_events(events: Vec<ToDoListEvent>) -> Transform {
    events
        .into_iter()
        .map(transform_for)
        .fold(identity(), compose)
}

/// 指示（第 6 章）。**起きたことではなく、起こしたいこと。**
///
/// イベントとの違いは時制。コマンドは断られることがあり、
/// イベントは断られない（もう起きているため）。
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ToDoListCommand {
    CreateList { list_name: ListName },
    AddItem { item: ToDoItem },
}

/// コマンドが断られた理由（第 6 章）。**第 7 章で育つ。**
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Rejected {
    /// 同じ名前のリストがもうある。
    ListAlreadyExists,
    /// リストがまだ無い。
    ListDoesNotExist,
}

/// 遷移表の行。
///
/// `ToDoList` そのものではなく、**遷移を決めるのに要る分だけ**を持つ。
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum ListState {
    Missing,
    Empty,
    HasItems,
}

/// 保管されているリストから、遷移に使う状態を読む。
///
/// **第 9 章まではリストを保管している**ので、起きたことの並びが手元に無い。
/// 状態だけは読めるので、ここで橋を架ける。
pub fn state_of_list(list: Option<&ToDoList>) -> ListState {
    match list {
        None => ListState::Missing,
        Some(list) if list.items.is_empty() => ListState::Empty,
        Some(_) => ListState::HasItems,
    }
}

/// 起きたことの並びから、遷移に使う状態を読む。
pub fn state_of(events: &[ToDoListEvent]) -> ListState {
    match events.last() {
        None => ListState::Missing,
        Some(ToDoListEvent::ListCreated { .. }) => ListState::Empty,
        Some(ToDoListEvent::ItemAdded { .. }) => ListState::HasItems,
    }
}

/// コマンドを実行して、起きたことを返す。断るときは理由を返す。
///
/// **遷移表そのもの。** 枝が 1 つでも欠けるとコンパイルが止まる（E0004）。
/// ただしコンパイラが見るのは**枝が揃っているか**だけで、
/// **行き先が正しいか**は見ない。だから表のマスを数えてテストする
/// （なでしこ3 版 Unit 4 と同じ）。
pub fn execute(
    events: &[ToDoListEvent],
    command: ToDoListCommand,
) -> Result<Vec<ToDoListEvent>, Rejected> {
    execute_in(state_of(events), command)
}

/// 遷移表の本体。**状態とコマンドだけを見る。**
pub fn execute_in(
    state: ListState,
    command: ToDoListCommand,
) -> Result<Vec<ToDoListEvent>, Rejected> {
    match (state, command) {
        (ListState::Missing, ToDoListCommand::CreateList { list_name }) => {
            Ok(vec![ToDoListEvent::ListCreated { list_name }])
        }
        (ListState::Missing, ToDoListCommand::AddItem { .. }) => Err(Rejected::ListDoesNotExist),
        (ListState::Empty, ToDoListCommand::CreateList { .. }) => Err(Rejected::ListAlreadyExists),
        (ListState::Empty, ToDoListCommand::AddItem { item }) => {
            Ok(vec![ToDoListEvent::ItemAdded { item }])
        }
        (ListState::HasItems, ToDoListCommand::CreateList { .. }) => {
            Err(Rejected::ListAlreadyExists)
        }
        (ListState::HasItems, ToDoListCommand::AddItem { item }) => {
            Ok(vec![ToDoListEvent::ItemAdded { item }])
        }
    }
}

/// 空のリスト。畳み込みの出発点。
pub fn empty_list() -> ToDoList {
    ToDoList {
        list_name: ListName::new(""),
        items: Vec::new(),
    }
}

/// イベントの並びから状態を作る。
pub fn replay(events: Vec<ToDoListEvent>) -> ToDoList {
    fold_events(events)(empty_list())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_known_list_has_its_items() {
        let list = fetch_list(&User::new("uberto"), &ListName::new("book")).unwrap();
        assert_eq!(list.items.len(), 3);
        assert_eq!(list.list_name, ListName::new("book"));
    }

    #[test]
    fn an_empty_list_is_still_a_list() {
        let list = fetch_list(&User::new("uberto"), &ListName::new("shopping")).unwrap();
        assert!(list.items.is_empty());
    }

    #[test]
    fn an_unknown_user_has_nothing() {
        assert!(fetch_list(&User::new("nobody"), &ListName::new("book")).is_none());
    }

    // -----------------------------------------------------------------
    // 第 4 章: ハブは渡された関数値しか使わない
    // -----------------------------------------------------------------

    use std::cell::RefCell;

    #[test]
    fn the_hub_uses_the_function_it_was_given() {
        // 埋め込みのデータではなく、**ここで渡したものが返る**。
        let hub = ToDoListHub::new(
            |_u, name| {
                Some(ToDoList {
                    list_name: name.clone(),
                    items: vec![ToDoItem::new("渡したほう")],
                })
            },
            |_u, _l| {},
        );

        let list = hub
            .list_of(&User::new("uberto"), &ListName::new("book"))
            .unwrap();

        assert_eq!(list.items, vec![ToDoItem::new("渡したほう")]);
    }

    #[test]
    fn adding_an_item_saves_the_new_list() {
        let saved: RefCell<Vec<ToDoList>> = RefCell::new(Vec::new());
        let hub = ToDoListHub::new(
            |_u, name| {
                Some(ToDoList {
                    list_name: name.clone(),
                    items: vec![ToDoItem::new("先にあったもの")],
                })
            },
            |_u, list: &ToDoList| saved.borrow_mut().push(list.clone()),
        );

        let after = hub
            .add_item(
                &User::new("uberto"),
                &ListName::new("book"),
                ToDoItem::new("足したもの"),
            )
            .unwrap();

        assert_eq!(after.items.len(), 2, "足した分だけ増える");
        assert_eq!(saved.borrow().len(), 1, "保存が 1 回呼ばれる");
        assert_eq!(saved.borrow()[0], after, "保存されたのは足した後のリスト");
    }

    // -----------------------------------------------------------------
    // 第 6 章: コマンドがイベントを生む
    // -----------------------------------------------------------------

    #[test]
    fn creating_a_list_produces_a_created_event() {
        let events = execute(
            &[],
            ToDoListCommand::CreateList {
                list_name: ListName::new("book"),
            },
        )
        .expect("無いリストは作れる");

        assert_eq!(
            events,
            vec![ToDoListEvent::ListCreated {
                list_name: ListName::new("book")
            }]
        );
    }

    /// 遷移表の全マス。**状態 3 × コマンド 2 = 6 マス。**
    ///
    /// コンパイラが見るのは「枝が揃っているか」だけで、
    /// **行き先が正しいかは見ない**。だから数える。
    ///
    /// なでしこ3 版は 16 マス（状態 4 × コマンド 4）を数えて穴を 1 つ
    /// 見つけた。こちらは 6 マスで、**表そのものを配列で書く**。
    #[test]
    fn every_cell_of_the_table_is_checked() {
        let book = || ListName::new("book");
        let item = || ToDoItem::new("write");
        let created = || ToDoListEvent::ListCreated { list_name: book() };
        let added = || ToDoListEvent::ItemAdded { item: item() };

        // (これまでに起きたこと, コマンド, 期待する結果)
        type Cell = (
            Vec<ToDoListEvent>,
            ToDoListCommand,
            Result<Vec<ToDoListEvent>, Rejected>,
        );
        let table: Vec<Cell> = vec![
            // Missing
            (
                vec![],
                ToDoListCommand::CreateList { list_name: book() },
                Ok(vec![created()]),
            ),
            (
                vec![],
                ToDoListCommand::AddItem { item: item() },
                Err(Rejected::ListDoesNotExist),
            ),
            // Empty
            (
                vec![created()],
                ToDoListCommand::CreateList { list_name: book() },
                Err(Rejected::ListAlreadyExists),
            ),
            (
                vec![created()],
                ToDoListCommand::AddItem { item: item() },
                Ok(vec![added()]),
            ),
            // HasItems
            (
                vec![added()],
                ToDoListCommand::CreateList { list_name: book() },
                Err(Rejected::ListAlreadyExists),
            ),
            (
                vec![added()],
                ToDoListCommand::AddItem { item: item() },
                Ok(vec![added()]),
            ),
        ];

        assert_eq!(table.len(), 6, "状態 3 × コマンド 2 のマスが揃っている");

        for (events, command, expected) in table {
            let state = state_of(&events);
            assert_eq!(
                execute(&events, command.clone()),
                expected,
                "{state:?} に {command:?} を出したとき"
            );
        }
    }

    #[test]
    fn the_hub_rejects_adding_to_a_missing_list() {
        let hub = ToDoListHub::new(|_u, _n| None, |_u, _l: &ToDoList| {});

        let result = hub.handle(
            &User::new("uberto"),
            &ListName::new("nope"),
            ToDoListCommand::AddItem {
                item: ToDoItem::new("write"),
            },
        );

        assert_eq!(result, Err(Rejected::ListDoesNotExist));
    }

    #[test]
    fn the_hub_applies_the_events_it_produced() {
        let saved: RefCell<Vec<ToDoList>> = RefCell::new(Vec::new());
        let hub = ToDoListHub::new(
            |_u, name| {
                Some(ToDoList {
                    list_name: name.clone(),
                    items: vec![ToDoItem::new("先にあったもの")],
                })
            },
            |_u, list: &ToDoList| saved.borrow_mut().push(list.clone()),
        );

        let after = hub
            .handle(
                &User::new("uberto"),
                &ListName::new("book"),
                ToDoListCommand::AddItem {
                    item: ToDoItem::new("足したもの"),
                },
            )
            .expect("あるリストには足せる");

        assert_eq!(after.items.len(), 2);
        assert_eq!(saved.borrow().len(), 1, "保存が 1 回呼ばれる");
    }

    // -----------------------------------------------------------------
    // 第 5 章: イベントを畳み込む
    // -----------------------------------------------------------------

    fn created(name: &str) -> ToDoListEvent {
        ToDoListEvent::ListCreated {
            list_name: ListName::new(name),
        }
    }

    fn added(description: &str) -> ToDoListEvent {
        ToDoListEvent::ItemAdded {
            item: ToDoItem::new(description),
        }
    }

    #[test]
    fn replaying_no_events_gives_an_empty_list() {
        assert_eq!(replay(vec![]), empty_list());
    }

    #[test]
    fn replaying_builds_the_state() {
        let list = replay(vec![created("book"), added("write"), added("publish")]);
        assert_eq!(list.list_name, ListName::new("book"));
        assert_eq!(list.items.len(), 2);
        assert_eq!(list.items[1], ToDoItem::new("publish"));
    }

    #[test]
    fn creating_again_starts_over() {
        // ListCreated は前の状態を見ない。**イベントの意味がそのまま出る。**
        let list = replay(vec![created("book"), added("write"), created("shopping")]);
        assert_eq!(list.list_name, ListName::new("shopping"));
        assert!(list.items.is_empty());
    }

    #[test]
    fn adding_to_a_missing_list_saves_nothing() {
        let saved: RefCell<usize> = RefCell::new(0);
        let hub = ToDoListHub::new(|_u, _n| None, |_u, _l: &ToDoList| *saved.borrow_mut() += 1);

        let after = hub.add_item(
            &User::new("uberto"),
            &ListName::new("nope"),
            ToDoItem::new("足せない"),
        );

        assert!(after.is_none());
        assert_eq!(*saved.borrow(), 0, "無いリストには保存しない");
    }
}
