import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._



object Antrophic {

  // ===== Request Related classes ======

  /**
   * Main request model for creating a message with Claude
   */
  case class MessageRequest(
                             model: String,
                             messages: Seq[MessageBody],
                             system: Option[String] = None,
                             max_tokens: Int = 1024,
                             temperature: Option[Double] = None,
                             top_p: Option[Double] = None,
                             top_k: Option[Int] = None,
                             stop_sequences: Option[Seq[String]] = None,
                             stream: Boolean = false,
                             //metadata: Option[RequestMetadata] = None
                           )

  /**
   * Message representation for conversations with Claude
   */
  case class MessageBody(
                      role: String, // "user" or "assistant"
                      content:String
                      //content: Either[String, Seq[MessageContent]], // Can be simple string or complex content
                      //name: Option[String] = None
                    )

//  /**
//   * Content block for structured messages (text or image)
//   */
//  sealed trait MessageContent
//
//  /**
//   * Text content block
//   */
//  case class TextContent(
//                          `type`: String = "text",
//                          text: String
//                        ) extends MessageContent
//
//  /**
//   * Image content block
//   */
//  case class ImageContent(
//                           `type`: String = "image",
//                           source: ImageSource
//                         ) extends MessageContent
//
//  /**
//   * Source information for images
//   */
//  case class ImageSource(
//                          `type`: String, // "base64" or "url"
//                          media_type: String, // MIME type like "image/jpeg"
//                          data: String // Base64-encoded data or URL
//                        )

  /**
   * Optional metadata for requests
   */
  case class RequestMetadata(
                              user_id: Option[String] = None
                            )


  implicit val requestMetadataEcoder:Encoder[RequestMetadata] = deriveEncoder[RequestMetadata].mapJson(_.dropNullValues)
  implicit val messageEncoder:Encoder[MessageBody] = deriveEncoder[MessageBody].mapJson(_.dropNullValues)
  implicit val messageRequestEncoder:Encoder[MessageRequest] = deriveEncoder[MessageRequest].mapJson(_.dropNullValues)


  // ===== Response Related classes ======


  /**
   * Response model for message creation
   */
  case class MessageResponse(
                              id: String,
                              `type`: String,
                              role: String,
                              content: Seq[ContentBlock],
                              model: String,
                              stop_reason: Option[String],
                              stop_sequence: Option[String],
                              usage: Usage
                            )

  /**
   * Content block in responses
   */
  case class ContentBlock(
                           `type`: String, // "text" for now
                           text: Option[String] = None
                         )

  /**
   * Token usage information
   */
  case class Usage(
                    input_tokens: Int,
                    output_tokens: Int
                  )


  implicit val usageDecoder:Decoder[Usage] = deriveDecoder[Usage]
  implicit val contentBlockDecoder:Decoder[ContentBlock] = deriveDecoder[ContentBlock]
  implicit val messageResponseDecoder:Decoder[MessageResponse] = deriveDecoder[MessageResponse]

}