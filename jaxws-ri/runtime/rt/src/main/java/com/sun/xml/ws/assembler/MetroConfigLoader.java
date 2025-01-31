/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.xml.ws.assembler;

import com.sun.istack.NotNull;
import com.sun.istack.logging.Logger;
import com.sun.xml.ws.api.ResourceLoader;
import com.sun.xml.ws.api.server.Container;
import com.sun.xml.ws.resources.TubelineassemblyMessages;
import com.sun.xml.ws.runtime.config.MetroConfig;
import com.sun.xml.ws.runtime.config.TubeFactoryList;
import com.sun.xml.ws.runtime.config.TubelineDefinition;
import com.sun.xml.ws.runtime.config.TubelineMapping;
import com.sun.xml.ws.util.xml.XmlUtil;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import jakarta.xml.ws.WebServiceException;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedExceptionAction;
import java.util.logging.Level;

/**
 * This class is responsible for locating and loading Metro configuration files
 * (both application jaxws-tubes.xml and default jaxws-tubes-default.xml).
 * <br>
 * Once the configuration is loaded the class is able to resolve which tubeline
 * configuration belongs to each endpoint or endpoint client. This information is
 * then used in {@link TubelineAssemblyController} to construct the list of
 * {@link TubeCreator} objects that are used in the actual tubeline construction.
 *
 * @author Marek Potociar
 */
// TODO Move the logic of this class directly into MetroConfig class.
class MetroConfigLoader {

    private static final String JAXWS_TUBES_JDK_XML_RESOURCE = "jaxws-tubes-default.xml";
    private static final Logger LOGGER = Logger.getLogger(MetroConfigLoader.class);

    private MetroConfigName defaultTubesConfigNames;

    private interface TubeFactoryListResolver {

        TubeFactoryList getFactories(TubelineDefinition td);
    }

    private static final TubeFactoryListResolver ENDPOINT_SIDE_RESOLVER = new TubeFactoryListResolver() {

        @Override
        public TubeFactoryList getFactories(TubelineDefinition td) {
            return (td != null) ? td.getEndpointSide() : null;
        }
    };
    private static final TubeFactoryListResolver CLIENT_SIDE_RESOLVER = new TubeFactoryListResolver() {

        @Override
        public TubeFactoryList getFactories(TubelineDefinition td) {
            return (td != null) ? td.getClientSide() : null;
        }
    };
    //
    private MetroConfig defaultConfig;
    private URL defaultConfigUrl;
    private MetroConfig appConfig;
    private URL appConfigUrl;

    MetroConfigLoader(Container container, MetroConfigName defaultTubesConfigNames) {
        this.defaultTubesConfigNames = defaultTubesConfigNames;
        ResourceLoader spiResourceLoader = null;
        if (container != null) {
            spiResourceLoader = container.getSPI(ResourceLoader.class);
        }
        // if spi resource can't load resource, default (MetroConfigUrlLoader) is used;
        // it searches the classpath, so it would be most probably used
        // when using jaxws- or metro-defaults from jaxws libraries
        init(container, spiResourceLoader, new MetroConfigUrlLoader(container));
    }

    private void init(Container container, ResourceLoader... loaders) {

        String appFileName = null;
        String defaultFileName = null;
        if (container != null) {
            MetroConfigName mcn = container.getSPI(MetroConfigName.class);
            if (mcn != null) {
                appFileName = mcn.getAppFileName();
                defaultFileName = mcn.getDefaultFileName();
            }
        }
        if (appFileName == null) {
            appFileName = defaultTubesConfigNames.getAppFileName();
        }

        if (defaultFileName == null) {
            defaultFileName = defaultTubesConfigNames.getDefaultFileName();
        }
        this.defaultConfigUrl = locateResource(defaultFileName, loaders);
        if (defaultConfigUrl != null) {
            LOGGER.config(TubelineassemblyMessages.MASM_0002_DEFAULT_CFG_FILE_LOCATED(defaultFileName, defaultConfigUrl));
        }

        this.defaultConfig = MetroConfigLoader.loadMetroConfig(defaultConfigUrl);
        if (defaultConfig == null) {
            throw LOGGER.logSevereException(new IllegalStateException(TubelineassemblyMessages.MASM_0003_DEFAULT_CFG_FILE_NOT_LOADED(defaultFileName)));
        }
        if (defaultConfig.getTubelines() == null) {
            throw LOGGER.logSevereException(new IllegalStateException(TubelineassemblyMessages.MASM_0004_NO_TUBELINES_SECTION_IN_DEFAULT_CFG_FILE(defaultFileName)));
        }
        if (defaultConfig.getTubelines().getDefault() == null) {
            throw LOGGER.logSevereException(new IllegalStateException(TubelineassemblyMessages.MASM_0005_NO_DEFAULT_TUBELINE_IN_DEFAULT_CFG_FILE(defaultFileName)));
        }

        this.appConfigUrl = locateResource(appFileName, loaders);
        if (appConfigUrl != null) {
            LOGGER.config(TubelineassemblyMessages.MASM_0006_APP_CFG_FILE_LOCATED(appConfigUrl));
            this.appConfig = MetroConfigLoader.loadMetroConfig(appConfigUrl);
        } else {
            LOGGER.config(TubelineassemblyMessages.MASM_0007_APP_CFG_FILE_NOT_FOUND());
            this.appConfig = null;
        }
    }

    TubeFactoryList getEndpointSideTubeFactories(URI endpointReference) {
        return getTubeFactories(endpointReference, ENDPOINT_SIDE_RESOLVER);
    }

    TubeFactoryList getClientSideTubeFactories(URI endpointReference) {
        return getTubeFactories(endpointReference, CLIENT_SIDE_RESOLVER);
    }

    private TubeFactoryList getTubeFactories(URI endpointReference, TubeFactoryListResolver resolver) {
        if (appConfig != null && appConfig.getTubelines() != null) {
            for (TubelineMapping mapping : appConfig.getTubelines().getTubelineMappings()) {
                if (mapping.getEndpointRef().equals(endpointReference.toString())) {
                    TubeFactoryList list = resolver.getFactories(getTubeline(appConfig, resolveReference(mapping.getTubelineRef())));
                    if (list != null) {
                        return list;
                    } else {
                        break;
                    }
                }
            }

            if (appConfig.getTubelines().getDefault() != null) {
                TubeFactoryList list = resolver.getFactories(getTubeline(appConfig, resolveReference(appConfig.getTubelines().getDefault())));
                if (list != null) {
                    return list;
                }
            }
        }

        for (TubelineMapping mapping : defaultConfig.getTubelines().getTubelineMappings()) {
            if (mapping.getEndpointRef().equals(endpointReference.toString())) {
                TubeFactoryList list = resolver.getFactories(getTubeline(defaultConfig, resolveReference(mapping.getTubelineRef())));
                if (list != null) {
                    return list;
                } else {
                    break;
                }
            }
        }

        return resolver.getFactories(getTubeline(defaultConfig, resolveReference(defaultConfig.getTubelines().getDefault())));
    }

    TubelineDefinition getTubeline(MetroConfig config, URI tubelineDefinitionUri) {
        if (config != null && config.getTubelines() != null) {
            for (TubelineDefinition td : config.getTubelines().getTubelineDefinitions()) {
                if (td.getName().equals(tubelineDefinitionUri.getFragment())) {
                    return td;
                }
            }
        }

        return null;
    }

    private static URI resolveReference(String reference) {
        try {
            return new URI(reference);
        } catch (URISyntaxException ex) {
            throw LOGGER.logSevereException(new WebServiceException(TubelineassemblyMessages.MASM_0008_INVALID_URI_REFERENCE(reference), ex));
        }
    }


    private static URL locateResource(String resource, ResourceLoader loader) {
        if (loader == null) return null;

        try {
            return loader.getResource(resource);
        } catch (MalformedURLException ex) {
            LOGGER.severe(TubelineassemblyMessages.MASM_0009_CANNOT_FORM_VALID_URL(resource), ex);
        }
        return null;
    }

    private static URL locateResource(String resource, ResourceLoader[] loaders) {

        for (ResourceLoader loader : loaders) {
            URL url = locateResource(resource, loader);
            if (url != null) {
                return url;
            }
        }
        return null;
    }

    private static MetroConfig loadMetroConfig(@NotNull URL resourceUrl) {
        try (InputStream is = getConfigInputStream(resourceUrl)) {
            JAXBContext jaxbContext = createJAXBContext();
            Unmarshaller unmarshaller = jaxbContext.createUnmarshaller();
            XMLInputFactory factory = XmlUtil.newXMLInputFactory(false);
            JAXBElement<MetroConfig> configElement = unmarshaller.unmarshal(factory.createXMLStreamReader(is), MetroConfig.class);
            return configElement.getValue();
        } catch (Exception e) {
            String message = TubelineassemblyMessages.MASM_0010_ERROR_READING_CFG_FILE_FROM_LOCATION(
                    resourceUrl != null ? resourceUrl.toString() : null);
            InternalError error = new InternalError(message);
            LOGGER.logException(error, e, Level.SEVERE);
            throw error;
        }
    }

    private static InputStream getConfigInputStream(URL resourceUrl) throws IOException {
        InputStream is;
        if (resourceUrl != null) {
            is = resourceUrl.openStream();
        } else {
            is = MetroConfigLoader.class.getResourceAsStream(JAXWS_TUBES_JDK_XML_RESOURCE);

            if (is == null)
                throw LOGGER.logSevereException(
                        new IllegalStateException(
                                TubelineassemblyMessages.MASM_0001_DEFAULT_CFG_FILE_NOT_FOUND(JAXWS_TUBES_JDK_XML_RESOURCE)));
        }

        return is;
    }

    private static JAXBContext createJAXBContext() throws Exception {
        // usage from JAX-WS/Metro/Glassfish
        return JAXBContext.newInstance(MetroConfig.class.getPackage().getName());
    }


    private static class MetroConfigUrlLoader extends ResourceLoader {

        ResourceLoader parentLoader;

        MetroConfigUrlLoader(ResourceLoader parentLoader) {
            this.parentLoader = parentLoader;
        }

        MetroConfigUrlLoader(Container container) {
            this((container != null) ? container.getSPI(ResourceLoader.class) : null);
        }

        @Override
        public URL getResource(String resource) throws MalformedURLException {
            LOGGER.entering(resource);
            URL resourceUrl = null;
            try {
                if (parentLoader != null) {
                    if (LOGGER.isLoggable(Level.FINE)) {
                        LOGGER.fine(TubelineassemblyMessages.MASM_0011_LOADING_RESOURCE(resource, parentLoader));
                    }

                    resourceUrl = parentLoader.getResource(resource);
                }

                if (resourceUrl == null) {
                    resourceUrl = loadViaClassLoaders("META-INF/" + resource);
                }

                return resourceUrl;
            } finally {
                LOGGER.exiting(resourceUrl);
            }
        }

        private static URL loadViaClassLoaders(final String resource) {
            URL resourceUrl = tryLoadFromClassLoader(resource, Thread.currentThread().getContextClassLoader());
            if (resourceUrl == null) {
                resourceUrl = tryLoadFromClassLoader(resource, MetroConfigLoader.class.getClassLoader());
                if (resourceUrl == null) {
                    return ClassLoader.getSystemResource(resource);
                }
            }

            return resourceUrl;
        }

        private static URL tryLoadFromClassLoader(final String resource, final ClassLoader loader) {
            return (loader != null) ? loader.getResource(resource) : null;
        }
    }

}
