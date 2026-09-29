//! 画面の組み立て（第 11 章）。
//!
//! **`tera` を使います。** 2 対象は自作しました（[ADR-010](../../../../docs/adr/ADR-010-own-template.md)・
//! [ADR-022](../../../../docs/adr/ADR-022-template-with-unfilled-check.md)）。理由は
//! 「既製品は未適用のタグを空文字か例外にするだけで、失敗として扱えない」でした。
//!
//! **Rust では成り立ちませんでした。** `tera` は既定で `Result` を返し、
//! 足りない変数の名前まで挙げます（[ADR-036](../../../../docs/adr/ADR-036-tera.md)）。
//!
//! あわせて**値の無害化も `tera` が既定でやります**。第 10 章までの
//! `format!` 直書きにはエスケープがありませんでした。

use tera::{Context, Tera};
use zettai_step5_domain::{ToDoList, ZettaiError};

/// 描けなかったことを、ドメインの言葉に写す。
///
/// **`tera::Error` をドメインに出しません。** `postgres::Error` と同じ扱いです
/// （[ADR-027](../../../../docs/adr/ADR-027-crate-boundary.md)）。
fn render_failed(e: &tera::Error) -> ZettaiError {
    ZettaiError::StoreUnavailable {
        detail: crate::event_store::detail_of(e),
    }
}

const LIST_HTML: &str = r#"<html><body><h1>{{ list_name }}</h1><ul>
{% for item in items %}<li>{{ item }}</li>
{% endfor %}</ul></body></html>"#;

const RENAME_HTML: &str = r#"<html><body><h1>{{ list_name }} の名前を変える</h1>
{% for reason in reasons %}<p class="error">{{ reason }}</p>
{% endfor %}<form method="post" action="/todo/{{ user }}/{{ list_name }}/rename">
<input name="newname" value="">
<button type="submit">変える</button>
</form></body></html>"#;

fn engine() -> Result<Tera, ZettaiError> {
    let mut tera = Tera::default();
    // **名前を `.html` にする。** `tera` の自動エスケープは**拡張子で決まり**、
    // `"list"` のような名前だと効きません（既知の制約 13）。
    // 「既製品を入れたから安全」ではなく、**安全になる設定を確かめる**必要があります。
    tera.add_raw_templates(vec![("list.html", LIST_HTML), ("rename.html", RENAME_HTML)])
        .map_err(|e| render_failed(&e))?;
    Ok(tera)
}

/// ToDo リストの画面。
pub fn render_list(list: &ToDoList) -> Result<String, ZettaiError> {
    let mut ctx = Context::new();
    ctx.insert("list_name", &list.list_name.0);
    let items: Vec<&str> = list.items.iter().map(|i| i.description.as_str()).collect();
    ctx.insert("items", &items);
    engine()?
        .render("list.html", &ctx)
        .map_err(|e| render_failed(&e))
}

/// 名前を変えるフォーム。**理由をすべて並べます。**
pub fn render_rename_form(
    user: &str,
    list_name: &str,
    reasons: &[String],
) -> Result<String, ZettaiError> {
    let mut ctx = Context::new();
    ctx.insert("user", user);
    ctx.insert("list_name", list_name);
    ctx.insert("reasons", reasons);
    engine()?
        .render("rename.html", &ctx)
        .map_err(|e| render_failed(&e))
}

#[cfg(test)]
mod tests {
    use super::*;
    use zettai_step5_domain::{ListName, ToDoItem};

    fn book() -> ToDoList {
        ToDoList {
            list_name: ListName::new("book"),
            items: vec![ToDoItem::new("write chapter")],
        }
    }

    #[test]
    fn a_list_is_rendered() {
        let html = render_list(&book()).expect("描ける");
        assert!(html.contains("<h1>book</h1>"));
        assert!(html.contains("<li>write chapter</li>"));
    }

    /// **値が無害化される。** 第 10 章までの `format!` 直書きには無かった。
    ///
    /// **既定では効きませんでした。** `tera` の自動エスケープは
    /// テンプレート名の拡張子で決まります（既知の制約 13）。
    #[test]
    fn a_dangerous_value_is_escaped() {
        let list = ToDoList {
            list_name: ListName::new("book"),
            items: vec![ToDoItem::new("<script>alert(1)</script>")],
        };
        let html = render_list(&list).expect("描ける");
        assert!(!html.contains("<script>"), "生のタグが残っている: {html}");
        assert!(html.contains("&lt;script&gt;"));
    }

    #[test]
    fn the_form_shows_every_reason() {
        let reasons = vec!["利用者名が空です".to_string(), "長すぎます".to_string()];
        let html = render_rename_form("uberto", "book", &reasons).expect("描ける");
        assert!(html.contains("利用者名が空です"));
        assert!(html.contains("長すぎます"));
    }

    /// **渡し忘れたら失敗になる。** これが要求そのもの。
    #[test]
    fn a_missing_tag_is_a_failure() {
        let mut tera = Tera::default();
        tera.add_raw_template("t", "{{ name }} と {{ forgotten }}")
            .expect("読める");
        let mut ctx = Context::new();
        ctx.insert("name", "book");

        let result = tera.render("t", &ctx);

        assert!(result.is_err(), "渡し忘れたのに通ってしまった");
        // **足りない変数の名前まで出る**
        let detail = format!("{:?}", result.unwrap_err());
        assert!(detail.contains("forgotten"), "実際: {detail}");
    }
}
