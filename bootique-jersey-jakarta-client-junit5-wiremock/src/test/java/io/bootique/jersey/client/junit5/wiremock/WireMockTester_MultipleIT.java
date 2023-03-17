/*
 * Licensed to ObjectStyle LLC under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ObjectStyle LLC licenses
 * this file to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package io.bootique.jersey.client.junit5.wiremock;

import io.bootique.BQRuntime;
import io.bootique.Bootique;
import io.bootique.jersey.client.HttpTargets;
import io.bootique.jetty.junit5.JettyTester;
import io.bootique.junit5.BQApp;
import io.bootique.junit5.BQTest;
import io.bootique.junit5.BQTestTool;
import jakarta.ws.rs.client.WebTarget;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@BQTest
public class WireMockTester_MultipleIT {

    @BQTestTool
    static final WireMockTester bqOnGithub = WireMockTester.create("https://github.com/bootique");

    @BQTestTool
    static final WireMockTester google = WireMockTester.create("https://www.google.com");

    @BQApp(skipRun = true)
    static final BQRuntime app = Bootique.app()
            .autoLoadModules()
            .module(bqOnGithub.moduleWithTestTarget("bqOnGitHub"))
            .module(google.moduleWithTestTarget("google"))
            .createRuntime();

    @Test
    public void testTwoTargets() {
        WebTarget google = app.getInstance(HttpTargets.class).newTarget("google");
        Response r0 = google.request().get();
        JettyTester.assertOk(r0)
                .assertContentType(MediaType.TEXT_HTML_TYPE)
                .assertContent(c -> assertTrue(c.contains("<title>Google</title>")));

        WebTarget bqOnGitHub = app.getInstance(HttpTargets.class).newTarget("bqOnGitHub");
        Response r1 = bqOnGitHub.request().get();
        JettyTester.assertOk(r1)
                .assertContentType(MediaType.TEXT_HTML_TYPE)
                .assertContent(c -> assertTrue(c.contains("<title>Bootique Project")));
    }
}
