/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.util;

import org.apache.flink.annotation.VisibleForTesting;
import org.apache.flink.configuration.GlobalConfiguration;
import org.apache.flink.util.OperatingSystem;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.lang.reflect.Method;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Properties;

/**
 * Utility class that gives access to the execution environment of the JVM, like the executing user,
 * startup options, or the JVM version.
 */
public class EnvironmentInformation {
    @VisibleForTesting public static final String UNKNOWN_COMMIT_ID = "DecafC0ffeeD0d0F00d";
    @VisibleForTesting public static final String UNKNOWN_COMMIT_ID_ABBREV = "DeadD0d0";

    private static final Logger LOG = LoggerFactory.getLogger(EnvironmentInformation.class);

    public static final String UNKNOWN = "<unknown>";

    /**
     * Returns the version of the code as String.
     *
     * @return The project version string.
     */
    public static String getVersion() {
        return getVersionsInstance().projectVersion;
    }

    /**
     * Returns the version of the used Scala compiler as String.
     *
     * @return The scala version string.
     */
    public static String getScalaVersion() {
        return getVersionsInstance().scalaVersion;
    }

    /** @return The Instant this version of the software was built. */
    public static Instant getBuildTime() {
        return getVersionsInstance().gitBuildTime;
    }

    /**
     * @return The Instant this version of the software was built as a String using the
     *     Europe/Berlin timezone.
     */
    public static String getBuildTimeString() {
        return getVersionsInstance().gitBuildTimeStr;
    }

    /** @return The last known commit id of this version of the software. */
    public static String getGitCommitId() {
        return getVersionsInstance().gitCommitId;
    }

    /** @return The last known abbreviated commit id of this version of the software. */
    public static String getGitCommitIdAbbrev() {
        return getVersionsInstance().gitCommitIdAbbrev;
    }

    /** @return The Instant of the last commit of this code. */
    public static Instant getGitCommitTime() {
        return getVersionsInstance().gitCommitTime;
    }

    /**
     * @return The Instant of the last commit of this code as a String using the Europe/Berlin
     *     timezone.
     */
    public static String getGitCommitTimeString() {
        return getVersionsInstance().gitCommitTimeStr;
    }

    /**
     * Returns the code revision (commit and commit date) of Flink, as generated by the Maven
     * builds.
     *
     * @return The code revision.
     */
    public static RevisionInformation getRevisionInformation() {
        return new RevisionInformation(getGitCommitIdAbbrev(), getGitCommitTimeString());
    }

    private static final class Versions {
        private static final Instant DEFAULT_TIME_INSTANT = Instant.EPOCH;
        private static final String DEFAULT_TIME_STRING = "1970-01-01T00:00:00+0000";
        private String projectVersion = UNKNOWN;
        private String scalaVersion = UNKNOWN;
        private Instant gitBuildTime = DEFAULT_TIME_INSTANT;
        private String gitBuildTimeStr = DEFAULT_TIME_STRING;
        private String gitCommitId = UNKNOWN_COMMIT_ID;
        private String gitCommitIdAbbrev = UNKNOWN_COMMIT_ID_ABBREV;
        private Instant gitCommitTime = DEFAULT_TIME_INSTANT;
        private String gitCommitTimeStr = DEFAULT_TIME_STRING;

        private static final String PROP_FILE = ".flink-runtime.version.properties";

        private static final String FAIL_MESSAGE =
                "The file "
                        + PROP_FILE
                        + " has not been generated correctly. You MUST run 'mvn generate-sources' in the flink-runtime module.";

        private String getProperty(Properties properties, String key, String defaultValue) {
            String value = properties.getProperty(key);
            if (value == null || value.charAt(0) == '$') {
                return defaultValue;
            }
            return value;
        }

        public Versions() {
            ClassLoader classLoader = EnvironmentInformation.class.getClassLoader();
            try (InputStream propFile = classLoader.getResourceAsStream(PROP_FILE)) {
                if (propFile != null) {
                    Properties properties = new Properties();
                    properties.load(propFile);

                    projectVersion = getProperty(properties, "project.version", UNKNOWN);
                    scalaVersion = getProperty(properties, "scala.binary.version", UNKNOWN);

                    gitCommitId = getProperty(properties, "git.commit.id", UNKNOWN_COMMIT_ID);
                    gitCommitIdAbbrev =
                            getProperty(
                                    properties, "git.commit.id.abbrev", UNKNOWN_COMMIT_ID_ABBREV);

                    // This is to reliably parse the datetime format configured in the
                    // git-commit-id-plugin
                    DateTimeFormatter gitDateTimeFormatter =
                            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

                    // Default format is in Berlin timezone because that is where Flink originated.
                    DateTimeFormatter berlinDateTime =
                            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(
                                    ZoneId.of("Europe/Berlin"));

                    try {
                        String propGitCommitTime =
                                getProperty(properties, "git.commit.time", DEFAULT_TIME_STRING);
                        gitCommitTime =
                                gitDateTimeFormatter.parse(propGitCommitTime, Instant::from);
                        gitCommitTimeStr = berlinDateTime.format(gitCommitTime);

                        String propGitBuildTime =
                                getProperty(properties, "git.build.time", DEFAULT_TIME_STRING);
                        gitBuildTime = gitDateTimeFormatter.parse(propGitBuildTime, Instant::from);
                        gitBuildTimeStr = berlinDateTime.format(gitBuildTime);
                    } catch (DateTimeParseException dtpe) {
                        LOG.error("{} : {}", FAIL_MESSAGE, dtpe);
                        throw new IllegalStateException(FAIL_MESSAGE);
                    }
                }
            } catch (IOException ioe) {
                LOG.info(
                        "Cannot determine code revision: Unable to read version property file.: {}",
                        ioe.getMessage());
            }
        }
    }

    private static final class VersionsHolder {
        static final Versions INSTANCE = new Versions();
    }

    private static Versions getVersionsInstance() {
        return VersionsHolder.INSTANCE;
    }

    /**
     * Gets the name of the user that is running the JVM.
     *
     * @return The name of the user that is running the JVM.
     */
    public static String getHadoopUser() {
        try {
            Class<?> ugiClass =
                    Class.forName(
                            "org.apache.hadoop.security.UserGroupInformation",
                            false,
                            EnvironmentInformation.class.getClassLoader());

            Method currentUserMethod = ugiClass.getMethod("getCurrentUser");
            Method shortUserNameMethod = ugiClass.getMethod("getShortUserName");
            Object ugi = currentUserMethod.invoke(null);
            return (String) shortUserNameMethod.invoke(ugi);
        } catch (ClassNotFoundException e) {
            return "<no hadoop dependency found>";
        } catch (LinkageError e) {
            // hadoop classes are not in the classpath
            LOG.debug(
                    "Cannot determine user/group information using Hadoop utils. "
                            + "Hadoop classes not loaded or compatible",
                    e);
        } catch (Throwable t) {
            // some other error occurred that we should log and make known
            LOG.warn("Error while accessing user/group information via Hadoop utils.", t);
        }

        return UNKNOWN;
    }

    /**
     * The maximum JVM heap size, in bytes.
     *
     * <p>This method uses the <i>-Xmx</i> value of the JVM, if set. If not set, it returns (as a
     * heuristic) 1/4th of the physical memory size.
     *
     * @return The maximum JVM heap size, in bytes.
     */
    public static long getMaxJvmHeapMemory() {
        final long maxMemory = Runtime.getRuntime().maxMemory();
        if (maxMemory != Long.MAX_VALUE) {
            // we have the proper max memory
            return maxMemory;
        } else {
            // max JVM heap size is not set - use the heuristic to use 1/4th of the physical memory
            final long physicalMemory = Hardware.getSizeOfPhysicalMemory();
            if (physicalMemory != -1) {
                // got proper value for physical memory
                return physicalMemory / 4;
            } else {
                throw new RuntimeException(
                        "Could not determine the amount of free memory.\n"
                                + "Please set the maximum memory for the JVM, e.g. -Xmx512M for 512 megabytes.");
            }
        }
    }

    /**
     * Gets an estimate of the size of the free heap memory.
     *
     * <p>NOTE: This method is heavy-weight. It triggers a garbage collection to reduce
     * fragmentation and get a better estimate at the size of free memory. It is typically more
     * accurate than the plain version {@link #getSizeOfFreeHeapMemory()}.
     *
     * @return An estimate of the size of the free heap memory, in bytes.
     */
    public static long getSizeOfFreeHeapMemoryWithDefrag() {
        // trigger a garbage collection, to reduce fragmentation
        System.gc();

        return getSizeOfFreeHeapMemory();
    }

    /**
     * Gets an estimate of the size of the free heap memory. The estimate may vary, depending on the
     * current level of memory fragmentation and the number of dead objects. For a better (but more
     * heavy-weight) estimate, use {@link #getSizeOfFreeHeapMemoryWithDefrag()}.
     *
     * @return An estimate of the size of the free heap memory, in bytes.
     */
    public static long getSizeOfFreeHeapMemory() {
        Runtime r = Runtime.getRuntime();
        return getMaxJvmHeapMemory() - r.totalMemory() + r.freeMemory();
    }

    /**
     * Gets the version of the JVM in the form "VM_Name - Vendor - Spec/Version".
     *
     * @return The JVM version.
     */
    public static String getJvmVersion() {
        try {
            final RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            return bean.getVmName()
                    + " - "
                    + bean.getVmVendor()
                    + " - "
                    + bean.getSpecVersion()
                    + '/'
                    + bean.getVmVersion();
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    /**
     * Gets the system parameters and environment parameters that were passed to the JVM on startup.
     *
     * @return The options passed to the JVM on startup.
     */
    public static String getJvmStartupOptions() {
        try {
            final RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            final StringBuilder bld = new StringBuilder();

            for (String s : bean.getInputArguments()) {
                bld.append(s).append(' ');
            }

            return bld.toString();
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    /**
     * Gets the system parameters and environment parameters that were passed to the JVM on startup.
     *
     * @return The options passed to the JVM on startup.
     */
    public static String[] getJvmStartupOptionsArray() {
        try {
            RuntimeMXBean bean = ManagementFactory.getRuntimeMXBean();
            List<String> options = bean.getInputArguments();
            return options.toArray(new String[options.size()]);
        } catch (Throwable t) {
            return new String[0];
        }
    }

    /**
     * Gets the directory for temporary files, as returned by the JVM system property
     * "java.io.tmpdir".
     *
     * @return The directory for temporary files.
     */
    public static String getTemporaryFileDirectory() {
        return System.getProperty("java.io.tmpdir");
    }

    /**
     * Tries to retrieve the maximum number of open file handles. This method will only work on
     * UNIX-based operating systems with Sun/Oracle Java versions.
     *
     * <p>If the number of max open file handles cannot be determined, this method returns {@code
     * -1}.
     *
     * @return The limit of open file handles, or {@code -1}, if the limit could not be determined.
     */
    public static long getOpenFileHandlesLimit() {
        if (OperatingSystem
                .isWindows()) { // getMaxFileDescriptorCount method is not available on Windows
            return -1L;
        }
        Class<?> sunBeanClass;
        try {
            sunBeanClass = Class.forName("com.sun.management.UnixOperatingSystemMXBean");
        } catch (ClassNotFoundException e) {
            return -1L;
        }

        try {
            Method fhLimitMethod = sunBeanClass.getMethod("getMaxFileDescriptorCount");
            Object result = fhLimitMethod.invoke(ManagementFactory.getOperatingSystemMXBean());
            return (Long) result;
        } catch (Throwable t) {
            LOG.warn("Unexpected error when accessing file handle limit", t);
            return -1L;
        }
    }

    /**
     * Logs information about the environment, like code revision, current user, Java version, and
     * JVM parameters.
     *
     * @param log The logger to log the information to.
     * @param componentName The component name to mention in the log.
     * @param commandLineArgs The arguments accompanying the starting the component.
     */
    public static void logEnvironmentInfo(
            Logger log, String componentName, String[] commandLineArgs) {
        if (log.isInfoEnabled()) {
            RevisionInformation rev = getRevisionInformation();
            String version = getVersion();
            String scalaVersion = getScalaVersion();

            String jvmVersion = getJvmVersion();
            String[] options = getJvmStartupOptionsArray();

            String javaHome = System.getenv("JAVA_HOME");

            String inheritedLogs = System.getenv("FLINK_INHERITED_LOGS");

            long maxHeapMegabytes = getMaxJvmHeapMemory() >>> 20;

            if (inheritedLogs != null) {
                log.info(
                        "--------------------------------------------------------------------------------");
                log.info(" Preconfiguration: ");
                log.info(inheritedLogs);
            }

            log.info(
                    "--------------------------------------------------------------------------------");
            log.info(
                    " Starting "
                            + componentName
                            + " (Version: "
                            + version
                            + ", Scala: "
                            + scalaVersion
                            + ", "
                            + "Rev:"
                            + rev.commitId
                            + ", "
                            + "Date:"
                            + rev.commitDate
                            + ")");
            log.info(" OS current user: " + System.getProperty("user.name"));
            log.info(" Current Hadoop/Kerberos user: " + getHadoopUser());
            log.info(" JVM: " + jvmVersion);
            log.info(" Maximum heap size: " + maxHeapMegabytes + " MiBytes");
            log.info(" JAVA_HOME: " + (javaHome == null ? "(not set)" : javaHome));

            String hadoopVersionString = getHadoopVersionString();
            if (hadoopVersionString != null) {
                log.info(" Hadoop version: " + hadoopVersionString);
            } else {
                log.info(" No Hadoop Dependency available");
            }

            if (options.length == 0) {
                log.info(" JVM Options: (none)");
            } else {
                log.info(" JVM Options:");
                for (String s : options) {
                    log.info("    " + s);
                }
            }

            if (commandLineArgs == null || commandLineArgs.length == 0) {
                log.info(" Program Arguments: (none)");
            } else {
                log.info(" Program Arguments:");
                for (String s : commandLineArgs) {
                    if (GlobalConfiguration.isSensitive(s)) {
                        log.info(
                                "    "
                                        + GlobalConfiguration.HIDDEN_CONTENT
                                        + " (sensitive information)");
                    } else {
                        log.info("    " + s);
                    }
                }
            }

            log.info(" Classpath: " + System.getProperty("java.class.path"));

            log.info(
                    "--------------------------------------------------------------------------------");
        }
    }

    public static String
    getHadoopVersionString() {
        try {
            Class<?> versionInfoClass =
                    Class.forName(
                            "org.apache.hadoop.util.VersionInfo",
                            false,
                            EnvironmentInformation.class.getClassLoader());
            Method method = versionInfoClass.getMethod("getVersion");
            return (String) method.invoke(null);
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            return null;
        } catch (Throwable e) {
            LOG.error("Cannot invoke VersionInfo.getVersion reflectively.", e);
            return null;
        }
    }

    // --------------------------------------------------------------------------------------------

    /** Don't instantiate this class */
    private EnvironmentInformation() {}

    // --------------------------------------------------------------------------------------------

    /**
     * Revision information encapsulates information about the source code revision of the Flink
     * code.
     */
    public static class RevisionInformation {

        /** The git commit id (hash) */
        public final String commitId;

        /** The git commit date */
        public final String commitDate;

        public RevisionInformation(String commitId, String commitDate) {
            this.commitId = commitId;
            this.commitDate = commitDate;
        }
    }
}
