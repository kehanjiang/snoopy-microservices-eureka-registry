package com.snoopy.registry.eureka;

import com.google.gson.Gson;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.snoopy.grpc.base.registry.RegistryServiceInfo;
import com.snoopy.grpc.base.utils.LoggerBaseUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import static com.netflix.appinfo.InstanceInfo.DEFAULT_PORT;
import static com.snoopy.registry.eureka.EurekaRegistryProvider.META_NAME;

/**
 * @author :   kehanjiang
 * @date :   2022/4/10  15:56
 */
public class SnoopyApplicationInfoManager extends ApplicationInfoManager {
    private ConcurrentMap<String, String> registerServices = new ConcurrentHashMap<>();

    public SnoopyApplicationInfoManager(EurekaInstanceConfig config, InstanceInfo instanceInfo, ApplicationInfoManager.OptionalArgs optionalArgs) {
        super(config, instanceInfo, optionalArgs);
    }

    public SnoopyApplicationInfoManager(EurekaInstanceConfig config, ApplicationInfoManager.OptionalArgs optionalArgs) {
        super(config, optionalArgs);
    }

    public SnoopyApplicationInfoManager(EurekaInstanceConfig config, InstanceInfo instanceInfo) {
        super(config, instanceInfo);
    }

    public void registry(RegistryServiceInfo registryServiceInfo) {
        registerServices.put(registryServiceInfo.getPath(), registryServiceInfo.generateData());
        String newIp = registryServiceInfo.getHost();
        InstanceInfo.Builder builder = new InstanceInfo.Builder(getInfo());
        if (newIp != null && !newIp.equals(getInfo().getIPAddr()) || getInfo().getPort() == DEFAULT_PORT) {
            LoggerBaseUtil.info(this, "The address changed from : {}:{} => {}:{}", getInfo().getIPAddr(), getInfo().getPort(), newIp,
                    registryServiceInfo.getPort());
            if (newIp != null) {
                builder.setIPAddr(newIp);
            }
            if (registryServiceInfo.getPort() > 0) {
                builder.setPort(registryServiceInfo.getPort());
            }
        }
        updateServiceMeta();
    }

    public boolean isRegistry(RegistryServiceInfo registryServiceInfo) {
        return registerServices.containsKey(registryServiceInfo.getPath());
    }

    private void updateServiceMeta() {
        Map<String, String> metaData = new HashMap<>();
        Gson gson = new Gson();
        metaData.put(META_NAME, gson.toJson(registerServices));
        registerAppMetadata(metaData);
    }

    public void unregister(RegistryServiceInfo registryServiceInfo) {
        if (registerServices.remove(registryServiceInfo.getPath()) != null) {
            updateServiceMeta();
        }
    }
}

