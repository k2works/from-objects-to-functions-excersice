mod Foundation {

    pub def wordScore(word: String): Int32 = String.length(word)

    pub def nonBlankName(name: String): Result[String, String] =
        if (String.isEmpty(String.trim(name)))
            Err("名前は空にできません")
        else
            Ok(name)

    pub def loadGreeting(load: Unit -> String \ ef): String \ ef = load()

}
