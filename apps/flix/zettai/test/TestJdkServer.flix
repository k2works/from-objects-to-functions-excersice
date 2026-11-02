mod TestJdkServer {

    use Domain.{User, ListName, ToDoItem, ToDoList}
    use Http.{Request, Response}

    def lists(): Map[User, List[ToDoList]] =
        Map#{
            User.User("uberto") => List#{
                ToDoList.ToDoList(
                    ListName.ListName("book"),
                    List#{Domain.todo("write chapter"), Domain.todo("publish book")}
                )
            }
        }

    def app(request: Request): Response =
        Http.route(Hub.inMemory(lists()), request)

    @Test
    def servesTheListOverRealHttp(): Unit \ {Assert, IO} = {
        let server = JdkServer.start(0, app);
        let port = JdkServer.port(server);
        let (status, body) = HttpTestClient.get(port, "/todo/uberto/book");
        JdkServer.stop(server);
        Assert.assertEq(expected = 200, status);
        Assert.assertTrue(String.contains(substr = "<li>write chapter</li>", body))
    }

    @Test
    def answersNotFoundOverRealHttp(): Unit \ {Assert, IO} = {
        let server = JdkServer.start(0, app);
        let port = JdkServer.port(server);
        let (status, _) = HttpTestClient.get(port, "/todo/uberto/missing");
        JdkServer.stop(server);
        Assert.assertEq(expected = 404, status)
    }

}
