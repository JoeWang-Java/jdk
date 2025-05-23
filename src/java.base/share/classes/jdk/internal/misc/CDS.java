/*
 * Copyright (c) 2020, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package jdk.internal.misc;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import jdk.internal.access.SharedSecrets;
import jdk.internal.util.StaticProperty;

public class CDS {
    // Must be in sync with cdsConfig.hpp
    private static final int IS_DUMPING_ARCHIVE              = 1 << 0;
    private static final int IS_DUMPING_METHOD_HANDLES       = 1 << 1;
    private static final int IS_DUMPING_STATIC_ARCHIVE       = 1 << 2;
    private static final int IS_LOGGING_LAMBDA_FORM_INVOKERS = 1 << 3;
    private static final int IS_USING_ARCHIVE                = 1 << 4;
    private static final int configStatus = getCDSConfigStatus();

    /**
     * Should we log the use of lambda form invokers?
     */
    public static boolean isLoggingLambdaFormInvokers() {
        return (configStatus & IS_LOGGING_LAMBDA_FORM_INVOKERS) != 0;
    }

    /**
      * Is the VM writing to a (static or dynamic) CDS archive.
      */
    public static boolean isDumpingArchive() {
        return (configStatus & IS_DUMPING_ARCHIVE) != 0;
    }

    /**
      * Is the VM using at least one CDS archive?
      */
    public static boolean isUsingArchive() {
        return (configStatus & IS_USING_ARCHIVE) != 0;
    }

    /**
      * Is dumping static archive.
      */
    public static boolean isDumpingStaticArchive() {
        return (configStatus & IS_DUMPING_STATIC_ARCHIVE) != 0;
    }

    public static boolean isSingleThreadVM() {
        return isDumpingStaticArchive();
    }

    private static native int getCDSConfigStatus();
    private static native void logLambdaFormInvoker(String line);


    // Used only when dumping static archive to keep weak references alive to
    // ensure that Soft/Weak Reference objects can be reliably archived.
    private static ArrayList<Object> keepAliveList;

    public static void keepAlive(Object s) {
        assert isSingleThreadVM(); // no need for synchronization
        assert isDumpingStaticArchive();
        if (keepAliveList == null) {
            keepAliveList = new ArrayList<>();
        }
        keepAliveList.add(s);
    }

    // This is called by native JVM code at the very end of Java execution before
    // dumping the static archive.
    // It collects the objects from keepAliveList so that they can be easily processed
    // by the native JVM code to check that any Reference objects that need special
    // clean up must have been registed with keepAlive()
    private static Object[] getKeepAliveObjects() {
        return keepAliveList.toArray();
    }

    /**
     * Initialize archived static fields in the given Class using archived
     * values from CDS dump time. Also initialize the classes of objects in
     * the archived graph referenced by those fields.
     *
     * Those static fields remain as uninitialized if there is no mapped CDS
     * java heap data or there is any error during initialization of the
     * object class in the archived graph.
     */
    public static native void initializeFromArchive(Class<?> c);

    /**
     * Ensure that the native representation of all archived java.lang.Module objects
     * are properly restored.
     */
    public static native void defineArchivedModules(ClassLoader platformLoader, ClassLoader systemLoader);

    /**
     * Returns a predictable "random" seed derived from the VM's build ID and version,
     * to be used by java.util.ImmutableCollections to ensure that archived
     * ImmutableCollections are always sorted the same order for the same VM build.
     */
    public static native long getRandomSeedForDumping();

    /**
     * log lambda form invoker holder, name and method type
     */
    public static void logLambdaFormInvoker(String prefix, String holder, String name, String type) {
        if (isLoggingLambdaFormInvokers()) {
            logLambdaFormInvoker(prefix + " " + holder + " " + name + " " + type);
        }
    }

    /**
      * log species
      */
    public static void logSpeciesType(String prefix, String cn) {
        if (isLoggingLambdaFormInvokers()) {
            logLambdaFormInvoker(prefix + " " + cn);
        }
    }

    static final String DIRECT_HOLDER_CLASS_NAME  = "java.lang.invoke.DirectMethodHandle$Holder";
    static final String DELEGATING_HOLDER_CLASS_NAME = "java.lang.invoke.DelegatingMethodHandle$Holder";
    static final String BASIC_FORMS_HOLDER_CLASS_NAME = "java.lang.invoke.LambdaForm$Holder";
    static final String INVOKERS_HOLDER_CLASS_NAME = "java.lang.invoke.Invokers$Holder";

    private static boolean isValidHolderName(String name) {
        return name.equals(DIRECT_HOLDER_CLASS_NAME)      ||
               name.equals(DELEGATING_HOLDER_CLASS_NAME)  ||
               name.equals(BASIC_FORMS_HOLDER_CLASS_NAME) ||
               name.equals(INVOKERS_HOLDER_CLASS_NAME);
    }

    private static boolean isBasicTypeChar(char c) {
         return "LIJFDV".indexOf(c) >= 0;
    }

    private static boolean isValidMethodType(String type) {
        String[] typeParts = type.split("_");
        // check return type (second part)
        if (typeParts.length != 2 || typeParts[1].length() != 1
                || !isBasicTypeChar(typeParts[1].charAt(0))) {
            return false;
        }
        // first part
        if (!isBasicTypeChar(typeParts[0].charAt(0))) {
            return false;
        }
        for (int i = 1; i < typeParts[0].length(); i++) {
            char c = typeParts[0].charAt(i);
            if (!isBasicTypeChar(c)) {
                if (!(c >= '0' && c <= '9')) {
                    return false;
                }
            }
        }
        return true;
    }

    // Throw exception on invalid input
    private static void validateInputLines(String[] lines) {
        for (String s: lines) {
            if (!s.startsWith("[LF_RESOLVE]") && !s.startsWith("[SPECIES_RESOLVE]")) {
                throw new IllegalArgumentException("Wrong prefix: " + s);
            }

            String[] parts = s.split(" ");
            boolean isLF = s.startsWith("[LF_RESOLVE]");

            if (isLF) {
                if (parts.length != 4) {
                    throw new IllegalArgumentException("Incorrect number of items in the line: " + parts.length);
                }
                if (!isValidHolderName(parts[1])) {
                    throw new IllegalArgumentException("Invalid holder class name: " + parts[1]);
                }
                if (!isValidMethodType(parts[3])) {
                    throw new IllegalArgumentException("Invalid method type: " + parts[3]);
                }
            } else {
                if (parts.length != 2) {
                   throw new IllegalArgumentException("Incorrect number of items in the line: " + parts.length);
                }
           }
      }
    }

    /**
     * called from vm to generate MethodHandle holder classes
     * @return {@code Object[]} if holder classes can be generated.
     * @param lines in format of LF_RESOLVE or SPECIES_RESOLVE output
     */
    private static Object[] generateLambdaFormHolderClasses(String[] lines) {
        Objects.requireNonNull(lines);
        validateInputLines(lines);
        Stream<String> lineStream = Arrays.stream(lines);
        Map<String, byte[]> result = SharedSecrets.getJavaLangInvokeAccess().generateHolderClasses(lineStream);
        int size = result.size();
        Object[] retArray = new Object[size * 2];
        int index = 0;
        for (Map.Entry<String, byte[]> entry : result.entrySet()) {
            retArray[index++] = entry.getKey();
            retArray[index++] = entry.getValue();
        };
        return retArray;
    }

    private static native void dumpClassList(String listFileName);
    private static native void dumpDynamicArchive(String archiveFileName);

    private static String drainOutput(InputStream stream, long pid, String tail, List<String> cmds) {
        String fileName  = "java_pid" + pid + "_" + tail;
        new Thread( ()-> {
            try (InputStreamReader isr = new InputStreamReader(stream);
                 BufferedReader rdr = new BufferedReader(isr);
                 PrintStream prt = new PrintStream(fileName)) {
                prt.println("Command:");
                for (String s : cmds) {
                    prt.print(s + " ");
                }
                prt.println("");
                String line;
                while((line = rdr.readLine()) != null) {
                    prt.println(line);
                }
            } catch (IOException e) {
                throw new RuntimeException("IOException happens during drain stream to file " +
                                           fileName + ": " + e.getMessage());
            }}).start();
        return fileName;
    }

    private static String[] excludeFlags = {
         "-XX:DumpLoadedClassList=",
         "-XX:+RecordDynamicDumpInfo",
         "-Xshare:",
         "-XX:SharedClassListFile=",
         "-XX:SharedArchiveFile=",
         "-XX:ArchiveClassesAtExit="};
    private static boolean containsExcludedFlags(String testStr) {
       for (String e : excludeFlags) {
           if (testStr.contains(e)) {
               return true;
           }
       }
       return false;
    }

    /**
    * called from jcmd VM.cds to dump static or dynamic shared archive
    * @param isStatic true for dump static archive or false for dynnamic archive.
    * @param fileName user input archive name, can be null.
    * @return The archive name if successfully dumped.
    */
    private static String dumpSharedArchive(boolean isStatic, String fileName) throws Exception {
        String cwd = new File("").getAbsolutePath(); // current dir used for printing message.
        String currentPid = String.valueOf(ProcessHandle.current().pid());
        String archiveFileName =  fileName != null ? fileName :
            "java_pid" + currentPid + (isStatic ? "_static.jsa" : "_dynamic.jsa");

        String tempArchiveFileName = archiveFileName + ".temp";
        File tempArchiveFile = new File(tempArchiveFileName);
        // The operation below may cause exception if the file or its dir is protected.
        if (!tempArchiveFile.exists()) {
            tempArchiveFile.createNewFile();
        }
        tempArchiveFile.delete();

        if (isStatic) {
            String listFileName = archiveFileName + ".classlist";
            File listFile = new File(listFileName);
            if (listFile.exists()) {
                listFile.delete();
            }
            dumpClassList(listFileName);
            String jdkHome = StaticProperty.javaHome();
            String classPath = System.getProperty("java.class.path");
            List<String> cmds = new ArrayList<String>();
            cmds.add(jdkHome + File.separator + "bin" + File.separator + "java"); // java
            cmds.add("-cp");
            cmds.add(classPath);
            cmds.add("-Xlog:cds");
            cmds.add("-Xshare:dump");
            cmds.add("-XX:SharedClassListFile=" + listFileName);
            cmds.add("-XX:SharedArchiveFile=" + tempArchiveFileName);

            // All runtime args.
            String[] vmArgs = VM.getRuntimeArguments();
            if (vmArgs != null) {
                for (String arg : vmArgs) {
                    if (arg != null && !containsExcludedFlags(arg)) {
                        cmds.add(arg);
                    }
                }
            }

            Process proc = Runtime.getRuntime().exec(cmds.toArray(new String[0]));

            // Drain stdout/stderr to files in new threads.
            String stdOutFileName = drainOutput(proc.getInputStream(), proc.pid(), "stdout", cmds);
            String stdErrFileName = drainOutput(proc.getErrorStream(), proc.pid(), "stderr", cmds);

            proc.waitFor();
            // done, delete classlist file.
            listFile.delete();

            // Check if archive has been successfully dumped. We won't reach here if exception happens.
            // Throw exception if file is not created.
            if (!tempArchiveFile.exists()) {
                throw new RuntimeException("Archive file " + tempArchiveFileName +
                                           " is not created, please check stdout file " +
                                            cwd + File.separator + stdOutFileName + " or stderr file " +
                                            cwd + File.separator + stdErrFileName + " for more detail");
            }
        } else {
            dumpDynamicArchive(tempArchiveFileName);
            if (!tempArchiveFile.exists()) {
                throw new RuntimeException("Archive file " + tempArchiveFileName +
                                           " is not created, please check current working directory " +
                                           cwd  + " for process " +
                                           currentPid + " output for more detail");
            }
        }
        // Override the existing archive file
        File archiveFile = new File(archiveFileName);
        if (archiveFile.exists()) {
            archiveFile.delete();
        }
        if (!tempArchiveFile.renameTo(archiveFile)) {
            throw new RuntimeException("Cannot rename temp file " + tempArchiveFileName + " to archive file" + archiveFileName);
        }
        // Everything goes well, print out the file name.
        String archiveFilePath = new File(archiveFileName).getAbsolutePath();
        System.out.println("The process was attached by jcmd and dumped a " + (isStatic ? "static" : "dynamic") + " archive " + archiveFilePath);
        return archiveFilePath;
    }

    /**
     * Detects if we need to emit explicit class initialization checks in
     * AOT-cached MethodHandles and VarHandles before accessing static fields
     * and methods.
     * @see jdk.internal.misc.Unsafe::shouldBeInitialized
     *
     * @return false only if a call to {@code ensureClassInitialized} would have
     * no effect during the application's production run.
     */
    public static boolean needsClassInitBarrier(Class<?> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        if ((configStatus & IS_DUMPING_METHOD_HANDLES) == 0) {
            return false;
        } else {
            return needsClassInitBarrier0(c);
        }
    }

    private static native boolean needsClassInitBarrier0(Class<?> c);

    /**
     * This class is used only by native JVM code at CDS dump time for loading
     * "unregistered classes", which are archived classes that are intended to
     * be loaded by custom class loaders during runtime.
     * See src/hotspot/share/cds/unregisteredClasses.cpp.
     */
    private static class UnregisteredClassLoader extends URLClassLoader {
        private String currentClassName;
        private Class<?> currentSuperClass;
        private Class<?>[] currentInterfaces;

        /**
         * Used only by native code. Construct an UnregisteredClassLoader for loading
         * unregistered classes from the specified file. If the file doesn't exist,
         * the exception will be caughted by native code which will print a warning message and continue.
         *
         * @param fileName path of the the JAR file to load unregistered classes from.
         */
        private UnregisteredClassLoader(String fileName) throws InvalidPathException, IOException {
            super(toURLArray(fileName), /*parent*/null);
            currentClassName = null;
            currentSuperClass = null;
            currentInterfaces = null;
        }

        private static URL[] toURLArray(String fileName) throws InvalidPathException, IOException {
            if (!((new File(fileName)).exists())) {
                throw new IOException("No such file: " + fileName);
            }
            return new URL[] {
                // Use an intermediate File object to construct a URI/URL without
                // authority component as URLClassPath can't handle URLs with a UNC
                // server name in the authority component.
                Path.of(fileName).toRealPath().toFile().toURI().toURL()
            };
        }


        /**
         * Load the class of the given <code>/name<code> from the JAR file that was given to
         * the constructor of the current UnregisteredClassLoader instance. This class must be
         * a direct subclass of <code>superClass</code>. This class must be declared to implement
         * the specified <code>interfaces</code>.
         * <p>
         * This method must be called in a single threaded context. It will never be recursed (thus
         * the asserts)
         *
         * @param name the name of the class to be loaded.
         * @param superClass must not be null. The named class must have a super class.
         * @param interfaces could be null if the named class does not implement any interfaces.
         */
        private Class<?> load(String name, Class<?> superClass, Class<?>[] interfaces)
            throws ClassNotFoundException
        {
            assert currentClassName == null;
            assert currentSuperClass == null;
            assert currentInterfaces == null;

            try {
                currentClassName = name;
                currentSuperClass = superClass;
                currentInterfaces = interfaces;

                return findClass(name);
            } finally {
                currentClassName = null;
                currentSuperClass = null;
                currentInterfaces = null;
            }
        }

        /**
         * This method must be called from inside the <code>load()</code> method. The <code>/name<code>
         * can be only:
         * <ul>
         * <li> the <code>name</code> parameter for <code>load()</code>
         * <li> the name of the <code>superClass</code> parameter for <code>load()</code>
         * <li> the name of one of the interfaces in <code>interfaces</code> parameter for <code>load()</code>
         * <ul>
         *
         * For all other cases, a <code>ClassNotFoundException</code> will be thrown.
         */
        protected Class<?> findClass(final String name)
            throws ClassNotFoundException
        {
            Objects.requireNonNull(currentClassName);
            Objects.requireNonNull(currentSuperClass);

            if (name.equals(currentClassName)) {
                // Note: the following call will call back to <code>this.findClass(name)</code> to
                // resolve the super types of the named class.
                return super.findClass(name);
            }
            if (name.equals(currentSuperClass.getName())) {
                return currentSuperClass;
            }
            if (currentInterfaces != null) {
                for (Class<?> c : currentInterfaces) {
                    if (name.equals(c.getName())) {
                        return c;
                    }
                }
            }

            throw new ClassNotFoundException(name);
        }
    }
}
