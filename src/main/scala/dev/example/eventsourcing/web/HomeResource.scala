package dev.example.eventsourcing.web

import javax.ws.rs._
import javax.ws.rs.core.MediaType._

import com.sun.jersey.api.view.Viewable

import org.springframework.stereotype.Component

@Component
@Path("/")
class HomeResource {
  @GET
  @Produces(Array(TEXT_HTML))
  def home = new Viewable(homePath("Home"))
}