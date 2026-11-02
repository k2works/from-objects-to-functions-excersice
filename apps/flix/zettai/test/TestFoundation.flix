mod TestFoundation {

    @Test
    def wordScoreReturnsTheSameValueForTheSameWord(): Unit \ Assert = {
        Assert.assertEq(expected = 4, Foundation.wordScore("Flix"));
        Assert.assertEq(expected = 4, Foundation.wordScore("Flix"))
    }

    @Test
    def nonBlankNameReturnsFailureAsValue(): Unit \ Assert = {
        Assert.assertEq(expected = Err("名前は空にできません"), Foundation.nonBlankName(""));
        Assert.assertEq(expected = Ok("仕事"), Foundation.nonBlankName("仕事"))
    }

    @Test
    def loadGreetingTakesTheEffectOfTheGivenFunction(): Unit \ Assert =
        Assert.assertEq(expected = "こんにちは", Foundation.loadGreeting(() -> "こんにちは"))

}
