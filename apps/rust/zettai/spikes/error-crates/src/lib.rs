//! `?` が経路をまたいで使えるか（Unit 4 / スパイク 3）。
#[derive(Debug, PartialEq, Eq)]
pub enum DomainError {
    NotFound,
}

#[derive(Debug, PartialEq, Eq)]
pub enum HttpError {
    Domain(DomainError),
    BadRequest,
}

/// **`From` を書けば `?` が自動で変換する。**
impl From<DomainError> for HttpError {
    fn from(e: DomainError) -> Self {
        HttpError::Domain(e)
    }
}

fn domain_call(ok: bool) -> Result<i32, DomainError> {
    if ok {
        Ok(1)
    } else {
        Err(DomainError::NotFound)
    }
}

/// E が違っても `?` がそのまま書ける。
pub fn http_call(ok: bool) -> Result<i32, HttpError> {
    let n = domain_call(ok)?;
    Ok(n + 1)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn question_mark_converts_across_the_boundary() {
        assert_eq!(http_call(true), Ok(2));
        assert_eq!(http_call(false), Err(HttpError::Domain(DomainError::NotFound)));
    }
}
