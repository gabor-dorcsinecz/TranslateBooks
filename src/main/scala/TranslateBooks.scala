import Antrophic.*
import cats.effect.*
import cats.implicits.*
import fs2.io.file
import fs2.io.file.{Files, Flags, Path}
import fs2.text
import io.circe.*
import io.circe.syntax.*
import org.http4s.*
import org.http4s.circe.*
import org.http4s.circe.CirceEntityCodec.given
import org.http4s.client.Client
import org.http4s.client.middleware.Logger
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.headers.*
import org.http4s.implicits.*
import org.typelevel.ci.CIStringSyntax
import retry.*
import retry.RetryPolicies.*

import java.io.{PrintWriter, StringWriter}
import scala.concurrent.duration.*

/*
  Translate my german books to english, by chopping them up and sending them to Anthropic.
  It waits for rate-limits, handles errors and timeouts and retries in case of failure
 */
object TranslateBooks extends IOApp.Simple {

  //Set your Anthropic API Key
  val anthropicApiKey: String =
    sys.env.getOrElse("ANTHROPIC_API_KEY", throw new Exception("ANTHROPIC_API_KEY not set. Use: export ANTHROPIC_API_KEY=xxxxxx"))

  val inputFile = "./toTranslate/GermanText.txt"
  val outputFile = inputFile.take(inputFile.lastIndexOf(".")) + "-translated.txt"

  val paragraphSeparator = "\n\n"

  val systemPrompt =
    """
      |The following is an antroposophy lecture in german following Rudolf Steiner's tradition. Please translate it to english
      |Do not add any extra text to the translation. Respond only with the translation, no intro needed.
      |""".stripMargin

  def readFile(pathStr:String):IO[String] = {
    Files[IO]
      .readAll(Path(pathStr))
      .through(text.utf8.decode)
      .compile
      .string
  }

  def isChapterStart(paragraph: String): Boolean =
    paragraph.trim.matches("(?i)^Kapitel\\s+\\d+")

  /*
  We have a list of paragraphs and we want to return a list of strings we can send for translation
  The word count should not exceed wordLimit, not include new chapters (the next item will have the new chapter)
   */
  def splitIntoChunks(paragraphs: List[String], wordLimit: Int): List[String] = {
    val builder = List.newBuilder[String]
    var currentChunk = new StringBuilder
    var wordCount = 0

    def flush(): Unit = {
      if (currentChunk.nonEmpty) {
        builder += currentChunk.toString()
        currentChunk = new StringBuilder
        wordCount = 0
      }
    }

    paragraphs.foreach { para =>
      val wordsInPara = para.split("\\s+").length
      val startsNewChapter = isChapterStart(para)

      if ((wordCount + wordsInPara > wordLimit && !startsNewChapter) || startsNewChapter) {
        flush()
      }

      if (currentChunk.nonEmpty) currentChunk.append("\n\n")
      currentChunk.append(para)
      wordCount += wordsInPara
    }

    flush() // make sure last chunk is included
    builder.result()
  }

  //Read the contents of a file and split them up into chunks we can send for translation
  def fileToChunks(fileName:String, wordLimit:Int):IO[List[String]] = {
    readFile(fileName).map { fileContent =>
      val paragraphs = fileContent.split(paragraphSeparator).toList.map(_.trim).filter(_.nonEmpty)
      splitIntoChunks(paragraphs, wordLimit)
    }
  }

  val model_sonnet = "claude-3-5-sonnet-20241022"
  val model_haiku = "claude-3-5-haiku-20241022"

  //Create the body of an Antrhopic API request
  def createMessage(txt:String):MessageRequest = {
    MessageRequest(
      model = model_haiku,
      messages = Seq(MessageBody(role = "user", content = txt.asJson.noSpaces)),
      system=Some(systemPrompt),
      temperature = Some(0.1),
      max_tokens = 8192
    )
  }


  //Create an http POST request for the Antrhopic API endpoint
  def createRequest(message:MessageRequest):Request[IO] = {
    Request[IO](
      method = Method.POST,
      uri = uri"https://api.anthropic.com/v1/messages",
      headers = Headers(
        `Content-Type`(MediaType.application.json),
        Header.Raw(ci"x-api-key", anthropicApiKey ),
        Header.Raw(ci"anthropic-version","2023-06-01"),
        Accept(MediaType.application.json)
      )
    ).withEntity(message.asJson)
  }

  // Add the part String parameter to the output file
  def appendToFile(part: String): IO[Unit] =
    fs2.Stream.emit(part + paragraphSeparator)
      .through(text.utf8.encode)
      .through(Files[IO].writeAll(Path(outputFile), Flags.Append))
      .compile
      .drain

  def sampleText(in:String):String = in.take(190).replaceAll("\n"," # ")

  def traceRequestError(e: Throwable, details: RetryDetails): IO[Unit] = {
    val sw = new StringWriter()
    val pw = new PrintWriter(sw)
    e.printStackTrace(pw)
    IO.println(s"### Failed attempt ${details.retriesSoFar} upcoming: ${details.upcomingDelay} Error: ${e.getMessage} # ${sw.toString}")
  }

  def translateChunk(chunkText:String,client:Client[IO]):IO[String] = {
    for {
      _ <- IO.println(s"<< Sending Next chunk, length: ${chunkText.length} # ${sampleText(chunkText)}")
      message <- IO.pure(createMessage(chunkText))
      //_ <- IO.println(message.asJson)
      req = createRequest(message)
      //_ <- IO.println(req.headers)
        response <-
        retryingOnAllErrors[MessageResponse](
          policy = exponentialBackoff[IO](20.seconds),
          onError = traceRequestError
        )(
          client.run(req).use(resp =>
            //IO.println(s">> Response headers: ${resp.headers.mkString(" # ",_ => false)}") >>
            resp.as[MessageResponse])
        )

      translation = response.content.headOption.flatMap(_.text).getOrElse("=== ERROR === Failed request")
      _ <- IO.println(s">> Response ${sampleText(translation)}")
      _ <- appendToFile(translation)
      _ <- IO.sleep(1.second)  //Rate Limits at Anthropic
    } yield (translation)
  }

  override def run:IO[Unit] = {
    EmberClientBuilder
      .default[IO]
      .withTimeout(500.seconds)  //Longer translations can take more than 2 minutes
      .withIdleConnectionTime(500.seconds)
      .build.use { baseClient =>
      val client = Logger(logHeaders = true, logBody = true)(baseClient)

      fileToChunks(inputFile,200)  //At about 350 words in about 10 % of requests Anthropic will only translate the first half
        .map(chunklist => chunklist.map(chunk => translateChunk(chunk,baseClient)))
        .flatMap(_.sequence)
        .map(_ => ())
    }
  }

}