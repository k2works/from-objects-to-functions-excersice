//! 複数のエラーをどの型で集めるか（ゲート 2）。
//!
//! いまの `ZettaiError` は**単一の enum** で、`Result` は最初の失敗で止まる。
//! 「利用者名が空 かつ 新しい名前が 41 文字」で理由を 2 つ返したい。

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ZettaiError {
    ListNotFound,
    EmptyUser,
    NameTooLong { limit: usize },
}

/// いまの形。**最初の失敗で止まる。**
pub fn current(user: &str, name: &str) -> Result<(String, String), ZettaiError> {
    let user = check_user(user)?;
    let name = check_name(name)?;
    Ok((user, name))
}

pub fn check_user(user: &str) -> Result<String, ZettaiError> {
    if user.is_empty() {
        return Err(ZettaiError::EmptyUser);
    }
    Ok(user.to_string())
}

pub fn check_name(name: &str) -> Result<String, ZettaiError> {
    if name.chars().count() > 40 {
        return Err(ZettaiError::NameTooLong { limit: 40 });
    }
    Ok(name.to_string())
}

// ===========================================================================
// 案 A: ZettaiError に Multiple を足す
// ===========================================================================

pub mod a_multiple {
    use super::*;

    /// 理由を入れ子にする。**型は 1 つのまま。**
    #[derive(Debug, Clone, PartialEq, Eq)]
    pub enum Error {
        ListNotFound,
        EmptyUser,
        NameTooLong { limit: usize },
        /// **自分自身の並びを持つ。**
        Multiple(Vec<Error>),
    }

    pub fn both(user: &str, name: &str) -> Result<(String, String), Error> {
        let mut reasons = Vec::new();
        if user.is_empty() {
            reasons.push(Error::EmptyUser);
        }
        if name.chars().count() > 40 {
            reasons.push(Error::NameTooLong { limit: 40 });
        }
        match reasons.len() {
            0 => Ok((user.to_string(), name.to_string())),
            1 => Err(reasons.remove(0)),
            _ => Err(Error::Multiple(reasons)),
        }
    }
}

// ===========================================================================
// 案 B: 別の型 Validated<T> を作る（アプリカティブ）
// ===========================================================================

pub mod b_validated {
    use super::*;

    /// **失敗を溜める型。** `Result` と違い、最初で止まらない。
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

        /// **合わせる。** どちらも失敗なら理由を連ねる。
        ///
        /// これがアプリカティブ。`and_then`（モナド）と違い、
        /// **右を見るのに左の成功を要らない。**
        pub fn zip<U>(self, other: Validated<U>) -> Validated<(T, U)> {
            match (self, other) {
                (Validated::Valid(a), Validated::Valid(b)) => Validated::Valid((a, b)),
                (Validated::Invalid(a), Validated::Invalid(b)) => {
                    Validated::Invalid([a, b].concat())
                }
                (Validated::Invalid(a), _) => Validated::Invalid(a),
                (_, Validated::Invalid(b)) => Validated::Invalid(b),
            }
        }

        pub fn map<U>(self, f: impl FnOnce(T) -> U) -> Validated<U> {
            match self {
                Validated::Valid(v) => Validated::Valid(f(v)),
                Validated::Invalid(e) => Validated::Invalid(e),
            }
        }

        /// 境界で `Result` に戻す。
        pub fn into_result(self) -> Result<T, Vec<ZettaiError>> {
            match self {
                Validated::Valid(v) => Ok(v),
                Validated::Invalid(e) => Err(e),
            }
        }
    }

    fn user_of(user: &str) -> Validated<String> {
        match check_user(user) {
            Ok(v) => Validated::valid(v),
            Err(e) => Validated::invalid(e),
        }
    }

    fn name_of(name: &str) -> Validated<String> {
        match check_name(name) {
            Ok(v) => Validated::valid(v),
            Err(e) => Validated::invalid(e),
        }
    }

    pub fn both(user: &str, name: &str) -> Result<(String, String), Vec<ZettaiError>> {
        user_of(user).zip(name_of(name)).into_result()
    }
}

// ===========================================================================
// 案 C: Result<T, Vec<ZettaiError>> にする
// ===========================================================================

pub mod c_vec {
    use super::*;

    pub fn both(user: &str, name: &str) -> Result<(String, String), Vec<ZettaiError>> {
        let mut reasons = Vec::new();
        if let Err(e) = check_user(user) {
            reasons.push(e);
        }
        if let Err(e) = check_name(name) {
            reasons.push(e);
        }
        if reasons.is_empty() {
            Ok((user.to_string(), name.to_string()))
        } else {
            Err(reasons)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    const TOO_LONG: &str = "ああああああああああああああああああああああああああああああああああああああああa";

    #[test]
    fn the_current_shape_stops_at_the_first_failure() {
        // **理由が 1 つしか返らない。** これが第 11 章の出発点
        assert_eq!(current("", TOO_LONG), Err(ZettaiError::EmptyUser));
    }

    #[test]
    fn a_collects_both() {
        let result = a_multiple::both("", TOO_LONG);
        match result {
            Err(a_multiple::Error::Multiple(reasons)) => assert_eq!(reasons.len(), 2),
            other => panic!("想定と違う: {other:?}"),
        }
    }

    #[test]
    fn b_collects_both() {
        assert_eq!(b_validated::both("", TOO_LONG).unwrap_err().len(), 2);
    }

    #[test]
    fn c_collects_both() {
        assert_eq!(c_vec::both("", TOO_LONG).unwrap_err().len(), 2);
    }

    #[test]
    fn the_input_really_fails_both_checks() {
        // **計画に書いた具体例が実物と合っているか。**
        // なでしこ3 版はここで手戻りした（空文字は 40 文字以内なので同時にだめにならない）
        assert_eq!(TOO_LONG.chars().count(), 41);
        assert!(check_user("").is_err());
        assert!(check_name(TOO_LONG).is_err());
    }
}
