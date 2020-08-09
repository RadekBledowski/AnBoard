package pl.rkbdi.anboard.inputmethod.dictionarypack

/**
 * A simple interface to report problems.
 */
interface ProblemReporter {
    fun report(e: Exception?)
}