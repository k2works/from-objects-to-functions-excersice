use tiny_http::{Response, Server};

fn todo_list_html(user: &str, list: &str) -> String {
    format!("<html><body><h1>{list}</h1><ul><li>write chapter</li></ul><p>{user}</p></body></html>")
}

fn main() {
    let server = Server::http("0.0.0.0:8080").unwrap();
    for request in server.incoming_requests() {
        let parts: Vec<&str> = request.url().trim_matches('/').split('/').collect();
        let response = match parts.as_slice() {
            ["todo", user, list] => Response::from_string(todo_list_html(user, list)),
            _ => Response::from_string("<h1>404</h1>"),
        };
        let _ = request.respond(response);
    }
}
