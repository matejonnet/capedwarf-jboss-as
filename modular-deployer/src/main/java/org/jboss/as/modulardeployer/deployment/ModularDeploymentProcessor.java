package org.jboss.as.modulardeployer.deployment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.jboss.as.ee.structure.Attachments.EAR_METADATA;

import org.jboss.as.controller.OperationFailedException;
import org.jboss.as.controller.PathAddress;
import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.client.ModelControllerClient;
import org.jboss.as.controller.client.OperationBuilder;
import org.jboss.as.controller.client.helpers.Operations;
import org.jboss.as.controller.client.helpers.ClientConstants;
import org.jboss.as.server.deployment.Attachments;
import org.jboss.as.server.deployment.DeploymentPhaseContext;
import org.jboss.as.server.deployment.DeploymentUnit;
import org.jboss.as.server.deployment.DeploymentUnitProcessingException;
import org.jboss.as.server.deployment.DeploymentUnitProcessor;
import org.jboss.as.server.deployment.module.ResourceRoot;
import org.jboss.dmr.ModelNode;
import org.jboss.metadata.ear.spec.EarMetaData;
import org.jboss.metadata.ear.spec.ModuleMetaData;
import org.jboss.metadata.ear.spec.ModulesMetaData;
import org.jboss.vfs.TempDir;
import org.jboss.vfs.VirtualFile;

public class ModularDeploymentProcessor implements DeploymentUnitProcessor {

    private final ModelControllerClient client;
    private final TempDir tempDir;
    private final AtomicInteger portOffset;
    
    public ModularDeploymentProcessor(TempDir tempDir) {
        this.tempDir = tempDir;
        try {
            client = ModelControllerClient.Factory.create(InetAddress.getByName("127.0.0.1"), 9999);
        } catch (UnknownHostException e) {
            throw new RuntimeException("Cannot connect to domain controller.", e);
        }
        portOffset = new AtomicInteger();
    }
    
    @Override
    public void deploy(DeploymentPhaseContext phaseContext) throws DeploymentUnitProcessingException {

        List<Module> modules = new ArrayList<>();

        DeploymentUnit unit = phaseContext.getDeploymentUnit();
        ResourceRoot deploymentRoot = unit.getAttachment(Attachments.DEPLOYMENT_ROOT);
        VirtualFile root = deploymentRoot.getRoot();
        EarMetaData emd = unit.getAttachment(EAR_METADATA);
        //TODO validate not null emd, deploymentRoot
        validateArchive(emd, root);
        
        modules = extractWars(emd, root);
        mergeLib(modules, emd, root);
        
        deployModules(modules);
    }

    @Override
    public void undeploy(DeploymentUnit context) {
        // TODO Auto-generated method stub
    }

    private void validateArchive(EarMetaData emd, VirtualFile root) throws DeploymentUnitProcessingException {
        VirtualFile appEngineApp = root.getChild("META-INF/appengine-application.xml");
        if (!appEngineApp.isFile()) {
            throw new DeploymentUnitProcessingException("Missing META-INF/appengine-application.xml.");
        }
    }

    private List<Module> extractWars(EarMetaData emd, VirtualFile root) throws DeploymentUnitProcessingException {
        List<Module> modules = new ArrayList<>();
        ModulesMetaData earModules = emd.getModules();
        Iterator<ModuleMetaData> it = earModules.iterator();
        while (it.hasNext()) {
            ModuleMetaData moduleMetaData = it.next();
            if (!moduleMetaData.getType().equals(ModuleMetaData.ModuleType.Web)) {
                throw new DeploymentUnitProcessingException("Only web modules are allowed.");
            }
            
            String moduleName = moduleMetaData.getFileName();
            VirtualFile war = root.getChild(moduleName);
            try {
                modules.add(new Module(tempDir, war, root.getName() + "-" + war.getName()));
            } catch (IOException e) {
                throw new DeploymentUnitProcessingException("Cannot add module.", e);
            }
        }
        return modules;
    }

    private void mergeLib(List<Module> modules, EarMetaData emd, VirtualFile root) throws DeploymentUnitProcessingException {
        VirtualFile libFolder = root.getChild(emd.getLibraryDirectory());
        for (Module module : modules) {
            try {
                module.addLibs(libFolder.getChildren());
            } catch (IOException e) {
                throw new DeploymentUnitProcessingException("Cannot add merge module libs.", e);
            }
        }
    }

    private void deployModules(List<Module> modules) throws DeploymentUnitProcessingException {
        for (Module module : modules) {
            boolean deployed = false;
            deployed = deployModule(module);
            if (deployed) {
                module.markDeployed();
            } else {
                //TODO rollback(modules);
                throw new DeploymentUnitProcessingException("Failed to deploy module " + module.name() + "");
            }
        }
        
//TODO        for (Module module : modules) {
//            boolean started = startModules();
//            if (started) {
//                module.markStarterd();
//            }
//        }
    }

    private boolean deployModule(Module module) throws DeploymentUnitProcessingException {
        boolean sgCreated = createServerGroup(module.name());
        boolean serverAdded = false;
        boolean deployed = false;
        
        //TODO host selection
        String host = "master";
        
        if (sgCreated) {
            serverAdded = addServerToGroup(module.name(), host);
        }
        if (serverAdded) {
            try {
                deployed = deployToServerGroup(module);
            } catch (IOException e) {
                throw new DeploymentUnitProcessingException("Cannot deploy to server group.", e);
            }
        }
        return deployed;
    }

    private boolean addServerToGroup(String name, String host) throws DeploymentUnitProcessingException {
        // /host=master/server-config=server-01:add(auto-start=, group=)
        PathAddress address = PathAddress.pathAddress(
                PathElement.pathElement(ClientConstants.HOST, host),
                PathElement.pathElement(ClientConstants.SERVER_CONFIG, name));

        ModelNode add = Operations.createAddOperation(address.toModelNode());
        add.get(ClientConstants.GROUP).set(name);
        add.get(ClientConstants.AUTO_START).set(true);
        
        try {
            ModelNode outcome = client.execute(add);
            return Operations.isSuccessfulOutcome(outcome);
        } catch (IOException e) {
            throw new DeploymentUnitProcessingException("Cannot create server group.", e);
        }
    }

    private boolean createServerGroup(String name) throws DeploymentUnitProcessingException {
        // /server-group=newg:add(profile=default,socket-binding-group=standard-sockets,socket-binding-port-offset=)
        PathAddress address = PathAddress.pathAddress(PathElement.pathElement(ClientConstants.SERVER_GROUP, name));

        ModelNode add = Operations.createAddOperation(address.toModelNode());
        add.get("profile").set("default");
        add.get(ClientConstants.SOCKET_BINDING_GROUP).set("standard-sockets");
        add.get("socket-binding-port-offset").set(getPortOffset());
        
        try {
            ModelNode outcome = client.execute(add);
            return Operations.isSuccessfulOutcome(outcome);
        } catch (IOException e) {
            throw new DeploymentUnitProcessingException("Cannot create server group.", e);
        }
    }

    private int getPortOffset() {
        return portOffset.getAndAdd(100);
    }

    private ModelNode deployOperation(String name) {
        /*
        {
            "operation" => "composite",
            "address" => [],
            "steps" => [
                {
                    "operation" => "add",
                    "address" => [
                        ("server-group" => "main-server-group"),
                        ("deployment" => "hello-servlet-noEJBnoDist.war")
                    ]
                },
                {
                    "operation" => "deploy",
                    "address" => [
                        ("server-group" => "main-server-group"),
                        ("deployment" => "hello-servlet-noEJBnoDist.war")
                    ]
                }
            ]
        }
        */

        PathAddress address = PathAddress.pathAddress(
                PathElement.pathElement(ClientConstants.SERVER_GROUP, name),
                PathElement.pathElement(ClientConstants.DEPLOYMENT, name));
        ModelNode add = Operations.createAddOperation(address.toModelNode());
        ModelNode deploy = Operations.createOperation(ClientConstants.DEPLOYMENT_DEPLOY_OPERATION, address.toModelNode());
        
        ModelNode composite = Operations.createCompositeOperation();
        ModelNode steps = composite.get(ClientConstants.STEPS);
        steps.get(ClientConstants.STEPS).add(add);
        steps.get(ClientConstants.STEPS).add(deploy);
        
        return composite;
    }
    
    private boolean deployToServerGroup(Module module) throws IOException {
        /*
        "operation" => "composite",
        "address" => [],
        "steps" => [
        */
        ModelNode deploy = Operations.createCompositeOperation();
        ModelNode steps = deploy.get(ClientConstants.STEPS);
        addStreamToOperation(steps, module);
        steps.add(deployOperation(module.name()));
        
        ModelNode outcome = client.execute(deploy);
        return Operations.isSuccessfulOutcome(outcome);
    }

    private void addStreamToOperation(ModelNode steps, Module module) throws FileNotFoundException {
        /*{
        "steps" => [
            {
                "operation" => "add",
                "address" => {"deployment" => "hello-servlet-noEJBnoDist.war"},
                "content" => [{"input-stream-index" => 0}]
            },
        ]
        }*/

        PathAddress address = PathAddress.pathAddress(PathElement.pathElement(ClientConstants.DEPLOYMENT, module.name()));
        
        ModelNode add = Operations.createAddOperation(address.toModelNode());

        OperationBuilder builder = OperationBuilder.create(steps, true);
        builder.addInputStream(module.getInputStream());

        add.get(ClientConstants.CONTENT).get(0).get(ClientConstants.INPUT_STREAM_INDEX).set(0);
        
        steps.add(builder.build().getOperation());
    }
}
