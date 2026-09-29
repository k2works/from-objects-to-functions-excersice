//! Zettai の起動。**配線だけを持つ。**
//!
//! 「何を返すか」は `http::handle` が決める。ここはサーバを組み立てて
//! 応答を渡すだけにする。こうすると `handle` を HTTP 無しでテストできる。

use tiny_http::{Header, Response, Server, StatusCode};
use zettai_step1_http::http::handle;

fn main() {
    let server = Server::http("0.0.0.0:8080").expect("8080 を開ける");
    let html: Header = "Content-Type: text/html; charset=utf-8"
        .parse()
        .expect("ヘッダとして読める");

    println!("http://localhost:8080/todo/uberto/book");

    for request in server.incoming_requests() {
        let reply = handle(request.url());
        let response = Response::from_string(reply.body)
            .with_header(html.clone())
            .with_status_code(StatusCode(reply.status));
        let _ = request.respond(response);
    }
}
