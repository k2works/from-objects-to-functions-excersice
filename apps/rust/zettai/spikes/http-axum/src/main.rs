use axum::{extract::Path, response::Html, routing::get, Router};

fn todo_list_html(user: &str, list: &str) -> String {
    format!("<html><body><h1>{list}</h1><ul><li>write chapter</li></ul><p>{user}</p></body></html>")
}

async fn show(Path((user, list)): Path<(String, String)>) -> Html<String> {
    Html(todo_list_html(&user, &list))
}

#[tokio::main]
async fn main() {
    let app = Router::new().route("/todo/{user}/{list}", get(show));
    let listener = tokio::net::TcpListener::bind("0.0.0.0:8080").await.unwrap();
    axum::serve(listener, app).await.unwrap();
}
