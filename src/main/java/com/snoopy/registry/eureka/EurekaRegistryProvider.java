package com.snoopy.registry.eureka;

import com.google.common.hash.Hashing;
import com.netflix.config.ConfigurationManager;
import com.snoopy.grpc.base.configure.GrpcRegistryProperties;
import com.snoopy.grpc.base.constans.GrpcConstants;
import com.snoopy.grpc.base.registry.IRegistry;
import com.snoopy.grpc.base.registry.IRegistryProvider;
import com.snoopy.grpc.base.utils.NetUtil;
import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

/**
 * @author :   kehanjiang
 * @date :   2021/12/1  15:44
 */
public class EurekaRegistryProvider implements IRegistryProvider {
    public static final String REGISTRY_PROTOCOL_EUREKA = "eureka";
    public static final String EUREKA_NAMESPACE = "eureka";
    public static final String APP_GROUP = "snoopy";
    public static final String DEFAULT_REGION = "default";
    public static final String DEFAULT_ENV = "snoopy";
    public static final String DEFAULT_NAME = "snoopy";
    public static final String META_NAME = "snoopy";

    private static final Map<String, String> params = new HashMap<>();

    static {
        addParam("name");
        addParam("appGroup");
        addParam("asgName");

        addParam("region");

        addParam("port");
        addParam("securePort");
        addParam("port.enabled");
        addParam("securePort.enabled");

        addParam("vipAddress");
        addParam("secureVipAddress");

        addParam("statusPageUrlPath");
        addParam("statusPageUrl");
        addParam("homePageUrlPath");
        addParam("homePageUrl");
        addParam("healthCheckUrlPath");
        addParam("healthCheckUrl");
        addParam("secureHealthCheckUrl");

        addParam("lease.renewalInterval");
        addParam("lease.duration");

        addParam("metadata");

        addParam("defaultAddressResolutionOrder");
        addParam("broadcastPublicIpv4");
        addParam("traffic.enabled");
        addParam("secureHealthCheckUrl");
        params.put("environment", "eureka.environment");

    }

    private static void addParam(String key) {
        params.put(key, encodeEurekaParamName(key));
    }

    private static String encodeEurekaParamName(String key) {
        return EUREKA_NAMESPACE + "." + key;
    }

    @Override
    public IRegistry newRegistryInstance(GrpcRegistryProperties grpcRegistryProperties) {
        Properties properties = new Properties();
        properties.setProperty(encodeEurekaParamName("name"), grpcRegistryProperties.getProperty("application.name", DEFAULT_NAME));
        properties.setProperty(encodeEurekaParamName("appGroup"), APP_GROUP);
        String instanceId= Hashing.murmur3_128()
                .hashString(System.getProperty("user.dir")+ NetUtil.getLocalIpAddress(), StandardCharsets.UTF_8)
                .toString();
        properties.setProperty(encodeEurekaParamName("instanceId"),instanceId);
        properties.setProperty(encodeEurekaParamName("region"), DEFAULT_REGION);
        properties.setProperty("eureka.environment", DEFAULT_ENV);
        properties.setProperty(encodeEurekaParamName("homePageUrl"), "");
        properties.setProperty(encodeEurekaParamName("statusPageUrl"), "");
        properties.setProperty(encodeEurekaParamName("healthCheckUrl"), "");
        StringBuilder sb = new StringBuilder();
        String[] addrs = GrpcConstants.ADDRESS_SPLIT_PATTERN.split(grpcRegistryProperties.getAddress());
        for (int i = 0; i < addrs.length; i++) {
            if (i > 0) {
                sb.append(",");
            }
            if (addrs[i].indexOf('/') < 0) {
                sb.append("http://");
                sb.append(addrs[i]);
                sb.append("/eureka");
            } else {
                sb.append(addrs[i]);
            }
        }
        properties.setProperty(encodeEurekaParamName("serviceUrl.default"), sb.toString());

        for (Map.Entry<String, String> entry : params.entrySet()) {
            String pval = grpcRegistryProperties.getExtra(entry.getKey());
            if (!StringUtils.isEmpty(pval)) {
                properties.setProperty(entry.getValue(), pval);
            }
        }
        ConfigurationManager.loadProperties(properties);
        return new EurekaRegistry(grpcRegistryProperties);
    }

    @Override
    public String registryType() {
        return REGISTRY_PROTOCOL_EUREKA;
    }
}
