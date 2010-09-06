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

public class Service1 implements Configurable<Config1> {

    private Config1 config;

    public synchronized Config1 getConfig() {
        return config;
    }

    public synchronized void setup(Config1 config) {
        System.err.println("Setup Config1");
        System.err.println(config.host());
        System.err.println(config.port());
        this.config = config;
        notifyAll();
    }

    public synchronized void deleted() {
        System.err.println("Deleted Config1");
        this.config = null;
        notifyAll();
    }
}
