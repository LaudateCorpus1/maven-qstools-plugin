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
package org.jboss.maven.plugins.qschecker.checkers;

import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.context.Context;
import org.codehaus.plexus.context.ContextException;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.ObjectBasedValueSource;
import org.codehaus.plexus.interpolation.PrefixedValueSourceWrapper;
import org.codehaus.plexus.interpolation.PropertiesBasedValueSource;
import org.codehaus.plexus.interpolation.StringSearchInterpolator;
import org.jboss.jdf.stacks.client.StacksClient;
import org.jboss.jdf.stacks.model.Bom;
import org.jboss.jdf.stacks.model.Stacks;
import org.jboss.maven.plugins.qschecker.QSChecker;
import org.jboss.maven.plugins.qschecker.QSCheckerException;
import org.jboss.maven.plugins.qschecker.Violation;
import org.jboss.maven.plugins.qschecker.maven.MavenDependency;
import org.jboss.maven.plugins.qschecker.xml.PositionalXMLReader;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

@Component(role = QSChecker.class, hint = "bomChecker")
public class BomChecker implements QSChecker {

    private Stacks stacks = new StacksClient().getStacks();

    private MavenSession mavenSession;

    private XPath xPath = XPathFactory.newInstance().newXPath();

    private StringSearchInterpolator interpolator = new StringSearchInterpolator();

    public static final String CONTEXT_BOMVERSION = "recommendedBomVersion";

    @Requirement
    private Context context;

    @Override
    public Map<String, List<Violation>> check(MavenProject project, MavenSession mavenSession, List<MavenProject> reactorProjects, Log log) throws QSCheckerException {
        this.mavenSession = mavenSession;

        Map<String, List<Violation>> results = new TreeMap<String, List<Violation>>();

        try {
            for (MavenProject mavenProject : reactorProjects) {
                processProject(mavenProject, results);
            }
        } catch (Exception e) {
            throw new QSCheckerException(e);
        }
        return results;
    }

    private void processProject(final MavenProject project, final Map<String, List<Violation>> results) throws Exception {
        Document doc = PositionalXMLReader.readXML(new FileInputStream(project.getFile()));
        checkBomVersion(project, doc, results);
        checkDuplicateProperties(project, doc, results);
        checkDuplicateDependencies(project, doc, results);
    }

    /**
     * Check if the POM has duplicate dependencies (managed or not)
     */
    private void checkDuplicateDependencies(MavenProject project, Document doc, Map<String, List<Violation>> results) throws Exception {
        NodeList artifacts = (NodeList) xPath.evaluate("//dependency/artifactId", doc, XPathConstants.NODESET);
        Set<String> declaredArtifacts = new HashSet<String>();
        for (int x = 0; x < artifacts.getLength(); x++) {
            Node artifact = artifacts.item(x);
            String artifactName = artifact.getTextContent();
            int lineNumber = Integer.parseInt((String) artifact.getUserData(PositionalXMLReader.LINE_NUMBER_KEY_NAME));
            if (!declaredArtifacts.add(artifactName)) {
                String msg = "Dependency [%s] is declared more than once";
                addViolation(project, results, new Violation(BomChecker.class, lineNumber, String.format(msg, artifactName)));
            }
        }
    }

    /**
     * Check if the POM has duplicate declared properties
     */
    private void checkDuplicateProperties(MavenProject project, Document doc, Map<String, List<Violation>> results) throws Exception {
        NodeList properties = (NodeList) xPath.evaluate("/project/properties/*", doc, XPathConstants.NODESET);
        Set<String> declaredProperties = new HashSet<String>();
        for (int x = 0; x < properties.getLength(); x++) {
            Node property = properties.item(x);
            String propertyName = property.getNodeName();
            int lineNumber = Integer.parseInt((String) property.getUserData(PositionalXMLReader.LINE_NUMBER_KEY_NAME));
            if (!declaredProperties.add(propertyName)) {
                String msg = "Property [%s] is declared more than once";
                addViolation(project, results, new Violation(BomChecker.class, lineNumber, String.format(msg, propertyName)));
            }
        }
    }

    /**
     * Check if project is using a JDF BOM with recommended Version
     * 
     * @param doc
     */
    private void checkBomVersion(MavenProject project, Document doc, Map<String, List<Violation>> results) throws Exception {
        NodeList dependencies = (NodeList) xPath.evaluate("/project/dependencyManagement/dependencies/dependency", doc, XPathConstants.NODESET);
        List<MavenDependency> managedDependencies = new ArrayList<MavenDependency>();
        // Iterate over all Declared Managed Dependencies
        for (int x = 0; x < dependencies.getLength(); x++) {
            Node dependency = dependencies.item(x);
            MavenDependency mavenDependency = getDependencyFromNode(project, dependency);
            managedDependencies.add(mavenDependency);
            // find if has a jdf bom using stacks
            Bom bomUsed = null;
            for (Bom bom : stacks.getAvailableBoms()) {
                if (bom.getGroupId().equals(mavenDependency.getGroupId()) && bom.getArtifactId().equals(mavenDependency.getArtifactId())) {
                    bomUsed = bom;
                }
            }
            int lineNumber = Integer.parseInt((String) dependency.getUserData(PositionalXMLReader.LINE_NUMBER_KEY_NAME));
            if (bomUsed == null // No JDF Bom used
                    && !mavenDependency.getGroupId().equals("org.jboss.as.quickstarts")) { // Escape internal project
                addViolation(project, results, new Violation(BomChecker.class, lineNumber, mavenDependency + " isn't a JBoss/JDF BOM"));
            } else if (bomUsed != null) {
                // find the recommended BOM version from Context or from Stacks
                String recommendedBomVersion;
                try {
                    recommendedBomVersion = (String) context.get(CONTEXT_BOMVERSION);
                } catch (ContextException e) { // if no context value, it throws a exception
                    recommendedBomVersion = bomUsed.getRecommendedVersion();
                }
                if (!mavenDependency.getVersion().equals(recommendedBomVersion)) {
                    String violationMsg = String.format("BOM %s isn't using the recommended version %s", mavenDependency, recommendedBomVersion);
                    addViolation(project, results, new Violation(BomChecker.class, lineNumber, violationMsg));
                }
            }
        }
    }

    private MavenDependency getDependencyFromNode(MavenProject project, Node dependency) throws InterpolationException {
        String groupId = null;
        String artifactId = null;
        String version = null;
        String type = null;
        String scope = null;
        for (int x = 0; x < dependency.getChildNodes().getLength(); x++) {
            Node node = dependency.getChildNodes().item(x);
            if ("groupId".equals(node.getNodeName())) {
                groupId = node.getTextContent();
            }
            if ("artifactId".equals(node.getNodeName())) {
                artifactId = node.getTextContent();
            }
            if ("version".equals(node.getNodeName())) {
                version = resolveMavenProperty(project, node.getTextContent());
            }
            if ("type".equals(node.getNodeName())) {
                type = node.getTextContent();
            }
            if ("scope".equals(node.getNodeName())) {
                scope = node.getTextContent();
            }
        }
        return new MavenDependency(groupId, artifactId, version, type, scope);
    }

    private String resolveMavenProperty(MavenProject project, String textContent) throws InterpolationException {
        interpolator.clearFeedback(); // Clear the feedback messages from previous interpolate(..) calls.
        // Associate project.model with ${project.*} and ${pom.*} prefixes
        PrefixedValueSourceWrapper modelWrapper = new PrefixedValueSourceWrapper(new ObjectBasedValueSource(project.getModel()), "project.", true);
        interpolator.addValueSource(modelWrapper);
        interpolator.addValueSource(new PropertiesBasedValueSource(project.getModel().getProperties()));
        return interpolator.interpolate(textContent);
    }

    private void addViolation(final MavenProject mavenProject, final Map<String, List<Violation>> results, final Violation violation) {
        // Get relative path based on maven work dir
        String fileAsString = mavenProject.getFile().getAbsolutePath().replaceAll((mavenSession.getExecutionRootDirectory() + "/"), "");
        if (results.get(fileAsString) == null) {
            results.put(fileAsString, new ArrayList<Violation>());
        }
        results.get(fileAsString).add(violation);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.jboss.maven.plugins.qschecker.QSChecker#getCheckerDescription()
     */
    @Override
    public String getCheckerDescription() {
        return "Check and verify if all quickstarts are using the same/latest BOM versions";
    }
}