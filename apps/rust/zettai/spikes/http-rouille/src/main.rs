use rouille::{router, Response};

fn todo_list_html(user: &str, list: &str) -> String {
    format!("<html><body><h1>{list}</h1><ul><li>write chapter</li></ul><p>{user}</p></body></html>")
}

fn main() {
    rouille::start_server("0.0.0.0:8080", move |request| {
        router!(request,
            (GET) (/todo/{user: String}/{list: String}) => {
                Response::html(todo_list_html(&user, &list))
            },
            _ => Response::html("<h1>404</h1>").with_status_code(404)
        )
    });
}
