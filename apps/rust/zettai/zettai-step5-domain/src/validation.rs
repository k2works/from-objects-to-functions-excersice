//! 複数の理由を集める型（第 11 章）。
//!
//! `Result` は**最初の失敗で止まります**。「利用者名が空 かつ 新しい名前が
//! 41 文字」でも理由が 1 つしか返りません。
//!
//! **溜める型を別に作ります**（[ADR-035]）。`Result` と役割が分かれ、
//! 使い分けが型に出ます。
//!
//! | 型 | ふるまい |
//! | :--- | :--- |
//! | `Result` | 最初の失敗で止まる（モナド） |
//! | `Validated` | **理由を溜める**（アプリカティブ） |

use crate::ZettaiError;

/// 検証の結果。**失敗しても先を見る。**
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum Validated<T> {
    Valid(T),
    Invalid(Vec<ZettaiError>),
}

impl<T> Validated<T> {
    pub fn valid(value: T) -> Self {
        Validated::Valid(value)
    }

    pub fn invalid(reason: ZettaiError) -> Self {
        Validated::Invalid(vec![reason])
    }

    /// 中身に関数をかける。**ファンクタ**（第 7・8 章と同じ形）。
    pub fn map<U>(self, f: impl FnOnce(T) -> U) -> Validated<U> {
        match self {
            Validated::Valid(v) => Validated::Valid(f(v)),
            Validated::Invalid(e) => Validated::Invalid(e),
        }
    }

    /// **合わせる。** どちらも失敗なら理由を連ねます。
    ///
    /// これがアプリカティブです。`and_then`（モナド）との違いは 1 点で、
    /// **右を見るのに左の成功が要りません**。
    pub fn zip<U>(self, other: Validated<U>) -> Validated<(T, U)> {
        match (self, other) {
            (Validated::Valid(a), Validated::Valid(b)) => Validated::Valid((a, b)),
            // **両方だめなら両方返す。** ここがこの型の存在理由
            (Validated::Invalid(a), Validated::Invalid(b)) => Validated::Invalid([a, b].concat()),
            (Validated::Invalid(a), _) => Validated::Invalid(a),
            (_, Validated::Invalid(b)) => Validated::Invalid(b),
        }
    }

    /// 境界で `Result` に戻す。**溜めるのは検証の中だけ。**
    pub fn into_result(self) -> Result<T, Vec<ZettaiError>> {
        match self {
            Validated::Valid(v) => Ok(v),
            Validated::Invalid(e) => Err(e),
        }
    }

    pub fn is_valid(&self) -> bool {
        matches!(self, Validated::Valid(_))
    }

    /// 理由の並び。成功なら空。
    pub fn reasons(&self) -> &[ZettaiError] {
        match self {
            Validated::Valid(_) => &[],
            Validated::Invalid(e) => e,
        }
    }
}

/// 何も検証していない値。**`zip` の単位元。**
pub fn unit() -> Validated<()> {
    Validated::Valid(())
}

// ---------------------------------------------------------------------------
// リスト名の変更に使う検証（第 11 章）
// ---------------------------------------------------------------------------

/// リスト名の上限。
pub const NAME_LIMIT: usize = 40;

/// 利用者名を検証する。
pub fn valid_user(user: &str) -> Validated<crate::User> {
    if user.is_empty() {
        return Validated::invalid(ZettaiError::EmptyUserName);
    }
    Validated::valid(crate::User::new(user))
}

/// 空でないこと。
fn not_empty(name: &str) -> Validated<()> {
    if name.is_empty() {
        return Validated::invalid(ZettaiError::EmptyListName);
    }
    unit()
}

/// 長すぎないこと。**文字数で数えます。** バイト数だと日本語で早くだめになります。
fn within_limit(name: &str) -> Validated<()> {
    if name.chars().count() > NAME_LIMIT {
        return Validated::invalid(ZettaiError::ListNameTooLong { limit: NAME_LIMIT });
    }
    unit()
}

/// 記号を含まないこと。**URL に載り、画面に出るため。**
fn without_markup(name: &str) -> Validated<()> {
    if name.chars().any(|c| "<>&\"'/".contains(c)) {
        return Validated::invalid(ZettaiError::ListNameHasMarkup);
    }
    unit()
}

/// 新しいリスト名を検証する。
///
/// **規則が 3 つあり、同時に破れます。** 1 つの値に複数の規則をかけるとき、
/// `Result` では最初の 1 つしか返りません。
pub fn valid_new_name(name: &str) -> Validated<crate::ListName> {
    not_empty(name)
        .zip(within_limit(name))
        .zip(without_markup(name))
        .map(|_| crate::ListName::new(name))
}

/// 名前の変更に要るものをまとめて検証する。
///
/// **両方だめなら理由が 2 つ返ります。**
pub fn valid_rename(
    user: &str,
    new_name: &str,
) -> Result<(crate::User, crate::ListName), Vec<ZettaiError>> {
    valid_user(user).zip(valid_new_name(new_name)).into_result()
}

#[cfg(test)]
mod tests {
    use super::*;

    /// **両方だめな入力。** 41 文字 かつ 記号を含む。
    ///
    /// 計画には「利用者名が空 かつ 名前が 41 文字」と書いたが、
    /// **利用者名はパスから来るので HTTP 経路では空にできなかった**
    /// （`/todo//book/rename` は空の部分が落ちて 3 要素になる）。
    /// **1 つの値に複数の規則をかける形に置き直した。**
    fn bad_name() -> String {
        "<script>".repeat(6)
    }

    #[test]
    fn the_example_really_fails_two_rules() {
        assert_eq!(bad_name().chars().count(), 48, "40 文字を超える");
        assert!(bad_name().contains('<'), "記号を含む");
    }

    #[test]
    fn both_reasons_come_back() {
        let reasons = valid_new_name(&bad_name())
            .into_result()
            .expect_err("断られる");
        assert_eq!(reasons.len(), 2, "実際: {reasons:?}");
        assert!(reasons.contains(&ZettaiError::ListNameTooLong { limit: NAME_LIMIT }));
        assert!(reasons.contains(&ZettaiError::ListNameHasMarkup));
    }

    #[test]
    fn one_reason_when_only_one_rule_is_broken() {
        let reasons = valid_new_name(&"あ".repeat(41))
            .into_result()
            .expect_err("断られる");
        assert_eq!(reasons.len(), 1, "長いだけ。記号は無い");
    }

    #[test]
    fn both_right_means_valid() {
        let (user, name) = valid_rename("uberto", "reading").expect("通る");
        assert_eq!(user, crate::User::new("uberto"));
        assert_eq!(name, crate::ListName::new("reading"));
    }

    #[test]
    fn exactly_the_limit_is_allowed() {
        let ok = "あ".repeat(NAME_LIMIT);
        assert!(
            valid_new_name(&ok).is_valid(),
            "{NAME_LIMIT} 文字ちょうどは通る"
        );
    }

    #[test]
    fn an_empty_name_is_one_reason_not_two() {
        // **空文字は 40 文字以内。** だから「空」と「長すぎ」は同時に起きない
        let reasons = valid_new_name("").reasons().to_vec();
        assert_eq!(reasons, vec![ZettaiError::EmptyListName]);
    }
}
