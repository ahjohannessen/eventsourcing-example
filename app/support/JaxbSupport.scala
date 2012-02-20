package support

import java.io._
import javax.xml.bind._

import com.sun.jersey.api.json.JSONJAXBContext

import play.api.http._
import play.api.libs.iteratee._
import play.api.mvc._

import dev.example.eventsourcing.web.SysError

case class JaxbXml(obj: AnyRef)
case class JaxbJson(obj: AnyRef)
case class Jaxb(obj: AnyRef)

trait JaxbSupport extends JaxbBodyParsers with JaxbWriteables with JaxbContentTypeOfs {
  def location(uri: String, elem: String) = "%s/%s" format (uri.split('?')(0), elem)
}

object JaxbSupport {
  def toXmlBytes(obj: AnyRef)(implicit codec: Codec, context: JAXBContext): Array[Byte] = {
    val marshaller = context.createMarshaller()
    val stream = new ByteArrayOutputStream

    marshaller.setProperty(Marshaller.JAXB_ENCODING, codec.charset)
    marshaller.marshal(obj, stream)

    stream.toByteArray
  }

  def toJsonBytes(obj: AnyRef)(implicit codec: Codec, context: JSONJAXBContext): Array[Byte] = {
    val marshaller = context.createJSONMarshaller()
    val stream = new ByteArrayOutputStream
    val writer = new OutputStreamWriter(stream, codec.charset)

    marshaller.marshallToJSON(obj, writer)

    writer.flush()
    stream.toByteArray
  }

  def fromXmlBytes[A](bytes: Array[Byte], charset: String, expectedType: Class[A])(implicit context: JAXBContext): A = {
    val stream = new ByteArrayInputStream(bytes)
    val reader = new InputStreamReader(stream, charset)
    val result = context.createUnmarshaller().unmarshal(reader)
    expectedType.cast(result)
  }

  def fromJsonBytes[A](bytes: Array[Byte], charset: String, expectedType: Class[A])(implicit context: JSONJAXBContext): A = {
    val stream = new ByteArrayInputStream(bytes)
    val reader = new InputStreamReader(stream, charset)
    val result = context.createJSONUnmarshaller().unmarshalFromJSON(reader, expectedType)
    expectedType.cast(result)
  }
}

trait JaxbWriteables {
  implicit def writeableOf_JaxbXml(implicit codec: Codec, context: JAXBContext): Writeable[JaxbXml] = {
    Writeable[JaxbXml](xml => JaxbSupport.toXmlBytes(xml.obj))
  }

  implicit def writeableOf_JaxbJson(implicit codec: Codec, context: JSONJAXBContext): Writeable[JaxbJson] = {
    Writeable[JaxbJson](json => JaxbSupport.toJsonBytes(json.obj))
  }

  implicit def writeableOf_JaxbNeg(implicit codec: Codec, context: JSONJAXBContext, header: RequestHeader): Writeable[Jaxb] = {
    Writeable[Jaxb] {
      jaxb => header.headers.get(HeaderNames.ACCEPT) match {
        case Some("application/json") => JaxbSupport.toJsonBytes(jaxb.obj)
        case _                        => JaxbSupport.toXmlBytes(jaxb.obj)
      }
    }
  }
}

trait JaxbContentTypeOfs {
  implicit def contentTypeOf_JaxbXml(implicit codec: Codec): ContentTypeOf[JaxbXml] = {
    ContentTypeOf[JaxbXml](Some(ContentTypes.XML))
  }

  implicit def contentTypeOf_JaxbJson(implicit codec: Codec): ContentTypeOf[JaxbJson] = {
    ContentTypeOf[JaxbJson](Some(ContentTypes.JSON))
  }

  implicit def contentTypeOf_Jaxb(implicit codec: Codec, header: RequestHeader): ContentTypeOf[Jaxb] = ContentTypeOf[Jaxb] {
    header.headers.get(HeaderNames.ACCEPT) match {
      case Some("application/json") => Some(ContentTypes.JSON)
      case _                        => Some(ContentTypes.XML)
    }
  }
}

trait JaxbBodyParsers { self: JaxbWriteables with JaxbContentTypeOfs =>
  object jaxb {
    object parse {

      //
      // TODO: refactor to avoid redundancies
      //

      def xml[A](implicit context: JSONJAXBContext, manifest: Manifest[A]): BodyParser[A] =
        xml(BodyParsers.parse.DEFAULT_MAX_TEXT_LENGTH)(context, manifest)

      def json[A](implicit context: JSONJAXBContext, manifest: Manifest[A]): BodyParser[A] =
        json(BodyParsers.parse.DEFAULT_MAX_TEXT_LENGTH)(context, manifest)

      def apply[A](implicit context: JSONJAXBContext, manifest: Manifest[A]): BodyParser[A] =
        apply(BodyParsers.parse.DEFAULT_MAX_TEXT_LENGTH)(context, manifest)

      def tolerantXml[A](implicit context: JAXBContext, manifest: Manifest[A]): BodyParser[A] =
        tolerantXml(BodyParsers.parse.DEFAULT_MAX_TEXT_LENGTH)(context, manifest)

      def tolerantJson[A](implicit context: JSONJAXBContext, manifest: Manifest[A]): BodyParser[A] =
        tolerantJson(BodyParsers.parse.DEFAULT_MAX_TEXT_LENGTH)(context, manifest)

      def xml[A](maxLength: Int)(implicit context: JAXBContext, manifest: Manifest[A]): BodyParser[A] =
        BodyParsers.parse.when(
          _.contentType.exists(m => m == "text/xml" || m == "application/xml"),
          tolerantXml(maxLength)(context, manifest),
          request => Results.BadRequest(JaxbXml(SysError("expecting text/xml or application/xml body"))
          )
        )

      def json[A](maxLength: Int)(implicit context: JSONJAXBContext, manifest: Manifest[A]): BodyParser[A] =
        BodyParsers.parse.when(
          _.contentType.exists(m => m == "application/json"),
          tolerantJson(maxLength)(context, manifest),
          request => Results.BadRequest(JaxbJson(SysError("expecting text/json or application/json body"))
          )
        )

      def apply[A](maxLength: Int)(implicit context: JSONJAXBContext, manifest: Manifest[A]): BodyParser[A] =
        BodyParser("routing, xml/json") { request =>
          if (request.contentType.exists(m => m == "text/xml" || m == "application/xml"))
            xml(maxLength)(context, manifest)(request)
          else if (request.contentType.exists(m => m == "application/json"))
            json(maxLength)(context, manifest)(request)
          else {
            val msg = "expecting text/xml, application/xml or application/json body"
            Done(Left(Results.BadRequest(JaxbXml(SysError(msg)))), Input.Empty)
          }
        }

      def tolerantXml[A](maxLength: Int)(implicit context: JAXBContext, manifest: Manifest[A]): BodyParser[A] =
        BodyParser("jaxb.xml, maxLength=" + maxLength) { request =>
          Traversable.takeUpTo[Array[Byte]](maxLength).apply(Iteratee.consume[Array[Byte]]().map { bytes =>
            scala.util.control.Exception.allCatch[A].either {
              JaxbSupport.fromXmlBytes(bytes, request.charset.getOrElse("utf-8"), manifest.erasure)
            }.left.map { e =>
              (Results.BadRequest(JaxbXml(SysError("cannot unmarshal xml input to %s" format manifest.erasure))), bytes)
            }
          }).flatMap(Iteratee.eofOrElse(Results.EntityTooLarge))
            .flatMap {
            case Left(b) => Done(Left(b), Input.Empty)
            case Right(it) => it.flatMap {
              case Left((r, in)) => Done(Left(r), Input.El(in))
              case Right(xml: A) => Done(Right(xml), Input.Empty)
            }
          }
        }

      def tolerantJson[A](maxLength: Int)(implicit context: JSONJAXBContext, manifest: Manifest[A]): BodyParser[A] =
        BodyParser("jaxb.json, maxLength=" + maxLength) { request =>
          Traversable.takeUpTo[Array[Byte]](maxLength).apply(Iteratee.consume[Array[Byte]]().map { bytes =>
            scala.util.control.Exception.allCatch[A].either {
              JaxbSupport.fromJsonBytes(bytes, request.charset.getOrElse("utf-8"), manifest.erasure)
            }.left.map { e =>
              (Results.BadRequest(JaxbJson(SysError("cannot unmarshal json input to %s" format manifest.erasure))), bytes)
            }
          }).flatMap(Iteratee.eofOrElse(Results.EntityTooLarge))
            .flatMap {
            case Left(b) => Done(Left(b), Input.Empty)
            case Right(it) => it.flatMap {
              case Left((r, in)) => Done(Left(r), Input.El(in))
              case Right(json: A) => Done(Right(json), Input.Empty)
            }
          }
        }

    }
  }
}

