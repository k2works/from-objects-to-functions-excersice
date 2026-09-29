//! Zettai の起動。**配線だけを持つ。**
//!
//! 「何を返すか」は `http::handle` が決める。ここはサーバを組み立てて
//! 応答を渡すだけにする。こうすると `handle` を HTTP 無しでテストできる。

use tiny_http::{Header, Response, Server, StatusCode};
use zettai_step4_domain::ToDoListHub;
use zettai_step4_http::http::handle;
use zettai_step4_http::store::InMemoryLists;

fn main() {
    let server = Server::http("0.0.0.0:8080").expect("8080 を開ける");

    // **配線はここだけ。** 保存先を作り、ハブに関数値として渡す。
    let store = InMemoryLists::seeded();
    let hub = ToDoListHub::new(
        |user, name| store.fetch(user, name),
        |user, list| store.save(user, list),
    );
    let html: Header = "Content-Type: text/html; charset=utf-8"
        .parse()
        .expect("ヘッダとして読める");

    println!("http://localhost:8080/todo/uberto/book");

    for mut request in server.incoming_requests() {
        let mut body = String::new();
        let _ = request.as_reader().read_to_string(&mut body);
        let method = request.method().as_str().to_string();
        let url = request.url().to_string();
        let reply = handle(&hub, &method, &url, &body);
        let response = Response::from_string(reply.body)
            .with_header(html.clone())
            .with_status_code(StatusCode(reply.status));
        let _ = request.respond(response);
    }
}
