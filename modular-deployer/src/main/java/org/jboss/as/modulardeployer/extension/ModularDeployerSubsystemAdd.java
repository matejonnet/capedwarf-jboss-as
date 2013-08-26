/*
 * JBoss, Home of Professional Open Source.
 * Copyright 2011, Red Hat, Inc., and individual contributors
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

package org.jboss.as.modulardeployer.extension;

import java.io.IOException;
import java.util.List;

import org.jboss.as.controller.AbstractBoottimeAddStepHandler;
import org.jboss.as.controller.OperationContext;
import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.ServiceVerificationHandler;
import org.jboss.as.ee.structure.EarInitializationProcessor;
import org.jboss.as.ee.structure.EarMetaDataParsingProcessor;
import org.jboss.as.modulardeployer.deployment.ModularDeploymentProcessor;
import org.jboss.as.server.AbstractDeploymentChainStep;
import org.jboss.as.server.DeploymentProcessorTarget;
import org.jboss.as.server.deployment.Phase;
import org.jboss.as.server.deployment.module.TempFileProviderService;
import org.jboss.dmr.ModelNode;
import org.jboss.msc.service.Service;
import org.jboss.msc.service.ServiceBuilder;
import org.jboss.msc.service.ServiceController;
import org.jboss.msc.service.ServiceName;
import org.jboss.msc.service.ServiceTarget;
import org.jboss.msc.service.StartContext;
import org.jboss.msc.service.StartException;
import org.jboss.msc.service.StopContext;
import org.jboss.vfs.TempDir;
import org.jboss.vfs.VFSUtils;

/**
 * Handler responsible for adding the subsystem resource to the model
 *
 * @author <a href="mailto:mlazar@redhat.com">Matej Lazar</a>
 */
class ModularDeployerSubsystemAdd extends AbstractBoottimeAddStepHandler {

    static final ModularDeployerSubsystemAdd INSTANCE = new ModularDeployerSubsystemAdd();

    private ModularDeployerSubsystemAdd() {
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void populateModel(ModelNode operation, ModelNode model) throws OperationFailedException {
//        ModularDeployerDefinition.APPENGINE_API.validateAndSet(operation, model);
//        ModularDeployerDefinition.ADMIN_TGT.validateAndSet(operation, model);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void performBoottime(final OperationContext context, ModelNode operation, ModelNode model,
                                ServiceVerificationHandler verificationHandler, final List<ServiceController<?>> newControllers)
            throws OperationFailedException {

        context.addStep(new AbstractDeploymentChainStep() {
            
            @Override
            public void execute(DeploymentProcessorTarget processorTarget) {
                final ServiceTarget serviceTarget = context.getServiceTarget();
                final TempDir tempDir = createTempDir(serviceTarget, newControllers);

                final int initialStructureOrder = Math.max(Math.max(Phase.STRUCTURE_WAR, Phase.STRUCTURE_WAR_DEPLOYMENT_INIT), Phase.STRUCTURE_EAR);
                processorTarget.addDeploymentProcessor(ModularDeployerExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, initialStructureOrder + 10, new EarInitializationProcessor());
                processorTarget.addDeploymentProcessor(ModularDeployerExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, initialStructureOrder + 20, new EarMetaDataParsingProcessor());
                processorTarget.addDeploymentProcessor(ModularDeployerExtension.SUBSYSTEM_NAME, Phase.STRUCTURE, initialStructureOrder + 30, new ModularDeploymentProcessor(tempDir));
            }
        }, OperationContext.Stage.RUNTIME);
    }
    
    protected static TempDir createTempDir(final ServiceTarget serviceTarget, final List<ServiceController<?>> newControllers) {
        final TempDir tempDir;
        try {
            tempDir = TempFileProviderService.provider().createTempDir(ModularDeployerExtension.SUBSYSTEM_NAME);
        } catch (IOException e) {
            throw new IllegalArgumentException("Cannot create temp dir for CapeDwarf sub-system!", e);
        }

        final ServiceBuilder<TempDir> builder = serviceTarget.addService(ServiceName.JBOSS.append(ModularDeployerExtension.SUBSYSTEM_NAME).append("tempDir"), new Service<TempDir>() {
            public void start(StartContext context) throws StartException {
            }

            public void stop(StopContext context) {
                VFSUtils.safeClose(tempDir);
            }

            public TempDir getValue() throws IllegalStateException, IllegalArgumentException {
                return tempDir;
            }
        });
        newControllers.add(builder.setInitialMode(ServiceController.Mode.ACTIVE).install());
        return tempDir;
    }
}
