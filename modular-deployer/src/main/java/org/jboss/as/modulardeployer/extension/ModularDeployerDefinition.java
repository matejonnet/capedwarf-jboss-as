package org.jboss.as.modulardeployer.extension;

import java.util.EnumSet;

import org.jboss.as.controller.PathElement;
import org.jboss.as.controller.ReloadRequiredRemoveStepHandler;
import org.jboss.as.controller.ReloadRequiredWriteAttributeHandler;
import org.jboss.as.controller.SimpleAttributeDefinition;
import org.jboss.as.controller.SimpleAttributeDefinitionBuilder;
import org.jboss.as.controller.SimpleResourceDefinition;
import org.jboss.as.controller.descriptions.DefaultResourceAddDescriptionProvider;
import org.jboss.as.controller.descriptions.DefaultResourceRemoveDescriptionProvider;
import org.jboss.as.controller.descriptions.DescriptionProvider;
import org.jboss.as.controller.descriptions.ModelDescriptionConstants;
import org.jboss.as.controller.descriptions.ResourceDescriptionResolver;
import org.jboss.as.controller.operations.validation.StringLengthValidator;
import org.jboss.as.controller.registry.AttributeAccess;
import org.jboss.as.controller.registry.ManagementResourceRegistration;
import org.jboss.as.controller.registry.OperationEntry;
import org.jboss.dmr.ModelType;

import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.ADD;
import static org.jboss.as.controller.descriptions.ModelDescriptionConstants.REMOVE;

/**
 * @author <a href="mailto:mlazar@redhat.com">Matej Lazar</a>
 */
class ModularDeployerDefinition extends SimpleResourceDefinition {
    static final ModularDeployerDefinition INSTANCE = new ModularDeployerDefinition();

//    protected static final SimpleAttributeDefinition ADMIN_TGT =
//            new SimpleAttributeDefinitionBuilder(ModularDeployerModel.ADMIN_AUTH, ModelType.STRING, true)
//                    .setAllowExpression(true)
//                    .setXmlName(ModularDeployerModel.ADMIN_AUTH)
//                    .setFlags(AttributeAccess.Flag.RESTART_ALL_SERVICES)
//                    .build();

    private ModularDeployerDefinition() {
        super(PathElement.pathElement(
                ModelDescriptionConstants.SUBSYSTEM, ModularDeployerExtension.SUBSYSTEM_NAME),
                ModularDeployerExtension.getResourceDescriptionResolver(ModularDeployerExtension.SUBSYSTEM_NAME)
        );
    }

    @Override
    public void registerOperations(final ManagementResourceRegistration rootResourceRegistration) {
        final ResourceDescriptionResolver rootResolver = getResourceDescriptionResolver();
        // Ops to add and remove the root resource
        final ModularDeployerSubsystemAdd subsystemAdd = ModularDeployerSubsystemAdd.INSTANCE;
        final DescriptionProvider subsystemAddDescription = new DefaultResourceAddDescriptionProvider(rootResourceRegistration, rootResolver);
        rootResourceRegistration.registerOperationHandler(ADD, subsystemAdd, subsystemAddDescription, EnumSet.of(OperationEntry.Flag.RESTART_ALL_SERVICES));
        final DescriptionProvider subsystemRemoveDescription = new DefaultResourceRemoveDescriptionProvider(rootResolver);
        rootResourceRegistration.registerOperationHandler(REMOVE, ReloadRequiredRemoveStepHandler.INSTANCE, subsystemRemoveDescription, EnumSet.of(OperationEntry.Flag.RESTART_ALL_SERVICES));
    }

    @Override
    public void registerAttributes(ManagementResourceRegistration resourceRegistration) {
//        resourceRegistration.registerReadWriteAttribute(APPENGINE_API, null, new ReloadRequiredWriteAttributeHandler());
//        resourceRegistration.registerReadWriteAttribute(ADMIN_TGT, null, new ReloadRequiredWriteAttributeHandler());
    }
}
