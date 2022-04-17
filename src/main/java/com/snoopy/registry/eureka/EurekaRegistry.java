package com.snoopy.registry.eureka;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.netflix.appinfo.EurekaInstanceConfig;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.appinfo.providers.EurekaConfigBasedInstanceInfoProvider;
import com.netflix.discovery.*;
import com.snoopy.grpc.base.configure.GrpcRegistryProperties;
import com.snoopy.grpc.base.registry.IRegistry;
import com.snoopy.grpc.base.registry.ISubscribeCallback;
import com.snoopy.grpc.base.registry.RegistryServiceInfo;
import com.snoopy.grpc.base.utils.LoggerBaseUtil;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

import static com.snoopy.registry.eureka.EurekaRegistryProvider.EUREKA_NAMESPACE;
import static com.snoopy.registry.eureka.EurekaRegistryProvider.META_NAME;

/**
 * @author :   kehanjiang
 * @date :   2021/12/1  15:18
 */
public class EurekaRegistry implements IRegistry {
    private SnoopyApplicationInfoManager snoopyApplicationInfoManager;
    private EurekaClient eurekaClient;
    private Map<String, EurekaEventListener> listenerMap = new HashMap<>();

    private final ReentrantLock reentrantLock = new ReentrantLock();

    public EurekaRegistry(GrpcRegistryProperties grpcRegistryProperties) {
        EurekaInstanceConfig instanceConfig = new MyDataCenterInstanceConfig(EUREKA_NAMESPACE);
        InstanceInfo instanceInfo = new EurekaConfigBasedInstanceInfoProvider(instanceConfig).get();
        instanceInfo.setStatus(InstanceInfo.InstanceStatus.UP);
        snoopyApplicationInfoManager = new SnoopyApplicationInfoManager(instanceConfig, instanceInfo);
        eurekaClient = new DiscoveryClient(snoopyApplicationInfoManager, new DefaultEurekaClientConfig());
    }


    @Override
    public void subscribe(RegistryServiceInfo serviceInfo, ISubscribeCallback subscribeCallback) {
        reentrantLock.lock();
        try {
            notifyChange(serviceInfo, subscribeCallback);
            EurekaEventListener eurekaEventListener = listenerMap.get(serviceInfo.getPath());
            if (eurekaEventListener == null) {
                eurekaEventListener = new EurekaEventListener() {
                    @Override
                    public void onEvent(EurekaEvent event) {
                        if (event instanceof CacheRefreshedEvent) {
                            notifyChange(serviceInfo, subscribeCallback);
                        }
                    }
                };
                eurekaClient.registerEventListener(eurekaEventListener);
                listenerMap.put(serviceInfo.getPath(), eurekaEventListener);
            }
        } catch (Throwable e) {
            throw new RuntimeException("[" + serviceInfo.getPath() + "] subscribe failed !", e);
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public void unsubscribe(RegistryServiceInfo serviceInfo) {
        reentrantLock.lock();
        try {
            EurekaEventListener eurekaEventListener = listenerMap.get(serviceInfo.getPath());
            if (eurekaEventListener != null) {
                eurekaClient.unregisterEventListener(eurekaEventListener);
                listenerMap.remove(serviceInfo.getPath());
            }
        } catch (Throwable e) {
            throw new RuntimeException("[" + serviceInfo.getPath() + "] unsubscribe failed !", e);
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public void register(RegistryServiceInfo serviceInfo) {
        reentrantLock.lock();
        try {
            if (!snoopyApplicationInfoManager.isRegistry(serviceInfo)) {
                unregister(serviceInfo);
                snoopyApplicationInfoManager.registry(serviceInfo);
            }
        } catch (Throwable e) {
            throw new RuntimeException("[" + serviceInfo.getPath() + "] register failed !", e);
        } finally {
            reentrantLock.unlock();
        }
    }

    @Override
    public void unregister(RegistryServiceInfo serviceInfo) {
        reentrantLock.lock();
        try {
            snoopyApplicationInfoManager.unregister(serviceInfo);
        } catch (Throwable e) {
            throw new RuntimeException("[" + serviceInfo.getPath() + "] register failed !", e);
        } finally {
            reentrantLock.unlock();
        }
    }

    private void notifyChange(RegistryServiceInfo serviceInfo, ISubscribeCallback subscribeCallback) {
        ConcurrentMap<String, List<RegistryServiceInfo>> newServices = new ConcurrentHashMap<String,
                List<RegistryServiceInfo>>();
        eurekaClient.getApplications().getRegisteredApplications().stream()
                .flatMap(app -> app.getInstances().stream())
                .filter(instanceInfo -> EurekaRegistryProvider.APP_GROUP.equalsIgnoreCase(instanceInfo.getAppGroupName()))
                .map(instanceInfo -> {
                    String metaVal = instanceInfo.getMetadata().get(META_NAME);
                    if (metaVal != null) {
                        try {
                            Gson gson = new Gson();
                            return gson.fromJson(metaVal, new TypeToken<Map<String, String>>() {
                            }.getType());
                        } catch (Exception e) {
                            LoggerBaseUtil.warn(this, "Failed to pasre metadata:{}", metaVal);
                        }
                    }
                    return (Map<String, String>) null;
                })
                .filter(Objects::nonNull)
                .flatMap(map -> map.entrySet().stream())
                .forEach(entry ->
                        newServices.compute(entry.getKey(),
                                (key, oldVal) -> {
                                    List<RegistryServiceInfo> list = oldVal;
                                    if (list == null) {
                                        list = new ArrayList<>();
                                    }
                                    list.add(new RegistryServiceInfo(entry.getValue()));
                                    return list;
                                })
                );
        List<RegistryServiceInfo> registryServiceInfoList = newServices.get(serviceInfo.getPath());
        if (registryServiceInfoList != null) {
            subscribeCallback.handle(registryServiceInfoList);
        }
    }

    @Override
    public void close() throws IOException {
        listenerMap.clear();
        if (eurekaClient != null) {
            eurekaClient.shutdown();
        }
    }
}
