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
package org.fusesource.cade.itests.model;

import org.fusesource.cade.Configurable;

public class Service2 implements Configurable<Config2> {

    private Config2 config;

    public synchronized Config2 getConfig() {
        return config;
    }

    public synchronized void setup(Config2 config) {
        System.err.println("Setup Config2");
        System.err.println(config.host());
        System.err.println(config.port());
        System.err.println(config.tokens());
        this.config = config;
        notifyAll();
    }

    public synchronized void deleted() {
        System.err.println("Deleted Config2");
        this.config = null;
        notifyAll();
    }
}
