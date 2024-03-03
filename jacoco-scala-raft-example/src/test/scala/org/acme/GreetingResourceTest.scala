package org.acme

import io.quarkus.test.junit.QuarkusTest
import io.restassured.RestAssured.given
import org.hamcrest.CoreMatchers.`is`
import org.junit.jupiter.api.Test

@QuarkusTest
class GreetingResourceTest {

    @Test
    def testHelloEndpoint(): Unit = {
        given()
          .`when`().get("/hello")
          .then()
             .statusCode(200)
             .body(`is`("Hello from RESTEasy Reactive"))
    }

    @Test
    def testGreetingEndpoint(): Unit = {
        given()
          .`when`().get("/hello/greeting/marcus")
          .then()
             .statusCode(200)
             .body(`is`("hello marcus"))
    }

    @Test
    def testCompareEndpoint(): Unit = {
        given()
          .`when`().get("/hello/compare/greeting")
          .then()
             .statusCode(200)
             .body(`is`("let's compare to greeting, ok?"))
    }
}