mod HttpTestClient {

    import java.net.URL
    import java.net.URLConnection
    import java.net.HttpURLConnection
    import java.nio.charset.StandardCharsets
    import java.lang.{String => JString}

    pub def get(port: Int32, path: String): (Int32, String) \ IO = {
        let url = new URL("http://localhost:${port}${path}");
        let c: URLConnection = url.openConnection();
        let conn = unchecked_cast(c as HttpURLConnection);
        let code = conn.getResponseCode();
        let stream = if (code < 400) conn.getInputStream() else conn.getErrorStream();
        let body = new JString(stream.readAllBytes(), StandardCharsets.UTF_8);
        (code, body)
    }

    pub def post(port: Int32, path: String, body: String): (Int32, String) \ IO = {
        let url = new URL("http://localhost:${port}${path}");
        let c: URLConnection = url.openConnection();
        let conn = unchecked_cast(c as HttpURLConnection);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        let out = conn.getOutputStream();
        out.write(body.getBytes(StandardCharsets.UTF_8));
        out.close();
        let code = conn.getResponseCode();
        let stream = if (code < 400) conn.getInputStream() else conn.getErrorStream();
        let text = new JString(stream.readAllBytes(), StandardCharsets.UTF_8);
        (code, text)
    }

}
