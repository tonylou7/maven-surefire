package org.apache.maven.plugin.surefire;

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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import java.io.File;
import java.util.List;
import java.util.Properties;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.surefire.suite.RunResult;
import org.codehaus.plexus.util.StringUtils;

/**
 * Run tests using Surefire.
 *
 * @author Jason van Zyl
 * @version $Id$
 * @noinspection JavaDoc
 */
@Mojo( name = "test", defaultPhase = LifecyclePhase.TEST, threadSafe = true,
       requiresDependencyResolution = ResolutionScope.TEST )
public class SurefirePlugin
    extends AbstractSurefireMojo
    implements SurefireReportParameters
{

    /**
     * Set this to "true" to ignore a failure during testing. Its use is NOT RECOMMENDED, but quite convenient on
     * occasion.
     */
    @Parameter( property = "maven.test.failure.ignore", defaultValue = "false" )
    private boolean testFailureIgnore;

    /**
     * Base directory where all reports are written to.
     */
    @Parameter( defaultValue = "${project.build.directory}/surefire-reports" )
    private File reportsDirectory;

    /**
     * Specify this parameter to run individual tests by file name, overriding the <code>includes/excludes</code>
     * parameters. Each pattern you specify here will be used to create an include pattern formatted like
     * <code>**&#47;${test}.java</code>, so you can just type "-Dtest=MyTest" to run a single test called
     * "foo/MyTest.java".<br/>
     * This parameter overrides the <code>includes/excludes</code> parameters, and the TestNG <code>suiteXmlFiles</code>
     * parameter.
     * <p/>
     * Since 2.7.3, you can execute a limited number of methods in the test by adding #myMethod or #my*ethod. For example,
     * "-Dtest=MyTest#myMethod".  This is supported for junit 4.x and testNg.
     */
    @Parameter( property = "test" )
    private String test;

    /**
     * Option to print summary of test suites or just print the test cases that have errors.
     */
    @Parameter( property = "surefire.printSummary", defaultValue = "true" )
    private boolean printSummary;

    /**
     * Selects the formatting for the test report to be generated. Can be set as "brief" or "plain".
     */
    @Parameter( property = "surefire.reportFormat", defaultValue = "brief" )
    private String reportFormat;

    /**
     * Option to generate a file test report or just output the test report to the console.
     */
    @Parameter( property = "surefire.useFile", defaultValue = "true" )
    private boolean useFile;


    /**
     * Set this to "true" to cause a failure if the none of the tests specified in -Dtest=... are run. Defaults to
     * "true".
     *
     * @since 2.12
     */
    @Parameter( property = "surefire.failIfNoSpecifiedTests" )
    private Boolean failIfNoSpecifiedTests;

    /**
     * Attach a debugger to the forked JVM. If set to "true", the process will suspend and wait for a debugger to attach
     * on port 5005. If set to some other string, that string will be appended to the argLine, allowing you to configure
     * arbitrary debuggability options (without overwriting the other options specified through the <code>argLine</code>
     * parameter).
     *
     * @since 2.4
     */
    @Parameter( property = "maven.surefire.debug" )
    private String debugForkedProcess;

    /**
     * Kill the forked test process after a certain number of seconds. If set to 0, wait forever for the process, never
     * timing out.
     *
     * @since 2.4
     */
    @Parameter( property = "surefire.timeout" )
    private int forkedProcessTimeoutInSeconds;

    private Properties originalSystemProperties;

    /**
     * systemPropertyVariables + systemProperties
     */
    private Properties internalSystemProperties = new Properties();

    /**
     * Option to pass dependencies to the system's classloader instead of using an isolated class loader when forking.
     * Prevents problems with JDKs which implement the service provider lookup mechanism by using the system's
     * classloader.
     *
     * @since 2.3
     */
    @Parameter( property = "surefire.useSystemClassLoader", defaultValue = "true" )
    private boolean useSystemClassLoader;

    /**
     * By default, Surefire forks your tests using a manifest-only JAR; set this parameter to "false" to force it to
     * launch your tests with a plain old Java classpath. (See
     * http://maven.apache.org/plugins/maven-surefire-plugin/examples/class-loading.html for a more detailed explanation
     * of manifest-only JARs and their benefits.)
     * <p/>
     * Beware, setting this to "false" may cause your tests to fail on Windows if your classpath is too long.
     *
     * @since 2.4.3
     */
    @Parameter( property = "surefire.useManifestOnlyJar", defaultValue = "true" )
    private boolean useManifestOnlyJar;

    protected void handleSummary( Summary summary )
        throws MojoExecutionException, MojoFailureException
    {
        assertNoException( summary );
        assertNoFailureOrTimeout( summary );
        writeSummary( summary );
    }

    private void assertNoException( Summary summary )
        throws MojoExecutionException
    {
        if ( !summary.isErrorFree() )
        {
            Exception cause = summary.getFirstException();
            throw new MojoExecutionException( cause.getMessage(), cause );
        }
    }

    private void assertNoFailureOrTimeout( Summary summary )
        throws MojoExecutionException
    {
        if ( summary.isFailureOrTimeout() )
        {
            throw new MojoExecutionException( "Failure or timeout" );
        }
    }

    private void writeSummary( Summary summary )
        throws MojoFailureException
    {
        RunResult result = summary.getResultOfLastSuccessfulRun();
        SurefireHelper.reportExecution( this, result, getLog() );
    }

    protected boolean isSkipExecution()
    {
        return isSkip() || isSkipTests() || isSkipExec();
    }

    protected String getPluginName()
    {
        return "surefire";
    }

    protected String[] getDefaultIncludes()
    {
        return new String[]{ "**/Test*.java", "**/*Test.java", "**/*TestCase.java" };
    }

    // now for the implementation of the field accessors

    public boolean isSkipTests()
    {
        return skipTests;
    }

    public void setSkipTests( boolean skipTests )
    {
        this.skipTests = skipTests;
    }

    /**
     * @noinspection deprecation
     */
    public boolean isSkipExec()
    {
        return skipExec;
    }

    /**
     * @noinspection deprecation
     */
    public void setSkipExec( boolean skipExec )
    {
        this.skipExec = skipExec;
    }

    public boolean isSkip()
    {
        return skip;
    }

    public void setSkip( boolean skip )
    {
        this.skip = skip;
    }

    public boolean isTestFailureIgnore()
    {
        return testFailureIgnore;
    }

    public void setTestFailureIgnore( boolean testFailureIgnore )
    {
        this.testFailureIgnore = testFailureIgnore;
    }

    public File getBasedir()
    {
        return basedir;
    }

    public void setBasedir( File basedir )
    {
        this.basedir = basedir;
    }

    public File getTestClassesDirectory()
    {
        return testClassesDirectory;
    }

    public void setTestClassesDirectory( File testClassesDirectory )
    {
        this.testClassesDirectory = testClassesDirectory;
    }

    public File getClassesDirectory()
    {
        return classesDirectory;
    }

    public void setClassesDirectory( File classesDirectory )
    {
        this.classesDirectory = classesDirectory;
    }

    public MavenProject getProject()
    {
        return project;
    }

    public void setProject( MavenProject project )
    {
        this.project = project;
    }

    public List<String> getClasspathDependencyExcludes()
    {
        return classpathDependencyExcludes;
    }

    public void setClasspathDependencyExcludes( List<String> classpathDependencyExcludes )
    {
        this.classpathDependencyExcludes = classpathDependencyExcludes;
    }

    public String getClasspathDependencyScopeExclude()
    {
        return classpathDependencyScopeExclude;
    }

    public void setClasspathDependencyScopeExclude( String classpathDependencyScopeExclude )
    {
        this.classpathDependencyScopeExclude = classpathDependencyScopeExclude;
    }

    public List<String> getAdditionalClasspathElements()
    {
        return additionalClasspathElements;
    }

    public void setAdditionalClasspathElements( List<String> additionalClasspathElements )
    {
        this.additionalClasspathElements = additionalClasspathElements;
    }

    public File getReportsDirectory()
    {
        return reportsDirectory;
    }

    public void setReportsDirectory( File reportsDirectory )
    {
        this.reportsDirectory = reportsDirectory;
    }

    public File getTestSourceDirectory()
    {
        return testSourceDirectory;
    }

    public void setTestSourceDirectory( File testSourceDirectory )
    {
        this.testSourceDirectory = testSourceDirectory;
    }

    public String getTest()
    {
        if ( StringUtils.isBlank( test ) )
        {
            return null;
        }
        String[] testArray = StringUtils.split( test, "," );
        StringBuffer tests = new StringBuffer();
        for ( int i = 0; i < testArray.length; i++ )
        {
            String singleTest = testArray[i];
            int index = singleTest.indexOf( '#' );
            if ( index >= 0 )
            {// the way version 2.7.3.  support single test method
                singleTest = singleTest.substring( 0, index );
            }
            tests.append( singleTest );
            tests.append( "," );
        }
        return tests.toString();
    }

    /**
     * @since 2.7.3
     */
    public String getTestMethod()
    {
        if ( StringUtils.isBlank( test ) )
        {
            return null;
        }
        //modified by rainLee, see http://jira.codehaus.org/browse/SUREFIRE-745
        int index = this.test.indexOf( '#' );
        int index2 = this.test.indexOf( "," );
        if ( index >= 0 )
        {
            if ( index2 < 0 )
            {
                String testStrAfterFirstSharp = this.test.substring( index + 1, this.test.length() );
                if ( testStrAfterFirstSharp.indexOf( "+" ) < 0 )
                {//the original way
                    return testStrAfterFirstSharp;
                }
                else
                {
                    return this.test;
                }
            }
            else
            {
                return this.test;
            }
        }
        return null;
    }

    public Properties getOriginalSystemProperties()
    {
        return originalSystemProperties;
    }

    public void setOriginalSystemProperties( Properties originalSystemProperties )
    {
        this.originalSystemProperties = originalSystemProperties;
    }

    public Properties getInternalSystemProperties()
    {
        return internalSystemProperties;
    }

    public void setInternalSystemProperties( Properties internalSystemProperties )
    {
        this.internalSystemProperties = internalSystemProperties;
    }

    public boolean isUseSystemClassLoader()
    {
        return useSystemClassLoader;
    }

    public void setUseSystemClassLoader( boolean useSystemClassLoader )
    {
        this.useSystemClassLoader = useSystemClassLoader;
    }

    public boolean isUseManifestOnlyJar()
    {
        return useManifestOnlyJar;
    }

    public void setUseManifestOnlyJar( boolean useManifestOnlyJar )
    {
        this.useManifestOnlyJar = useManifestOnlyJar;
    }

    public Boolean getFailIfNoSpecifiedTests()
    {
        return failIfNoSpecifiedTests;
    }

    public void setFailIfNoSpecifiedTests( Boolean failIfNoSpecifiedTests )
    {
        this.failIfNoSpecifiedTests = failIfNoSpecifiedTests;
    }

    public boolean isPrintSummary()
    {
        return printSummary;
    }

    public void setPrintSummary( boolean printSummary )
    {
        this.printSummary = printSummary;
    }

    public String getReportFormat()
    {
        return reportFormat;
    }

    public void setReportFormat( String reportFormat )
    {
        this.reportFormat = reportFormat;
    }

    public boolean isUseFile()
    {
        return useFile;
    }

    public void setUseFile( boolean useFile )
    {
        this.useFile = useFile;
    }

    public String getDebugForkedProcess()
    {
        return debugForkedProcess;
    }

    public void setDebugForkedProcess( String debugForkedProcess )
    {
        this.debugForkedProcess = debugForkedProcess;
    }

    public int getForkedProcessTimeoutInSeconds()
    {
        return forkedProcessTimeoutInSeconds;
    }

    public void setForkedProcessTimeoutInSeconds( int forkedProcessTimeoutInSeconds )
    {
        this.forkedProcessTimeoutInSeconds = forkedProcessTimeoutInSeconds;
    }

    public void setTest( String test )
    {
        this.test = test;
    }


}
