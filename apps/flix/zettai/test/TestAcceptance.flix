mod TestAcceptance {

    use Domain.{User, ListName, ToDoItem, ToDoList}

    def lists(): Map[User, List[ToDoList]] =
        Map#{
            User.User("uberto") => List#{
                ToDoList.ToDoList(
                    ListName.ListName("book"),
                    List#{
                        Domain.todo("write chapter"),
                        Domain.todo("publish book"),
                        Domain.todo("read <today> & \"FP\"")
                    }
                )
            }
        }

    @Test
    def seeTheItemsOfAList(): Unit \ {Assert, IO} =
        Routes.onEveryRoute(lists(), Scenarios.seeTheItemsOfAList)

    @Test
    def aMissingListIsNotFound(): Unit \ {Assert, IO} =
        Routes.onEveryRoute(lists(), Scenarios.aMissingListIsNotFound)

    @Test
    def aUserWithoutListsFindsNothing(): Unit \ {Assert, IO} =
        Routes.onEveryRoute(Map#{}, Scenarios.aMissingListIsNotFound)

}
