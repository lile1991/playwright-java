/*
 * Copyright (c) Microsoft Corporation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.microsoft.playwright;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static com.microsoft.playwright.Page.EventType.*;
import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.*;

public class TestPageEventNetwork extends TestBase {
  @Test
  void PageEventsRequest() {
    List<Request> requests = new ArrayList<>();
    page.addListener(REQUEST, event -> requests.add((Request) event.data()));
    page.navigate(server.EMPTY_PAGE);
    assertEquals(1, requests.size());
    assertEquals(server.EMPTY_PAGE, requests.get(0).url());
    assertEquals("document", requests.get(0).resourceType());
    assertEquals("GET", requests.get(0).method());
    assertNotNull(requests.get(0).response());
    assertEquals(page.mainFrame(), requests.get(0).frame());
    assertEquals(server.EMPTY_PAGE, requests.get(0).frame().url());
  }

  @Test
  void PageEventsResponse() {
    List<Response> responses = new ArrayList<>();
    page.addListener(RESPONSE, event -> responses.add((Response) event.data()));
    page.navigate(server.EMPTY_PAGE);
    assertEquals(1, responses.size());
    assertEquals(server.EMPTY_PAGE, responses.get(0).url());
    assertEquals(200, responses.get(0).status());
    assertTrue(responses.get(0).ok());
    assertNotNull(responses.get(0).request());
  }

  @Test
  void PageEventsRequestFailed() {
    server.setRoute("/one-style.css", exchange -> exchange.getResponseBody().close());
    List<Request> failedRequests = new ArrayList<>();
    page.addListener(REQUESTFAILED, event -> failedRequests.add((Request) event.data()));
    page.navigate(server.PREFIX + "/one-style.html");
    assertEquals(1, failedRequests.size());
    assertTrue(failedRequests.get(0).url().contains("one-style.css"));
    assertNull(failedRequests.get(0).response());
    assertEquals("stylesheet", failedRequests.get(0).resourceType());
    if (isChromium()) {
      assertEquals("net::ERR_EMPTY_RESPONSE", failedRequests.get(0).failure().errorText());
    } else if (isWebKit()) {
      if (isMac)
        assertEquals("The network connection was lost.", failedRequests.get(0).failure().errorText());
      else if (isWindows)
        assertEquals("Server returned nothing (no headers, no data)", failedRequests.get(0).failure().errorText());
      else
        assertEquals("Message Corrupt", failedRequests.get(0).failure().errorText());
    } else {
      assertEquals("NS_ERROR_NET_RESET", failedRequests.get(0).failure().errorText());
    }
    assertNotNull(failedRequests.get(0).frame());
  }

  @Test
  void PageEventsRequestFinished() {
    Deferred<Event<Page.EventType>> event = page.futureEvent(REQUESTFINISHED);
    Response response = page.navigate(server.EMPTY_PAGE);
    event.get();
    Request request = response.request();
    assertEquals(server.EMPTY_PAGE, request.url());
    assertNotNull(request.response());
    assertEquals(page.mainFrame(), request.frame());
    assertEquals(server.EMPTY_PAGE, request.frame().url());
    assertNull(request.failure());
  }

  @Test
  void shouldFireEventsInProperOrder() {
    List<String> events = new ArrayList<>();
    page.addListener(REQUEST, event -> events.add("request"));
    page.addListener(RESPONSE, event -> events.add("response"));
    Response response = page.navigate(server.EMPTY_PAGE);
    assertNull(response.finished());
    events.add("requestfinished");
    assertEquals(asList("request", "response", "requestfinished"), events);
  }

  @Test
  void shouldSupportRedirects() {
    List<String> events = new ArrayList<>();
    page.addListener(REQUEST, event -> {
      Request request = (Request) event.data();
      events.add(request.method() + " " + request.url());
    });
    page.addListener(RESPONSE,event -> {
      Response response = (Response) event.data();
      events.add(response.status() + " " + response.url());
    });
    page.addListener(REQUESTFINISHED, event -> {
      Request request = (Request) event.data();
      events.add("DONE " + request.url());
    });
    page.addListener(REQUESTFAILED, event -> {
      Request request = (Request) event.data();
      events.add("FAIL " + request.url());
    });
    server.setRedirect("/foo.html", "/empty.html");
    String FOO_URL = server.PREFIX + "/foo.html";
    Response response = page.navigate(FOO_URL);
    response.finished();
    assertEquals(asList(
      "GET " + FOO_URL,
      "302 " + FOO_URL,
      "DONE " + FOO_URL,
      "GET " + server.EMPTY_PAGE,
      "200 " + server.EMPTY_PAGE,
      "DONE " + server.EMPTY_PAGE), events);
    Request redirectedFrom = response.request().redirectedFrom();
    assertTrue(redirectedFrom.url().contains("/foo.html"));
    assertNull(redirectedFrom.redirectedFrom());
    assertEquals(response.request(), redirectedFrom.redirectedTo());
  }
}
