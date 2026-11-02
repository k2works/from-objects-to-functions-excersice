mod JdkServer {

    import com.sun.net.httpserver.HttpExchange
    import com.sun.net.httpserver.HttpHandler
    import com.sun.net.httpserver.HttpServer
    import java.net.InetSocketAddress
    import java.nio.charset.StandardCharsets

    use Http.{Request, Response}

    pub def start(port: Int32, app: Request -> Response \ ef): HttpServer \ IO = {
        let server = HttpServer.create(new InetSocketAddress(port), 0);
        let adapter = new HttpHandler {
            def handle(_this: HttpHandler, exchange: HttpExchange): Unit \ IO + ef = {
                let request = {
                    method = exchange.getRequestMethod(),
                    path = exchange.getRequestURI().getPath()
                };
                let response = app(request);
                let bytes = response#body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", response#contentType);
                exchange.sendResponseHeaders(response#status, Int32.toInt64(Array.length(bytes)));
                let out = exchange.getResponseBody();
                out.write(bytes);
                out.close()
            }
        };
        server.createContext("/", adapter);
        server.start();
        server
    }

    pub def port(server: HttpServer): Int32 \ IO =
        server.getAddress().getPort()

    pub def stop(server: HttpServer): Unit \ IO =
        server.stop(0)

}
