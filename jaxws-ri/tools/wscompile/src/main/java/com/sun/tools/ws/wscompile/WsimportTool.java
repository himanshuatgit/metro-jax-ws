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

import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.writer.ProgressCodeWriter;
import com.sun.istack.tools.DefaultAuthenticator;
import com.sun.tools.ws.ToolVersion;
import com.sun.tools.ws.api.TJavaGeneratorExtension;
import com.sun.tools.ws.processor.generator.CustomExceptionGenerator;
import com.sun.tools.ws.processor.generator.GeneratorBase;
import com.sun.tools.ws.processor.generator.SeiGenerator;
import com.sun.tools.ws.processor.generator.ServiceGenerator;
import com.sun.tools.ws.processor.generator.JwsImplGenerator;
import com.sun.tools.ws.processor.model.Model;
import com.sun.tools.ws.processor.modeler.wsdl.ConsoleErrorReporter;
import com.sun.tools.ws.processor.modeler.wsdl.WSDLModeler;
import com.sun.tools.ws.processor.util.DirectoryUtil;
import com.sun.tools.ws.resources.WscompileMessages;
import com.sun.tools.ws.resources.WsdlMessages;
import com.sun.tools.ws.util.WSDLFetcher;
import com.sun.tools.ws.wsdl.parser.MetadataFinder;
import com.sun.tools.ws.wsdl.parser.WSDLInternalizationLogic;
import com.sun.tools.xjc.util.NullStream;
import com.sun.xml.ws.api.server.Container;
import com.sun.xml.ws.util.ServiceFinder;
import org.xml.sax.EntityResolver;
import org.xml.sax.SAXParseException;

import javax.xml.stream.*;
import java.io.*;
import java.util.*;
import java.text.MessageFormat;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;

/**
 * @author Vivek Pandey
 */
public class WsimportTool {
    /** JAXWS module name. JAXWS dependency is mandatory in generated Java module. */
    private static final String JAXWS_MODULE = "jakarta.xml.ws";

    private static final String WSIMPORT = "wsimport";
    private final PrintStream out;
    private final Container container;

    /**
     * Wsimport specific options
     */
    protected WsimportOptions options = new WsimportOptions();

    public WsimportTool(OutputStream out) {
        this(out, null);
    }

    public WsimportTool(OutputStream logStream, Container container) {
        this.out = (logStream instanceof PrintStream)?(PrintStream)logStream:new PrintStream(logStream);
        this.container = container;
    }

    public class Listener extends WsimportListener {
        ConsoleErrorReporter cer = new ConsoleErrorReporter(out == null ? new PrintStream(new NullStream()) : out);

        /**
         * Default constructor.
         */
        public Listener() {}

        @Override
        public void generatedFile(String fileName) {
            message(fileName);
        }

        @Override
        public void message(String msg) {
            out.println(msg);
        }

        @Override
        public void error(SAXParseException exception) {
            cer.error(exception);
        }

        @Override
        public void fatalError(SAXParseException exception) {
            cer.fatalError(exception);
        }

        @Override
        public void warning(SAXParseException exception) {
            cer.warning(exception);
        }

        @Override
        public void debug(SAXParseException exception) {
            cer.debug(exception);
        }

        @Override
        public void info(SAXParseException exception) {
            cer.info(exception);
        }

        public void enableDebugging(){
            cer.enableDebugging();
        }
    }

    protected class Receiver extends ErrorReceiverFilter {

        private Listener listener;

        public Receiver(Listener listener) {
            super(listener);
            this.listener = listener;
        }

        @Override
        public void info(SAXParseException exception) {
            if (options.verbose)
                super.info(exception);
        }

        @Override
        public void warning(SAXParseException exception) {
            if (!options.quiet)
                super.warning(exception);
        }

        @Override
        public void pollAbort() throws AbortException {
            if (listener.isCanceled())
                throw new AbortException();
        }

        @Override
        public void debug(SAXParseException exception){
            if(options.debugMode){
                listener.debug(exception);
            }
        }
    }

    public boolean run(String[] args) {
        Listener listener = new Listener();
        Receiver receiver = new Receiver(listener);
        return run(args, listener, receiver);
    }

    protected boolean run(String[] args, Listener listener,
                       Receiver receiver) {
        for (String arg : args) {
            if (arg.equals("-version")) {
                listener.message(
                        WscompileMessages.WSIMPORT_VERSION(ToolVersion.VERSION.MAJOR_VERSION));
                return true;
            }
            if (arg.equals("-fullversion")) {
                listener.message(
                        WscompileMessages.WSIMPORT_FULLVERSION(ToolVersion.VERSION.toString()));
                return true;
            }
        }

        try {
            parseArguments(args, listener, receiver);

            try {
                Model wsdlModel = buildWsdlModel(listener, receiver);
                if (wsdlModel == null)
                   return false;

                if (!generateCode(listener, receiver, wsdlModel, true))
                   return false;

                /* Not so fast!
            } catch(AbortException e){
                //error might have been reported
                 *
                 */
            }catch (IOException | XMLStreamException e) {
                receiver.error(e);
                return false;
            }
            if (!options.nocompile){
                if(!compileGeneratedClasses(receiver, listener)){
                    listener.message(WscompileMessages.WSCOMPILE_COMPILATION_FAILED());
                    return false;
                }
            }
            try {
                if (options.clientjar != null) {
                    //add all the generated class files to the list of generated files
                    addClassesToGeneratedFiles();
                    jarArtifacts(listener);

                }
            } catch (IOException e) {
                receiver.error(e);
                return false;
            }

        } catch (Options.WeAreDone done) {
            usage(done.getOptions());
        } catch (BadCommandLineException e) {
            if (e.getMessage() != null) {
                System.out.println(e.getMessage());
                System.out.println();
            }
            usage(options);
            return false;
        } finally{
            deleteGeneratedFiles();
            if (!options.disableAuthenticator) {
                DefaultAuthenticator.reset();
            }
        }
        return !receiver.hadError();
    }

    private void deleteGeneratedFiles() {
        Set<File> trackedRootPackages = new HashSet<>();

        if (options.clientjar != null) {
            //remove all non-java artifacts as they will packaged in jar.
            Iterable<File> generatedFiles = options.getGeneratedFiles();
            synchronized (generatedFiles) {
                for (File file : generatedFiles) {
                    if (!file.getName().endsWith(".java")) {
                        boolean deleted = file.delete();
                        if (options.verbose && !deleted) {
                            System.out.println(MessageFormat.format("{0} could not be deleted.", file));
                        }
                        trackedRootPackages.add(file.getParentFile());
                    }
                }
            }
            //remove empty package dirs
            for(File pkg:trackedRootPackages) {

                while (pkg != null && pkg.list() != null && pkg.list().length == 0 && !pkg.equals(options.destDir)) {
                    File parentPkg = pkg.getParentFile();
                    boolean deleted = pkg.delete();
                    if (options.verbose && !deleted) {
                        System.out.println(MessageFormat.format("{0} could not be deleted.", pkg));
                    }
                    pkg = parentPkg;
                }
            }
        }
        if(!options.keep) {
            options.removeGeneratedFiles();
        }
    }

    private void addClassesToGeneratedFiles() throws IOException {
        Iterable<File> generatedFiles = options.getGeneratedFiles();
        final List<File> trackedClassFiles = new ArrayList<>();
        for(File f: generatedFiles) {
            if(f.getName().endsWith(".java")) {
                String relativeDir = DirectoryUtil.getRelativePathfromCommonBase(f.getParentFile(),options.sourceDir);
                final String className = f.getName().substring(0,f.getName().indexOf(".java"));
                File classDir = new File(options.destDir,relativeDir);
                if(classDir.exists()) {
                    classDir.listFiles(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String name) {
                            if(name.equals(className+".class") || (name.startsWith(className+"$") && name.endsWith(".class"))) {
                                trackedClassFiles.add(new File(dir,name));
                                return true;
                            }
                            return false;
                        }
                    });
                }
            }
        }
        for(File f: trackedClassFiles) {
            options.addGeneratedFile(f);
        }
    }

    private void jarArtifacts(WsimportListener listener) throws IOException {
        File zipFile = new File(options.clientjar);
        if(!zipFile.isAbsolute()) {
            zipFile = new File(options.destDir, options.clientjar);
        }

        FileOutputStream fos;
        if (!options.quiet) {
            listener.message(WscompileMessages.WSIMPORT_ARCHIVING_ARTIFACTS(zipFile));
        }

        BufferedInputStream bis = null;
        FileInputStream fi = null;
        fos = new FileOutputStream(zipFile);
        JarOutputStream jos = new JarOutputStream(fos);
        try {
            String base = options.destDir.getCanonicalPath();
            for(File f: options.getGeneratedFiles()) {
                //exclude packaging the java files in the jar
                if(f.getName().endsWith(".java")) {
                    continue;
                }
                if(options.verbose) {
                    listener.message(WscompileMessages.WSIMPORT_ARCHIVE_ARTIFACT(f, options.clientjar));
                }
                String entry = f.getCanonicalPath().substring(base.length()+1).replace(File.separatorChar, '/');
                fi = new FileInputStream(f);
                bis = new BufferedInputStream(fi);
                JarEntry jarEntry = new JarEntry(entry);
                jos.putNextEntry(jarEntry);
                int bytesRead;
                byte[] buffer = new byte[1024];
                while ((bytesRead = bis.read(buffer)) != -1) {
                    jos.write(buffer, 0, bytesRead);
                }
            }
        } finally {
            try {
                if (bis != null) {
                    bis.close();
                }
            } finally {
                if (jos != null) {
                    jos.close();
                }
                if (fi != null) {
                    fi.close();
                }
            }
        }
    }

    protected void parseArguments(String[] args, Listener listener,
                                  Receiver receiver) throws BadCommandLineException {
        options.parseArguments(args);
        options.validate();
        if (options.debugMode)
            listener.enableDebugging();
        options.parseBindings(receiver);
    }

    protected Model buildWsdlModel(Listener listener, final Receiver receiver)
            throws BadCommandLineException, XMLStreamException, IOException {
        //set auth info
        //if(options.authFile != null)
        if (!options.disableAuthenticator) {
            class AuthListener implements DefaultAuthenticator.Receiver {

                private final boolean isFatal;

                AuthListener(boolean isFatal) {
                    this.isFatal = isFatal;
                }

                @Override
                public void onParsingError(String text, Locator loc) {
                    error(new SAXParseException(WscompileMessages.WSIMPORT_ILLEGAL_AUTH_INFO(text), loc));
                }

                @Override
                public void onError(Exception e, Locator loc) {
                    if (e instanceof FileNotFoundException) {
                        error(new SAXParseException(WscompileMessages.WSIMPORT_AUTH_FILE_NOT_FOUND(
                                loc.getSystemId(), WsimportOptions.defaultAuthfile), null));
                    } else {
                        error(new SAXParseException(WscompileMessages.WSIMPORT_FAILED_TO_PARSE(loc.getSystemId(),e.getMessage()), loc));
                    }
                }

                private void error(SAXParseException e) {
                    if (isFatal) {
                        receiver.error(e);
                    } else {
                        receiver.debug(e);
                    }
                }
            }

            DefaultAuthenticator da = DefaultAuthenticator.getAuthenticator();
            if (options.proxyAuth != null) {
                da.setProxyAuth(options.proxyAuth);
            }
            if (options.authFile != null) {
                da.setAuth(options.authFile, new AuthListener(true));
            } else {
                da.setAuth(new File(WsimportOptions.defaultAuthfile), new AuthListener(false));
            }
        }

        if (!options.quiet) {
            listener.message(WscompileMessages.WSIMPORT_PARSING_WSDL());
        }

        MetadataFinder forest = new MetadataFinder(new WSDLInternalizationLogic(), options, receiver);
        forest.parseWSDL();
        if (forest.isMexMetadata)
            receiver.reset();

        WSDLModeler wsdlModeler = new WSDLModeler(options, receiver,forest);
        Model wsdlModel = wsdlModeler.buildModel();
        if (wsdlModel == null) {
            listener.message(WsdlMessages.PARSING_PARSE_FAILED());
        }

        if(options.clientjar != null) {
            if( !options.quiet )
                listener.message(WscompileMessages.WSIMPORT_FETCHING_METADATA());
            options.wsdlLocation = new WSDLFetcher(options,listener).fetchWsdls(forest);
        }

        return wsdlModel;
    }

    protected boolean generateCode(Listener listener, Receiver receiver,
                                   Model wsdlModel, boolean generateService)
                                   throws IOException {
        //generated code
        if( !options.quiet )
            listener.message(WscompileMessages.WSIMPORT_GENERATING_CODE());

        @SuppressWarnings({"deprecation"})
        TJavaGeneratorExtension[] genExtn = ServiceFinder.find(TJavaGeneratorExtension.class, ServiceLoader.load(TJavaGeneratorExtension.class)).toArray();
        CustomExceptionGenerator.generate(wsdlModel,  options, receiver);
        SeiGenerator.generate(wsdlModel, options, receiver, genExtn);
        if(receiver.hadError()){
            throw new AbortException();
        }
        if (generateService)
        {
            ServiceGenerator.generate(wsdlModel, options, receiver);
        }
        for (GeneratorBase g : ServiceFinder.find(GeneratorBase.class, ServiceLoader.load(GeneratorBase.class))) {
            g.init(wsdlModel, options, receiver);
            g.doGeneration();
        }

        List<String> implFiles = null;
       if (options.isGenerateJWS) {
	        implFiles = JwsImplGenerator.generate(wsdlModel, options, receiver);
       }

        for (Plugin plugin: options.activePlugins) {
            try {
                plugin.run(wsdlModel, options, receiver);
            } catch (SAXException sex) {
                // fatal error. error should have been reported
                return false;
            }
        }

        if (options.getModuleName() != null) {
            options.getCodeModel()._prepareModuleInfo(options.getModuleName(), JAXWS_MODULE);
        }

        CodeWriter cw;
        if (options.filer != null) {
            cw = new FilerCodeWriter(options);
        } else {
            cw = new WSCodeWriter(options.sourceDir, options);
        }

        if (options.verbose)
            cw = new ProgressCodeWriter(cw, out);
        options.getCodeModel().build(cw);

        if (options.isGenerateJWS) {
	        //move Impl files to implDestDir
	        return JwsImplGenerator.moveToImplDestDir(implFiles, options, receiver);
       }

       return true;
    }

    public void setEntityResolver(EntityResolver resolver){
        this.options.entityResolver = resolver;
    }

    protected boolean compileGeneratedClasses(ErrorReceiver receiver, WsimportListener listener){
        List<String> sourceFiles = new ArrayList<>();

        for (File f : options.getGeneratedFiles()) {
            if (f.exists() && f.getName().endsWith(".java")) {
                sourceFiles.add(f.getAbsolutePath());
            }
        }

        if (sourceFiles.size() > 0) {
            String classDir = options.destDir.getAbsolutePath();
            String classpathString = createClasspathString();
            List<String> args = new ArrayList<>();

            args.add("-d");
            args.add(classDir);
            args.add("-classpath");
            args.add(classpathString);

            if (options.debug) {
                args.add("-g");
            }

            if (options.encoding != null) {
                args.add("-encoding");
                args.add(options.encoding);
            }

            if (options.javacOptions != null) {
                args.addAll(options.getJavacOptions(args, listener));
            }

            args.addAll(sourceFiles);

            if (!options.quiet) listener.message(WscompileMessages.WSIMPORT_COMPILING_CODE());

            if(options.verbose){
                StringBuilder argstr = new StringBuilder();
                for(String arg:args){
                    argstr.append(arg).append(" ");
                }
                listener.message("javac "+ argstr);
            }

            return JavaCompilerHelper.compile(args.toArray(new String[0]), out, receiver);
        }
        //there are no files to compile, so return true?
        return true;
    }

    private String createClasspathString() {
        StringBuilder classpathStr = new StringBuilder(System.getProperty("java.class.path"));
        for(String s: options.cmdlineJars) {
            classpathStr.append(File.pathSeparator);
            classpathStr.append(new File(s));
        }
        return classpathStr.toString();
    }

    protected void usage(Options options) {
        System.out.println(WscompileMessages.WSIMPORT_HELP(WSIMPORT));
        System.out.println(WscompileMessages.WSIMPORT_USAGE_EXTENSIONS());
        if (options != null) {
            WsimportOptions opts = (WsimportOptions) options;
            List<Plugin> plugins = opts.getAllPlugins();
            if (!plugins.isEmpty()) {
                System.out.println(WscompileMessages.WSIMPORT_USAGE_PLUGINS());
                for (Plugin p : plugins) {
                    System.out.println(p.getUsage());
                }
            }
        }
        System.out.println(WscompileMessages.WSIMPORT_USAGE_EXAMPLES());
    }
}
