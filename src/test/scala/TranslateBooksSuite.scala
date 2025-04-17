import cats.effect.IO
import munit.CatsEffectSuite

import TranslateBooks.*
import munit.CatsEffectSuite
import cats.effect.IO

class TranslateBooksSpec extends CatsEffectSuite {

  test("isChapterStart should identify chapters correctly") {
    assert(isChapterStart("Kapitel 1"))
    assert(isChapterStart("kapitel 99"))
    assert(!isChapterStart("Einleitung"))
  }

  test("splitIntoChunks should chunk paragraphs respecting word limits and chapters") {
    val paras = List(
      "Kapitel 1",
      "Dies ist ein Absatz mit ein paar Wörtern.",
      "Noch ein kurzer Absatz.",
      "Kapitel 2",
      "Absatz nach Kapitel 2",
      "Ein letzter Absatz."
    )

    val chunks = splitIntoChunks(paras, wordLimit = 5)
    println(chunks)
    assertEquals(chunks.length, 6)
    assert(clue(chunks.head).startsWith("Kapitel 1"))
    assert(clue(chunks(3)).startsWith("Kapitel 2"))
  }

  test("createMessage should embed user text and system prompt") {
    val message = createMessage("Hallo Welt!")
    assertEquals(message.model, model_haiku)
    assert(message.messages.head.content.contains("Hallo Welt"))
    assert(message.system.get.contains("Rudolf Steiner"))
  }

  test("sampleText should format string snippet with line breaks") {
    val input = "line 1\nline 2\nline 3"
    assertEquals(sampleText(input), "line 1 # line 2 # line 3")
  }

  test("fileToChunks should produce IO with expected number of chunks") {
    val content = "Kapitel 1\n\nEin kurzer Absatz.\n\nKapitel 2\n\nNoch einer."
    val testFilePath = "test-input.txt"

    val setup = fs2.Stream.emit(content)
      .through(fs2.text.utf8.encode)
      .through(fs2.io.file.Files[IO].writeAll(fs2.io.file.Path(testFilePath)))
      .compile.drain

    val test = for {
      _ <- setup
      chunks <- fileToChunks(testFilePath, 10)
    } yield assertEquals(chunks.length, 2)

    test
  }

}
