/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.http.server.tck.tests.cors;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpHeaders;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Delete;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.PathVariable;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.Produces;
import io.micronaut.http.server.cors.CrossOrigin;
import io.micronaut.http.server.tck.AssertionUtils;
import io.micronaut.http.server.tck.HttpResponseAssertion;
import io.micronaut.http.server.util.HttpHostResolver;
import io.micronaut.http.uri.UriBuilder;
import jakarta.inject.Singleton;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;

import static io.micronaut.http.server.tck.TestScenario.asserts;
import static org.junit.jupiter.api.Assertions.assertAll;


@SuppressWarnings({
    "java:S2259", // The tests will show if it's null
    "java:S5960", // We're allowed assertions, as these are used in tests only
    "checkstyle:MissingJavadocType",
})
public class CrossOriginTest {

    private static final String SPECNAME = "CrossOriginTest";

    @Test
    void corsSimpleRequestNotAllowedForLocalhostAndAny() throws IOException {
        asserts(SPECNAME,
            HttpRequest.GET(UriBuilder.of("/foo").path("bar").build()).header(HttpHeaders.ORIGIN, "https://foo.com"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("bar")
                .build()));
        asserts(SPECNAME,
            HttpRequest.GET(UriBuilder.of("/foo").path("bar").build()).header(HttpHeaders.ORIGIN, "https://bar.com"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .build()));

    }

    @Test
    void corsSimpleRequestMethods() {
        assertAll(
            () -> asserts(SPECNAME,
                HttpRequest.GET(UriBuilder.of("/methods").path("getit").build()).header(HttpHeaders.ORIGIN, "http://www.google.com"),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .build())),
            () -> asserts(SPECNAME,
                HttpRequest.POST(UriBuilder.of("/methods").path("postit/id").build(),"post").header(HttpHeaders.ORIGIN, "https://www.google.com"),
                (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.OK)
                    .build())),
            () -> asserts(SPECNAME,
                HttpRequest.DELETE(UriBuilder.of("/methods").path("deleteit/id").build(),"delete").header(HttpHeaders.ORIGIN, "https://www.google.com"),
                (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                    .status(HttpStatus.FORBIDDEN)
                    .build()))
        );
    }

    @Test
    void corsSimpleRequestHeaders() throws IOException {
        asserts(SPECNAME,
            Collections.singletonMap("micronaut.server.cors.enabled", true),
            HttpRequest.GET(UriBuilder.of("/allowedheaders").path("bar").build())
                .header(HttpHeaders.ORIGIN, "https://foo.com")
                .header(HttpHeaders.AUTHORIZATION, "foo")
                .header(HttpHeaders.CONTENT_TYPE, "bar"),
            (server, request) -> AssertionUtils.assertDoesNotThrow(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.OK)
                .body("bar")
                .build()));
        asserts(SPECNAME,
            Collections.singletonMap("micronaut.server.cors.enabled", true),
            HttpRequest.GET(UriBuilder.of("/allowedheaders").path("bar").build())
                .header(HttpHeaders.ORIGIN, "https://foo.com")
                .header("foo", "bar"),
            (server, request) -> AssertionUtils.assertThrows(server, request, HttpResponseAssertion.builder()
                .status(HttpStatus.FORBIDDEN)
                .body("bar")
                .build()));
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/foo")
    static class Foo {
        @CrossOrigin("https://foo.com")
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/bar")
        String index() {
            return "bar";
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/methods")
    @CrossOrigin(
        allowedOrigins = "^http(|s):\\/\\/www\\.google\\.com$",
        allowedMethods = { HttpMethod.GET, HttpMethod.POST }
    )
    static class AllowedMethods {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/getit")
        String canGet() {
            return "get";
        }

        @Produces(MediaType.TEXT_PLAIN)
        @Post("/postit/{id}")
        String canPost(@PathVariable String id) {
            return id;
        }

        @Delete("/deleteit/{id}")
        String cantDelete(@PathVariable String id) {
            return id;
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/allowedheaders")
    @CrossOrigin(
        value = "https://foo.com",
        allowedHeaders = { HttpHeaders.CONTENT_TYPE, HttpHeaders.AUTHORIZATION }
    )
    static class AllowedHeaders {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/bar")
        String index() {
            return "bar";
        }
    }
    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/exposedheaders")
    @CrossOrigin(
        value = "https://foo.com",
        exposedHeaders = { HttpHeaders.CONTENT_TYPE, HttpHeaders.AUTHORIZATION }
    )
    static class ExposedHeaders {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/bar")
        String index() {
            return "bar";
        }
    }

    // TODO: tests for CrossOrigin.allowCredentials, CrossOrigin.maxAge
    @Requires(property = "spec.name", value = SPECNAME)
    @Controller("/credentials")
    @CrossOrigin(
        value = "https://foo.com",
        allowCredentials = "false"
    )
    static class Credentials {
        @Produces(MediaType.TEXT_PLAIN)
        @Get("/bar")
        String index() {
            return "bar";
        }
    }

    @Requires(property = "spec.name", value = SPECNAME)
    @Replaces(HttpHostResolver.class)
    @Singleton
    static class HttpHostResolverReplacement implements HttpHostResolver {
        @Override
        public String resolve(@Nullable HttpRequest request) {
            return "https://micronautexample.com";
        }
    }
}