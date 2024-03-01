package org.acme

import jakarta.inject.Inject
import jakarta.ws.rs.{GET, Path, Produces}
import jakarta.ws.rs.core.MediaType

@Path("/hello")
class GreetingResource {

    @GET
    @Produces(Array[String](MediaType.TEXT_PLAIN))
    def hello() = "Hello from RESTEasy Reactive"

    val service = GreetingService()

    @GET
    @Produces(Array[String](MediaType.TEXT_PLAIN))
    @Path("/greeting/{name}")
    def greeting(name: String) = {
        service.greeting(name);
    }

}