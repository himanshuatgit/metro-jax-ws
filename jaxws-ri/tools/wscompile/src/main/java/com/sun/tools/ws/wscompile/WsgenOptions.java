/*
 * Copyright (c) 1997, 2022 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Distribution License v. 1.0, which is available at
 * http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * SPDX-License-Identifier: BSD-3-Clause
 */

package com.sun.tools.ws.wscompile;

import com.sun.tools.ws.api.WsgenExtension;
import com.sun.tools.ws.api.WsgenProtocol;
import com.sun.tools.ws.resources.WscompileMessages;
import com.sun.xml.ws.api.BindingID;
import com.sun.xml.ws.binding.SOAPBindingImpl;
import com.sun.xml.ws.util.ServiceFinder;

import jakarta.jws.WebService;
import javax.xml.namespace.QName;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

/**
 * @author Vivek Pandey
 */
public class WsgenOptions extends Options {
    /**
     * -servicename
     */
    public QName serviceName;

    /**
     * -portname
     */
    public QName portName;

    /**
     * -r
     */
    public File nonclassDestDir;


    /**
     * -wsdl
     */
    public boolean genWsdl;

    /**
     * -inlineSchemas
     */
    public boolean inlineSchemas;

    /**
     * protocol value
     */
    public String protocol = "soap1.1";

    public Set<String> protocols = new LinkedHashSet<>();
    public Map<String, String> nonstdProtocols = new LinkedHashMap<>();

    /**
     * -Xnosource
     */
    public boolean nosource;

    /**
     * -XwsgenReport
     */
    public File wsgenReport;

    /**
     * -Xdonotoverwrite
     */
    public boolean doNotOverWrite;

    /**
     * Tells if user specified a specific protocol
     */
    public boolean protocolSet = false;

    /**
     * <code>-x file1 -x file2 ...</code><br>
     * Files to be parsed to get classes' metadata in addition/instead of using annotations and reflection API
     */
    public List<String> externalMetadataFiles = new ArrayList<>();

    private static final String SERVICENAME_OPTION = "-servicename";
    private static final String PORTNAME_OPTION = "-portname";
    private static final String HTTP   = "http";
    private static final String SOAP11 = "soap1.1";
    public static final String X_SOAP12 = "Xsoap1.2";
    private static final String NOSOURCE_OPTION = "-Xnosource";

    public WsgenOptions() {
        protocols.add(SOAP11);
        protocols.add(X_SOAP12);
        nonstdProtocols.put(X_SOAP12, SOAPBindingImpl.X_SOAP12HTTP_BINDING);
        ServiceFinder<WsgenExtension> extn = ServiceFinder.find(WsgenExtension.class, ServiceLoader.load(WsgenExtension.class));
        for(WsgenExtension ext : extn) {
            Class clazz = ext.getClass();
            WsgenProtocol pro = (WsgenProtocol)clazz.getAnnotation(WsgenProtocol.class);
            protocols.add(pro.token());
            nonstdProtocols.put(pro.token(), pro.lexical());
        }
    }

    @Override
    protected int parseArguments(String[] args, int i) throws BadCommandLineException {

        int j = super.parseArguments(args, i);
        if (args[i].equals(SERVICENAME_OPTION)) {
            serviceName = QName.valueOf(requireArgument(SERVICENAME_OPTION, args, ++i));
            if (serviceName.getNamespaceURI() == null || serviceName.getNamespaceURI().length() == 0) {
                throw new BadCommandLineException(WscompileMessages.WSGEN_SERVICENAME_MISSING_NAMESPACE(args[i]));
            }
            if (serviceName.getLocalPart() == null || serviceName.getLocalPart().length() == 0) {
                throw new BadCommandLineException(WscompileMessages.WSGEN_SERVICENAME_MISSING_LOCALNAME(args[i]));
            }
            return 2;
        } else if (args[i].equals(PORTNAME_OPTION)) {
            portName = QName.valueOf(requireArgument(PORTNAME_OPTION, args, ++i));
            if (portName.getNamespaceURI() == null || portName.getNamespaceURI().length() == 0) {
                throw new BadCommandLineException(WscompileMessages.WSGEN_PORTNAME_MISSING_NAMESPACE(args[i]));
            }
            if (portName.getLocalPart() == null || portName.getLocalPart().length() == 0) {
                throw new BadCommandLineException(WscompileMessages.WSGEN_PORTNAME_MISSING_LOCALNAME(args[i]));
            }
            return 2;
        } else if (args[i].equals("-r")) {
            nonclassDestDir = new File(requireArgument("-r", args, ++i));
            if (!nonclassDestDir.exists()) {
                throw new BadCommandLineException(WscompileMessages.WSCOMPILE_NO_SUCH_DIRECTORY(nonclassDestDir.getPath()));
            }
            return 2;
        } else if (args[i].startsWith("-wsdl")) {
            genWsdl = true;
            //String value = requireArgument("-wsdl", args, ++i).substring(5);
            String value = args[i].substring(5);
            int index = value.indexOf(':');
            if (index == 0) {
                value = value.substring(1);
                index = value.indexOf('/');
                if (index == -1) {
                    protocol = value;
                } else {
                    protocol = value.substring(0, index);
                }
                protocolSet = true;
            }
            return 1;
        } else if (args[i].equals(NOSOURCE_OPTION)) {
            // -nosource implies -nocompile and -keep. this is undocumented switch.
            nosource = true;
            nocompile = true;
            keep = true;
            return 1;
        } else if (args[i].equals("-XwsgenReport")) {
            // undocumented switch for the test harness
            wsgenReport = new File(requireArgument("-XwsgenReport", args, ++i));
            return 2;
        } else if (args[i].equals("-Xdonotoverwrite")) {
            doNotOverWrite = true;
            return 1;
        } else if (args[i].equals("-inlineSchemas")) {
            inlineSchemas = true;
            return 1;
        } else if ("-x".equals(args[i])) {
            externalMetadataFiles.add(requireArgument("-x", args, ++i));
            return 1;
        }

        return j;
    }


    @Override
    protected void addFile(String arg) {
        endpoints.add(arg);
    }

    List<String> endpoints = new ArrayList<>();

    public Class endpoint;


    private boolean isImplClass;

    public void validate() throws BadCommandLineException {
        if(nonclassDestDir == null)
            nonclassDestDir = destDir;

        if (!protocols.contains(protocol)) {
            throw new BadCommandLineException(WscompileMessages.WSGEN_INVALID_PROTOCOL(protocol, protocols));
        }

        if (endpoints.isEmpty()) {
            throw new BadCommandLineException(WscompileMessages.WSGEN_MISSING_FILE());
        }
        if (protocol == null || protocol.equalsIgnoreCase(X_SOAP12) && !isExtensionMode()) {
            throw new BadCommandLineException(WscompileMessages.WSGEN_SOAP_12_WITHOUT_EXTENSION());
        }

        if (nonstdProtocols.containsKey(protocol) && !isExtensionMode()) {
            throw new BadCommandLineException(WscompileMessages.WSGEN_PROTOCOL_WITHOUT_EXTENSION(protocol));
        }
        if (inlineSchemas && !genWsdl) {
            throw new BadCommandLineException(WscompileMessages.WSGEN_INLINE_SCHEMAS_ONLY_WITH_WSDL());
        }

        validateEndpointClass();
        validateArguments();
    }
    /**
     * Get an implementation class annotated with @WebService annotation.
     */
    private void validateEndpointClass() throws BadCommandLineException {
        Class clazz = null;
        for(String cls : endpoints){
            clazz = getClass(cls);
            if (clazz == null)
                continue;

            if (clazz.isEnum() || clazz.isInterface() ||
                clazz.isPrimitive()) {
                continue;
            }
            isImplClass = true;
            WebService webService = (WebService) clazz.getAnnotation(WebService.class);
            if(webService == null)
                continue;
            break;
        }
        if(clazz == null){
            throw new BadCommandLineException(WscompileMessages.WSGEN_CLASS_NOT_FOUND(endpoints.get(0)));
        }
        if(!isImplClass){
            throw new BadCommandLineException(WscompileMessages.WSGEN_CLASS_MUST_BE_IMPLEMENTATION_CLASS(clazz.getName()));
        }
        endpoint = clazz;
        validateBinding();
    }

    private void validateBinding() throws BadCommandLineException {
        if (genWsdl) {
            BindingID binding = BindingID.parse(endpoint);
            if ((binding.equals(BindingID.SOAP12_HTTP) ||
                 binding.equals(BindingID.SOAP12_HTTP_MTOM)) &&
                    !(protocol.equals(X_SOAP12) && isExtensionMode())) {
                throw new BadCommandLineException(WscompileMessages.WSGEN_CANNOT_GEN_WSDL_FOR_SOAP_12_BINDING(binding.toString(), endpoint.getName()));
            }
            if (binding.equals(BindingID.XML_HTTP)) {
                throw new BadCommandLineException(WscompileMessages.WSGEN_CANNOT_GEN_WSDL_FOR_NON_SOAP_BINDING(binding.toString(), endpoint.getName()));
            }
        }
    }

    private void validateArguments() throws BadCommandLineException {
        if (!genWsdl) {
            if (serviceName != null) {
                throw new BadCommandLineException(WscompileMessages.WSGEN_WSDL_ARG_NO_GENWSDL(SERVICENAME_OPTION));
            }
            if (portName != null) {
                throw new BadCommandLineException(WscompileMessages.WSGEN_WSDL_ARG_NO_GENWSDL(PORTNAME_OPTION));
            }
            if (nosource) {
                throw new BadCommandLineException(WscompileMessages.WSGEN_WSDL_ARG_NO_GENWSDL(NOSOURCE_OPTION));
            }
        }
    }

    BindingID getBindingID(String protocol) {
        if (protocol.equals(SOAP11))
            return BindingID.SOAP11_HTTP;
        if (protocol.equals(X_SOAP12))
            return BindingID.SOAP12_HTTP;
        String lexical = nonstdProtocols.get(protocol);
        return (lexical != null) ? BindingID.parse(lexical) : null;
    }


    private Class getClass(String className) {
        try {
            return getClassLoader().loadClass(className);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

}
