package org.jboss.as.modulardeployer.deployment;

import java.util.Collections;
import java.util.List;

import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.vfs.VirtualFile;

import com.google.common.collect.Lists;

public class ModulardeploymentProcessor implements DeploymentUnitProcessor {

    
    @Override
    public void deploy(DeploymentPhaseContext phaseContext)
            throws DeploymentUnitProcessingException {
        DeploymentUnit unit = phaseContext.getDeploymentUnit();

        ResourceRoot deploymentRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        VirtualFile root = deploymentRoot.getRoot();


    }

    @Override
    public void undeploy(DeploymentUnit context) {
        // TODO Auto-generated method stub
    }

}
