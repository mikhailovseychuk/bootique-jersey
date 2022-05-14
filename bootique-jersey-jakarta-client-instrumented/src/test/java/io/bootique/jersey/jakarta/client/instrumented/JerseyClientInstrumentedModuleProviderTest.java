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

package io.bootique.jersey.jakarta.client.instrumented;

import io.bootique.BQRuntime;
import io.bootique.jersey.jakarta.client.JerseyClientModule;
import io.bootique.jersey.jakarta.client.instrumented.JerseyClientInstrumentedModule;
import io.bootique.jersey.jakarta.client.instrumented.JerseyClientInstrumentedModuleProvider;
import io.bootique.junit5.*;
import io.bootique.metrics.MetricsModule;
import io.bootique.metrics.health.HealthCheckModule;
import org.junit.jupiter.api.Test;

@BQTest
public class JerseyClientInstrumentedModuleProviderTest {

    @BQTestTool
    final BQTestFactory testFactory = new BQTestFactory();

    @Test
    public void testAutoLoadable() {
        BQModuleProviderChecker.testAutoLoadable(JerseyClientInstrumentedModuleProvider.class);
    }

    @Test
    public void testModuleDeclaresDependencies() {
        final BQRuntime bqRuntime = testFactory.app().moduleProvider(new JerseyClientInstrumentedModuleProvider()).createRuntime();
        BQRuntimeChecker.testModulesLoaded(bqRuntime,
                JerseyClientModule.class,
                JerseyClientInstrumentedModule.class,
                MetricsModule.class,
                HealthCheckModule.class
        );
    }
}