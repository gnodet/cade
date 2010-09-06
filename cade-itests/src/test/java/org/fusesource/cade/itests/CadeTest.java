/**
 * Copyright (C) 2010 the original author or authors.
 * See the notice.md file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fusesource.cade.itests;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Properties;

import org.fusesource.cade.itests.model.Activator;
import org.fusesource.cade.itests.model.Config1;
import org.fusesource.cade.itests.model.Config2;
import org.fusesource.cade.itests.model.Service1;
import org.fusesource.cade.itests.model.Service2;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.ops4j.pax.exam.Customizer;
import org.ops4j.pax.exam.Option;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;
import org.ops4j.pax.exam.options.UrlProvisionOption;
import org.ops4j.pax.swissbox.tinybundles.core.TinyBundle;
import org.osgi.service.cm.ConfigurationAdmin;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.ops4j.pax.exam.CoreOptions.felix;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.logProfile;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.modifyBundle;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.newBundle;
import static org.ops4j.pax.swissbox.tinybundles.core.TinyBundles.withBnd;
import static org.osgi.framework.Constants.BUNDLE_ACTIVATOR;
import static org.osgi.framework.Constants.BUNDLE_SYMBOLICNAME;
import static org.osgi.framework.Constants.EXPORT_PACKAGE;
import static org.osgi.framework.Constants.IMPORT_PACKAGE;

@RunWith(JUnit4TestRunner.class)
public class CadeTest extends AbstractIntegrationTest {

    private static final String PID_CONFIG1 = "org.fusesource.cade.itests.model.Config1";
    private static final String PID_CONFIG2 = "org.fusesource.cade.itests.model.CustomPID";

    @Test
    public void testSvc1() throws Exception {
        ConfigurationAdmin ca = getOsgiService(ConfigurationAdmin.class);
        Service1 svc1 = getOsgiService(Service1.class);

        ca.getConfiguration(PID_CONFIG1).delete();
        Thread.sleep(500);
        assertNull(svc1.getConfig());

        synchronized (svc1) {
            ca.getConfiguration(PID_CONFIG1).update(props("host", "localhost"));
            System.err.println("Waiting for Service1");
            svc1.wait(10000);
            assertNotNull(svc1.getConfig());
            assertEquals("localhost", svc1.getConfig().host());
            assertEquals(80, svc1.getConfig().port());

            ca.getConfiguration(PID_CONFIG1).update(props("host", "localhost", "port", "8080"));
            System.err.println("Waiting for Service1");
            svc1.wait(10000);
            assertNotNull(svc1.getConfig());
            assertEquals("localhost", svc1.getConfig().host());
            assertEquals(8080, svc1.getConfig().port());

            // TODO: mandatory is not handled yet
//            ca.getConfiguration("org.fusesource.cade.itests.model.Config1")
//                    .update(props("port", "8080"));
//            svc1.wait(10000);
//            assertNull(svc1.getConfig());

            ca.getConfiguration(PID_CONFIG1).delete();
            System.err.println("Waiting for Service1");
            svc1.wait(10000);
            assertNull(svc1.getConfig());
        }
    }

    @Test
    public void testSvc2() throws Exception {
        ConfigurationAdmin ca = getOsgiService(ConfigurationAdmin.class);
        Service2 svc2 = getOsgiService(Service2.class);

        ca.getConfiguration(PID_CONFIG1).delete();
        ca.getConfiguration(PID_CONFIG2).delete();
        Thread.sleep(500);
        assertNull(svc2.getConfig());

        synchronized (svc2) {
            ca.getConfiguration(PID_CONFIG1).update(props("host", "localhost"));
            System.err.println("Waiting for Service2 / Config1");
            svc2.wait(10000);
            ca.getConfiguration(PID_CONFIG2).update(props("tokens", "foo, bar"));
            System.err.println("Waiting for Service2 / Config2");
            svc2.wait(10000);
            assertNotNull(svc2.getConfig());
            assertEquals("localhost", svc2.getConfig().host());
            assertEquals(80, svc2.getConfig().port());
            assertEquals(Arrays.asList(new String[] { "foo", "bar" }), svc2.getConfig().tokens());
        }
    }

    public static Properties props(String... values) {
        Properties props = new Properties();
        for (int i = 0; i < values.length;) {
            props.put(values[i++], values[i++]);
        }
        return props;
    }

    @Configuration
    public static Option[] configuration() throws Exception {
        return new Option[] {
                logProfile(),
                mavenBundle("org.fusesource.cade", "cade-bundle"),
                felix(),
                tiny( newBundle()
                        .add( Activator.class )
                        .add( Config1.class )
                        .add( Service1.class )
                        .add( Config2.class )
                        .add( Service2.class )
                        .set( BUNDLE_SYMBOLICNAME, "Test-Bundle-Model" )
                        .set( EXPORT_PACKAGE, "org.fusesource.cade.itests.model" )
                        .set( IMPORT_PACKAGE, "*" )
                        .set( BUNDLE_ACTIVATOR, Activator.class.getName() )
                        .build( withBnd() ) ),
                new Customizer() {
                    @Override
                    public InputStream customizeTestProbe(InputStream testProbe) throws Exception {
                        TinyBundle b = modifyBundle( testProbe );
                        return b.removeResource( mapClassToEntry( Activator.class.getName() ) )
                                .removeResource( mapClassToEntry( Config1.class.getName() ) )
                                .removeResource( mapClassToEntry( Service1.class.getName() ) )
                                .removeResource( mapClassToEntry( Config2.class.getName() ) )
                                .removeResource( mapClassToEntry( Service2.class.getName() ) )
                                .build( withBnd() );
                    }
                }
        };
    }

    public static  String mapClassToEntry( String clazzname ) {
        return clazzname.replace( '.', '/' ) + ".class";
    }

    public static UrlProvisionOption tiny( InputStream is ) throws Exception {
        File output = File.createTempFile("tiny-bundle-", ".jar");
        output.deleteOnExit();
        FileOutputStream fos = new FileOutputStream(output);
        copy(is, fos);
        return new UrlProvisionOption(output.toURI().toURL().toExternalForm());
    }

    public static int copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[4096];
        int count = 0;
        int n = 0;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
            count += n;
        }
        input.close();
        output.close();
        return count;
    }
}
