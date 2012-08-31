/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2012, Red Hat, Inc., and individual contributors
 * as indicated by the @author tags. See the copyright.txt file in the
 * distribution for a full listing of individual contributors.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

package org.jboss.jdf.stacks.client;

import java.net.MalformedURLException;
import java.net.URL;

import org.jboss.jdf.stacks.model.Stacks;

/**
 * @author <a href="mailto:benevides@redhat.com">Rafael Benevides</a>
 *
 */
public class Main {

//    private static final Logger log = Logger.getLogger(Main.class.getName());
    
    /**
     * @param args
     * @throws InterruptedException 
     * @throws MalformedURLException 
     */
    public static void main(String[] args) throws InterruptedException, MalformedURLException {
        DefaultStacksClientConfiguration clientConfiguration = new DefaultStacksClientConfiguration();
        clientConfiguration.setUrl(new URL("file:///home/rafael/projetos/jdf/jdf-stack/stacks.yaml"));
        System.out.println("stacks Client criado");
        StacksClient stacksClient = new StacksClient(clientConfiguration);
        System.out.println("Primeiro uso");
        Stacks stacks = stacksClient.getStacks();
        System.out.println("segundo uso");
        stacksClient.getStacks();
        System.out.println("Alterado Refresh");
        stacksClient.getActualConfiguration().setCacheRefreshPeriodInSeconds(-1);
        System.out.println("terceiro uso");
        stacksClient.getStacks();
        System.out.println(stacks.getAvailableRuntimes());

    }

}
