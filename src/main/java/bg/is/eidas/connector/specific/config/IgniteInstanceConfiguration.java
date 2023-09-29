package bg.is.eidas.connector.specific.config;

import bg.is.eidas.connector.specific.config.SpecificConnectorProperties.CacheNames;
import org.apache.ignite.Ignite;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.logger.slf4j.Slf4jLogger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.FileUrlResource;
import org.springframework.util.Assert;

import javax.cache.Cache;
import java.io.File;
import java.io.IOException;

@Configuration
public class IgniteInstanceConfiguration {

    @Lazy
    @Bean
    public Ignite igniteClient(@Value("#{environment.EIDAS_CONFIG_REPOSITORY}/igniteSpecificCommunication.xml") String igniteConfig) throws IOException {
        Assert.isTrue(new File(igniteConfig).exists(), "Required Ignite configuration file not found: " + igniteConfig);
        Ignition.setClientMode(true);
        IgniteConfiguration cfg = Ignition.loadSpringBean(new FileUrlResource(igniteConfig).getInputStream(), "igniteSpecificCommunication.cfg");
        cfg.setIgniteInstanceName(cfg.getIgniteInstanceName() + "Client");
        cfg.setGridLogger(new Slf4jLogger());
        return Ignition.getOrStart(cfg);
    }

    @Lazy
    @Bean("specificNodeConnectorRequestCache")
    public Cache<String, String> specificNodeConnectorRequestCache(Ignite igniteClient) {
        return igniteClient.cache(CacheNames.INCOMING_NODE_REQUESTS_CACHE.getName());
    }

    @Lazy
    @Bean("nodeSpecificConnectorResponseCache")
    public Cache<String, String> nodeSpecificConnectorResponseCache(Ignite igniteClient) {
        return igniteClient.cache(CacheNames.OUTGOING_NODE_RESPONSES_CACHE.getName());
    }

    @Lazy
    @Bean("specificMSSpRequestCorrelationMap")
    public Cache<String, String> specificMSSpRequestCorrelationMap(Ignite igniteClient) {
        return igniteClient.cache(CacheNames.SP_REQUEST_CORRELATION_CACHE.getName());
    }
}
