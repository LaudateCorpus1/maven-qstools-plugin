/*
 * JBoss, Home of Professional Open Source
 * Copyright 2013, Red Hat, Inc. and/or its affiliates, and individual
 * contributors by the @authors tag. See the copyright.txt in the 
 * distribution for a full listing of individual contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,  
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jboss.maven.plugins.qstools;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.context.Context;
import org.jboss.maven.plugins.qstools.config.ConfigurationProvider;
import org.jboss.maven.plugins.qstools.xml.PositionalXMLReader;
import org.w3c.dom.Document;

public abstract class AbstractProjectWalker implements QSChecker, QSFixer {

    private enum WalkType {
        FIX,
        CHECK
    };

    @Requirement
    private Context context;

    @Requirement
    private DependencyProvider dependencyProvider;

    @Requirement
    private ConfigurationProvider configurationProvider;

    private XPath xPath = XPathFactory.newInstance().newXPath();

    private Log log;

    private MavenSession mavenSession;

    private int violationsQtd;

    /*
     * (non-Javadoc)
     * 
     * @see org.jboss.maven.plugins.qstools.QSChecker#getViolatonsQtd()
     */
    @Override
    public int getViolatonsQtd() {
        return violationsQtd;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jboss.maven.plugins.qstools.QSChecker#resetViolationsQtd()
     */
    @Override
    public void resetViolationsQtd() {
        violationsQtd = 0;
    }

    @Override
    public Map<String, List<Violation>> check(MavenProject project, MavenSession mavenSession,
        List<MavenProject> reactorProjects, Log log) throws QSCheckerException {
        Map<String, List<Violation>> results = new TreeMap<String, List<Violation>>();

        // iterate over all reactor projects
        walk(WalkType.CHECK, project, mavenSession, reactorProjects, log, results);

        if (violationsQtd > 0) {
            log.info("There are " + violationsQtd + " checkers violations");
        }
        return results;
    }

    @Override
    public void fix(MavenProject project, MavenSession mavenSession, List<MavenProject> reactorProjects, Log log)
        throws QSCheckerException {
        walk(WalkType.FIX, project, mavenSession, reactorProjects, log, null);
    }

    @SuppressWarnings("unchecked")
    public void walk(WalkType walkType, MavenProject project, MavenSession mavenSession, List<MavenProject> reactorProjects,
        Log log, Map<String, List<Violation>> results) throws QSCheckerException {
        this.mavenSession = mavenSession;
        this.log = log;
        try {
            List<String> ignoredQuickstarts = (List<String>) context.get(Constants.IGNORED_QUICKSTARTS_CONTEXT);
            for (MavenProject mavenProject : reactorProjects) {
                if (!ignoredQuickstarts.contains(mavenProject.getBasedir().getName())) {
                    Document doc = PositionalXMLReader.readXML(new FileInputStream(mavenProject.getFile()));
                    if (configurationProvider.getQuickstartsRules(project.getGroupId()).isCheckerIgnored(this)) {
                        String msg = "Skiping %s for %s:%s";
                        log.warn(String.format(msg,
                            this.getClass().getSimpleName(),
                            mavenProject.getGroupId(),
                            mavenProject.getArtifactId()));
                    } else {
                        switch (walkType) {
                            case CHECK:
                                checkProject(mavenProject, doc, results);
                                break;
                            case FIX:
                                fixProject(mavenProject, doc);
                                break;
                            default:
                                break;
                        }

                    }
                } else {
                    log.debug("Ignoring " + mavenProject.getBasedir().getName() + ". It is listed on .quickstarts_ignore file");
                }
            }
        } catch (Exception e) {
            // Any other exception is a problem.
            throw new QSCheckerException(e);
        }
    }

    /**
     * Adds violation referencing the pom.xml file as the violated file
     * 
     * @param file the file where the violation happened
     * 
     * @param results the list of results
     * 
     * @param lineNumber the line number where the violation happened
     * 
     * @param violationMessage the violation message to be added
     * 
     */
    protected void addViolation(final File file, final Map<String, List<Violation>> results, int lineNumber,
        String violationMessage) {
        // Get relative path based on maven work dir
        String rootDirectory = (mavenSession.getExecutionRootDirectory() + File.separator).replace("\\", "\\\\");
        String fileAsString = file.getAbsolutePath().replace(rootDirectory, "");
        if (results.get(fileAsString) == null) {
            results.put(fileAsString, new ArrayList<Violation>());
        }
        results.get(fileAsString).add(new Violation(getClass(), lineNumber, violationMessage));
        violationsQtd++;
    }

    public abstract void checkProject(final MavenProject project, Document doc, final Map<String, List<Violation>> results)
        throws Exception;

    public abstract void fixProject(final MavenProject project, Document doc) throws Exception;

    /**
     * @return the dependencyProvider
     */
    protected DependencyProvider getDependencyProvider() {
        return dependencyProvider;
    }

    /**
     * @return the xPath
     */
    protected XPath getxPath() {
        return xPath;
    }

    /**
     * @return the log
     */
    protected Log getLog() {
        return log;
    }

    /**
     * @return the mavenSession
     */
    protected MavenSession getMavenSession() {
        return mavenSession;
    }

    /**
     * @return the context
     */
    protected Context getContext() {
        return context;
    }

    /**
     * @return the configurationProvider
     */
    protected ConfigurationProvider getConfigurationProvider() {
        return configurationProvider;
    }

}
