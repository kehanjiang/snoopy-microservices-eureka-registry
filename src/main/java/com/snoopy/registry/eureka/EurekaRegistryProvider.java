package com.snoopy.registry.eureka;

import com.snoopy.grpc.base.configure.GrpcRegistryProperties;
import com.snoopy.grpc.base.registry.IRegistry;
import com.snoopy.grpc.base.registry.IRegistryProvider;

/**
 * @author :   kehanjiang
 * @date :   2021/12/1  15:44
 */
public class EurekaRegistryProvider implements IRegistryProvider {
    public static final String REGISTRY_PROTOCOL_EUREKA = "eureka";
    private GrpcRegistryProperties grpcRegistryProperties;

    public EurekaRegistryProvider(GrpcRegistryProperties grpcRegistryProperties) {
        this.grpcRegistryProperties = grpcRegistryProperties;
    }

    @Override
    public IRegistry newRegistryInstance() {
        return new EurekaRegistry(grpcRegistryProperties);
    }

    @Override
    public String registryType() {
        return REGISTRY_PROTOCOL_EUREKA;
    }
}
