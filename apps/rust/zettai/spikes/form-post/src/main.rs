//! `tiny_http` が実際に渡す POST のボディと、302 の書き方を確かめる。
//!
//! ```bash
//! cargo run &
//! curl -s -X POST -d 'user=uberto&newname=reading' http://localhost:8099/echo
//! curl -si -X POST -d 'newname=x' http://localhost:8099/redirect | head -3
//! ```

use std::io::Read;
use tiny_http::{Header, Response, Server, StatusCode};

fn main() {
    let server = Server::http("0.0.0.0:8099").expect("8099 を開ける");
    println!("http://localhost:8099/echo");

    for mut request in server.incoming_requests() {
        let mut body = String::new();
        let _ = request.as_reader().read_to_string(&mut body);
        let url = request.url().to_string();
        let method = request.method().as_str().to_string();

        // **実物をそのまま出す。** 推測しない
        println!("method={method} url={url} body={body:?}");
        let headers: Vec<String> = request
            .headers()
            .iter()
            .map(|h| format!("{}: {}", h.field, h.value))
            .collect();
        println!("headers={headers:?}");

        let response = if url.ends_with("/redirect") {
            // 302 の書き方
            let location: Header = "Location: /todo/uberto/reading"
                .parse()
                .expect("ヘッダとして読める");
            Response::from_string("")
                .with_header(location)
                .with_status_code(StatusCode(302))
        } else {
            Response::from_string(format!("method={method} url={url} body={body}"))
                .with_status_code(StatusCode(200))
        };
        let _ = request.respond(response);
    }
}
