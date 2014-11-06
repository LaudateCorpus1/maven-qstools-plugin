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
package org.jboss.maven.plugins.qstools.checkers;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.checkstyle.CheckstyleExecutor;
import org.apache.maven.plugin.checkstyle.CheckstyleExecutorRequest;
import org.apache.maven.plugin.checkstyle.CheckstyleResults;
import org.apache.maven.plugin.checkstyle.DefaultCheckstyleExecutor;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Requirement;
import org.jboss.maven.plugins.qstools.QSToolsException;
import org.jboss.maven.plugins.qstools.config.ConfigurationProvider;
import org.jboss.maven.plugins.qstools.config.Rules;

import com.puppycrawl.tools.checkstyle.api.AuditEvent;

public abstract class AbstractCheckstyleChecker implements QSChecker {

    @Requirement
    private ConfigurationProvider configurationProvider;

    private int violationsQtd;

    private String checkerMessage;

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

    @Requirement(role = CheckstyleExecutor.class)
    private DefaultCheckstyleExecutor checkstyleExecutor;

    @Override
    public Map<String, List<Violation>> check(MavenProject project, MavenSession mavenSession, List<MavenProject> reactorProjects, Log log) throws QSToolsException {
        Map<String, List<Violation>> results = new TreeMap<String, List<Violation>>();
        if (configurationProvider.getQuickstartsRules(project.getGroupId()).isCheckerIgnored(this.getClass())) {
            checkerMessage = "This checker is ignored for this groupId in config file.";
        } else {
            CheckstyleExecutorRequest executorRequest = new CheckstyleExecutorRequest();
            Rules rules = configurationProvider.getQuickstartsRules(project.getGroupId());
            try {
                executorRequest
                    .setReactorProjects(reactorProjects)
                    .setSourceDirectory(project.getBasedir())
                    .setTestSourceDirectory(project.getBasedir())
                    .setFailsOnError(false)
                    .setProject(project)
                    .setConfigLocation(getCheckstyleConfig())
                    .setLog(log)
                    .setEncoding("UTF-8")
                    .setHeaderLocation(rules.getHeaderLocation())
                    .setIncludes(getIncludes())
                    .setExcludes(rules.getExcludes() + ", " + rules.getCheckerSpecificExcludes(this));
                CheckstyleResults checkstyleResults = checkstyleExecutor.executeCheckstyle(executorRequest);
                Map<String, List<AuditEvent>> files = checkstyleResults.getFiles();
                for (String file : files.keySet()) {
                    List<AuditEvent> events = files.get(file);
                    // If file has events/violations
                    if (events.size() > 0) {
                        List<Violation> violations = new ArrayList<Violation>();
                        for (AuditEvent event : events) {
                            // Add each checktyle AuditEvent as a new Violation
                            violations.add(new Violation(this.getClass(), event.getLine(), event.getMessage()));
                            violationsQtd++;
                        }
                        results.put(file, violations);
                    }
                }
            } catch (Exception e) {
                throw new QSToolsException(e);
            }
        }
        if (getCheckerMessage() != null) {
            log.info("--> Checker Message: " + getCheckerMessage());
        }
        return results;
    }

    abstract String getIncludes();

    abstract String getCheckstyleConfig();

    @Override
    public String getCheckerMessage() {
        return checkerMessage;
    }

}
