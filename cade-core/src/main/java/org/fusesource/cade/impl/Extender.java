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
package org.fusesource.cade.impl;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Dictionary;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.fusesource.cade.Configurable;
import org.fusesource.cade.Meta;
import org.fusesource.cade.impl.converter.DefaultConverter;
import org.fusesource.cade.impl.converter.GenericType;
import org.osgi.framework.BundleContext;
import org.osgi.framework.ServiceReference;
import org.osgi.framework.ServiceRegistration;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.ConfigurationEvent;
import org.osgi.service.cm.ConfigurationListener;
import org.osgi.util.tracker.ServiceTracker;

/**
 * The extender is responsible for tracking and calling Configurable objects based
 * on the configurations provided by the ConfigurationAdmin services.
 */
public class Extender implements ConfigurationListener {

    private final BundleContext context;
    private final ServiceRegistration registration;
    private final ServiceTracker configurableTracker;
    private final ServiceTracker configAdminTracker;
    private final ConcurrentMap<String, List<Configurable<?>>> configurables;

    public Extender(BundleContext ctx) {
        context = ctx;
        configurables = new ConcurrentHashMap<String, List<Configurable<?>>>();
        configAdminTracker = new ConfigAdminTracker(context);
        configAdminTracker.open();
        configurableTracker = new ConfigurableTracker(context);
        configurableTracker.open();
        registration = context.registerService(ConfigurationListener.class.getName(), this, null);
    }

    /**
     * Destroy this object.
     */
    public void dispose() {
        registration.unregister();
        configurableTracker.close();
        configAdminTracker.close();
    }

    public void configurationEvent(ConfigurationEvent event) {
        try {
            if (event.getPid() != null) {
                List<Configurable<?>> cfgs = configurables.get(event.getPid());
                if (cfgs != null) {
                    for (Configurable<?> cfg : cfgs) {
                        updateConfigurable((Configurable<Object>) cfg, false);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void addConfigurable(Configurable<Object> configurable) {
        Class configurationType = getConfigurationType(configurable);
        List<String> pids = getPids(configurationType);
        for (String pid : pids) {
            List<Configurable<?>> cfgs = configurables.get(pid);
            if (cfgs == null) {
                cfgs = new CopyOnWriteArrayList<Configurable<?>>();
                List<Configurable<?>> oldCfgs = configurables.putIfAbsent(pid, cfgs);
                if (oldCfgs != null) {
                    cfgs = oldCfgs;
                }
            }
            cfgs.add(configurable);
        }
        updateConfigurable(configurable, true);
    }

    public void removeConfigurable(Configurable<?> configurable) {
        Class configurationType = getConfigurationType(configurable);
        String pid = configurationType.getName();
        List<Configurable<?>> cfgs = configurables.get(pid);
        if (cfgs != null) {
            cfgs.remove(configurable);
        }
    }

    public void addConfigAdmin(ServiceReference reference, ConfigurationAdmin configAdmin) {
        try {
            Configuration[] cfgs = configAdmin.listConfigurations(null);
            if (cfgs != null) {
                for (Configuration cfg : cfgs) {
                    configurationEvent(new ConfigurationEvent(reference, ConfigurationEvent.CM_UPDATED, cfg.getFactoryPid(), cfg.getPid()));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void removeConfigAdmin(ServiceReference reference, ConfigurationAdmin configAdmin) {
    }

    protected void updateConfigurable(final Configurable<Object> configurable, boolean force) {
        Class<?> configurationType = getConfigurationType(configurable);
        List<String> pids = getPids(configurationType);
        // Check if there is any ConfigAdmin with the needed pid
        Object[] cas = configAdminTracker.getServices();
        final Properties properties = new Properties();
        if (cas != null) {
            for (Object ca : cas) {
                try {
                    StringBuilder filter = new StringBuilder();
                    filter.append("(|");
                    for (String pid : pids) {
                        filter.append("(service.pid=").append(pid).append(")");
                    }
                    filter.append(")");
                    Configuration[] cfgs = ((ConfigurationAdmin) ca).listConfigurations(filter.toString());
                    if (cfgs != null) {
                        for (Configuration cfg : cfgs) {
                            Dictionary<?, ?> d = cfg.getProperties();
                            if (d != null) {
                                for (Enumeration<?> e = d.keys(); e.hasMoreElements();) {
                                    String key = (String) e.nextElement();
                                    if (!properties.containsKey(key)) {
                                        properties.put(key, d.get(key));
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        // check if there is any config to set
        if (properties.isEmpty()) {
            configurable.deleted();
        } else {
            InvocationHandler handler = new InvocationHandler() {
                public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                    String name = method.getName();
                    Meta.Key key = method.getAnnotation(Meta.Key.class);
                    if (key != null) {
                        name = key.value();
                    }
                    Object value = properties.get(name);
                    if (value == null && key == null) {
                        name = name.replace('_', '.');
                        value = properties.get(name);
                    }
                    if (value == null) {
                        Meta.Default def = method.getAnnotation(Meta.Default.class);
                        if (def != null) {
                            value = def.value();
                        }
                    }
                    Meta.Separated sep = method.getAnnotation(Meta.Separated.class);
                    if (value != null && sep != null) {
                        value = value.toString().split(sep.value());
                    }
                    if (value != null) {
                        value = new DefaultConverter(configurable.getClass().getClassLoader())
                                .convert(value, new GenericType(method.getGenericReturnType()));
                    }
                    return value;
                }
            };
            Object proxy = Proxy.newProxyInstance(configurable.getClass().getClassLoader(), new Class[]{configurationType}, handler);
            configurable.setup(proxy);
        }
    }

    /**
     * Get the list of PIDs defining the configuration class.
     *
     * @param clazz the configuration class
     * @return the associated PIDs
     */
    protected List<String> getPids(Class<?> clazz) {
        List<String> pids = new ArrayList<String>();
        getPids(clazz, pids);
        return pids;
    }

    private void getPids(Class<?> clazz, List<String> pids) {
        String pid = getPid(clazz);
        if (pid != null) {
            pids.add(pid);
        }
        for (Class cl : clazz.getInterfaces()) {
            getPids(cl, pids);
        }
    }

    /**
     * Get the configuration PID associated to a given class.
     * If a {@link org.fusesource.cade.Meta.PID} annotation is defined, get the
     * value from it, else return the class name.
     *
     * @param clazz the configuration interface
     * @return the configuration pid
     */
    protected String getPid(Class<?> clazz) {
        if (clazz == Meta.class) {
            return null;
        }
        Meta.PID pid = clazz.getAnnotation(Meta.PID.class);
        if (pid != null) {
            String p = pid.value();
            return p != null && p.length() > 0 ? p : null;
        }
        return clazz.getName();
    }

    /**
     * Retrieve the interface used for the configuration given the configurable object.
     * This is the parameterized type of the Configurable class.
     *
     * @param configurable the configurable object
     * @return the configuration interface
     */
    protected Class<?> getConfigurationType(Configurable<?> configurable) {
        for (Type t : configurable.getClass().getGenericInterfaces()) {
            GenericType gt = new GenericType(t);
            if (gt.getRawClass() == Configurable.class) {
                return gt.getActualTypeArgument(0).getRawClass();
            }
        }
        throw new IllegalStateException("Unable to determine configuration type for class " + configurable.getClass());
    }

    /**
     * A tracker for OSGi services implementing the {@link Configurable} interface.
     */
    public class ConfigurableTracker extends ServiceTracker {

        public ConfigurableTracker(BundleContext context) {
            super(context, Configurable.class.getName(), null);
        }

        @Override
        public Object addingService(ServiceReference reference) {
            Object o = super.addingService(reference);
            addConfigurable((Configurable<Object>) o);
            return o;
        }

        @Override
        public void removedService(ServiceReference reference, Object service) {
            removeConfigurable((Configurable<?>) service);
            super.removedService(reference, service);
        }
    }

    /**
     * A tracker for ConfigurationAdmin services.
     */
    public class ConfigAdminTracker extends ServiceTracker {

        public ConfigAdminTracker(BundleContext context) {
            super(context, ConfigurationAdmin.class.getName(), null);
        }

        @Override
        public Object addingService(ServiceReference reference) {
            Object o = super.addingService(reference);
            addConfigAdmin(reference, (ConfigurationAdmin) o);
            return o;
        }

        @Override
        public void removedService(ServiceReference reference, Object service) {
            removeConfigAdmin(reference, (ConfigurationAdmin) service);
            super.removedService(reference, service);
        }
    }

}