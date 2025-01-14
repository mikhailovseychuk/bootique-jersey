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

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.common.Slf4jNotifier;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.extension.Extension;
import com.github.tomakehurst.wiremock.stubbing.StubMapping;
import io.bootique.BQCoreModule;
import io.bootique.di.BQModule;
import io.bootique.junit5.BQTestScope;
import io.bootique.junit5.scope.BQAfterScopeCallback;
import io.bootique.junit5.scope.BQBeforeScopeCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.function.UnaryOperator;

/**
 * A Bootique test tool that sets up and manages a WireMock "server". Each tester should be annotated with
 * {@link io.bootique.junit5.BQTestTool}. The tester supports manually configured request "stubs" as well as proxying
 * a real backend and caching that backend's responses as local "snapshot" files.
 *
 * @since 3.0
 */
public class WireMockTester implements BQBeforeScopeCallback, BQAfterScopeCallback {

    private static final Logger LOGGER = LoggerFactory.getLogger(WireMockTester.class);

    private final List<StubMapping> stubs;
    private final List<Extension> extensions;
    private boolean verbose;
    private String originUrl;
    private WireMockTesterProxy proxy;
    private String filesRoot;
    private UnaryOperator<WireMockConfiguration> configCustomizer;

    protected volatile WireMockServer server;

    public static WireMockTester create() {
        return new WireMockTester();
    }

    protected WireMockTester() {
        this.stubs = new ArrayList<>();
        this.extensions = new ArrayList<>();
    }

    public WireMockTester stub(MappingBuilder mappingBuilder) {
        this.stubs.add(mappingBuilder.build());
        return this;
    }

    /**
     * Adds a WireMock extension to the tester allowing the user to customize responses, react to WireMock events, etc.
     */
    public WireMockTester extension(Extension extension) {
        this.extensions.add(extension);
        return this;
    }

    /**
     * A builder method that adds a special stub with minimal priority that will proxy all requests to the specified
     * real backend service (aka "origin"). If "takeLocalSnapshots" is true, after each proxy call a response snapshot
     * will be captured and stored locally. Snapshots location is the folder configured per {@link #filesRoot(String)}.
     * The effect of the capturing snapshots is that all subsequent calls to this URL will only work with local data
     * and will not attempt to access remote URLs.
     * <p>Limitation: due to a bug in <a href="https://github.com/wiremock/wiremock/issues/655">WireMock proxy
     * implementation</a>, some "root" requests to WireMock proxies may fail with 404. E.g. consider an origin URL of
     * "http://example.org/p1/p2?q=a". If the origin is mapped as "http://example.org/p1/p2", for "GET /?q=a", WireMock
     * will generate a URL of "http://example.org/p1/p2/?q=a", which may fail due to the trailing slash. To work around
     * this limitation, map WireMock to a URL without the last path component, e.g. "http://example.org/p1", and add the
     * path to the request as "GET /p2?q=a"</p>
     */
    // TODO: see the limitation above ... devise a WireMock or Bootique fix for it
    public WireMockTester proxy(String originUrl, boolean takeLocalSnapshots) {
        this.originUrl = originUrl;
        this.proxy = new WireMockTesterProxy(originUrl);
        return takeLocalSnapshots ? extension(proxy.createSnapshotRecorder()) : this;
    }

    /**
     * A builder method that establishes a local directory that will be used as a root for local snapshots of WireMock
     * responses. Either those recorded by the proxy feature (see {@link #proxy(String, boolean)}), or created manually.
     * If not set, the default location of "src/main/resources" is used.
     */
    public WireMockTester filesRoot(String path) {
        this.filesRoot = path;
        return this;
    }

    /**
     * A builder method that establishes a local directory that will be used as a root for WireMock recording files.
     * If not set, a default location will be picked automatically.
     */
    public WireMockTester filesRoot(File dir) {
        return filesRoot(dir.getAbsolutePath());
    }

    /**
     * A builder method to set a function that customizes tester-provided configuration
     */
    public WireMockTester configCustomizer(UnaryOperator<WireMockConfiguration> configCustomizer) {
        this.configCustomizer = configCustomizer;
        return this;
    }

    /**
     * A builder method that enables verbose logging for the tester.
     */
    public WireMockTester verbose() {
        this.verbose = true;
        return this;
    }

    /**
     * A builder method that enables proper handling of redirects within the same origin when taking snapshots. It's a
     * workaround for a WireMock proxy limitation. With the current version of WireMock the client will be redirected
     * to the url from the original "Location" header, instead of the proxy URL. This method causes rewriting "Location"
     * header with a URL that points to the proxy, but keeps the original "Location" header in snapshot files.
     *
     * <p> <strong>Wiremock default behaviour example:</strong>
     * <p>WireMockTester.create().proxy("http://example.org", true);
     * <p>1st request went to wiremock proxy, as expected:
     * <p>GET http://{wiremock_host}:{wiremock_port}/path1/?q=a --&gt; 307 "headers" : {"Location": "http://example.org/path2/?q=a"}
     * <p>2nd request was executed based on url from "Location" header, bypassing proxy:
     * <p>GET http://example.org/path2/?q=a --&gt; 404
     *
     * <p> <strong>Rewrite redirect example:</strong>
     * <p>WireMockTester.create().proxy("http://example.org", true).rewriteRedirectLocation();
     * <p>1st request went to wiremock proxy:
     * <p>GET http://{wiremock_host}:{wiremock_port}/path1/?q=a --&gt; 307 "headers" : {"Location": "http://example.org/path2/?q=a"}
     * <p>2nd request went to proxy as well, because "Location" header value was rewritten
     * <p>GET http://{wiremock_host}:{wiremock_port}/path2/?q=a --&gt; ... (matches proxy)
     */
    public WireMockTester rewriteRedirectLocation() {
        if (originUrl == null) {
            return this;
        }

        var rewriter = new WireMockRedirectRewriter(originUrl);
        return extension(rewriter.injector()).extension(rewriter.replacer());
    }

    @Override
    public void beforeScope(BQTestScope scope, ExtensionContext context) {
        ensureRunning();
    }

    @Override
    public void afterScope(BQTestScope scope, ExtensionContext context) {
        if (server != null) {
            server.shutdown();
        }
    }

    /**
     * Returns a Bootique module that forces a named Jersey client "target" in a test {@link io.bootique.BQRuntime}
     * to read data from WireMock instead of a real URL. Multiple test BQRuntimes can be initialized from a single
     * WireMockTester instance if needed.
     *
     * @param targetName the name of the mapped target in {@link io.bootique.jersey.client.HttpTargets} that should be
     *                   routed to WireMock
     */
    public BQModule moduleWithTestTarget(String targetName) {
        String propName = "bq.jerseyclient.targets." + targetName + ".url";
        return b -> BQCoreModule.extend(b).setProperty(propName, getUrl());
    }

    public Integer getPort() {
        return ensureRunning().port();
    }

    /**
     * Returns the URL of the internal WireMock server.
     */
    public String getUrl() {
        return ensureRunning().baseUrl();
    }

    protected WireMockServer ensureRunning() {
        if (server == null) {
            synchronized (this) {
                if (server == null) {
                    WireMockServer server = createServer(createServerConfig(), createStubs());
                    startServer(server);
                    this.server = server;
                }
            }
        }

        return server;
    }

    protected WireMockConfiguration createServerConfig() {

        WireMockConfiguration config = WireMockConfiguration
                .wireMockConfig()
                .dynamicPort()
                .notifier(new Slf4jNotifier(verbose));

        if (filesRoot != null) {
            config.usingFilesUnderDirectory(filesRoot);
        }

        if (!extensions.isEmpty()) {
            config.extensions(extensions.toArray(new Extension[0]));
        }

        return configCustomizer != null ? configCustomizer.apply(config) : config;
    }

    protected List<StubMapping> createStubs() {

        if (proxy == null) {
            return this.stubs;
        }

        List<StubMapping> allStubs = new ArrayList<>(stubs.size() + 1);
        allStubs.addAll(this.stubs);

        // A proxy stub should be added only after all user-provided stubs are known to the Tester,
        // as the proxy stub must have the lowest priority to act as a "catch-all" rule
        allStubs.add(proxy.createStub(stubs));

        return allStubs;
    }

    protected WireMockServer createServer(WireMockConfiguration config, List<StubMapping> stubs) {
        WireMockServer server = new WireMockServer(config);
        stubs.forEach(server::addStubMapping);
        return server;
    }

    protected void startServer(WireMockServer server) {
        server.start();
        LOGGER.info("WireMock started on port {}", server.port());
    }
}
