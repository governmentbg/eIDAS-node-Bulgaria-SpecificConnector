package bg.is.eidas.connector.specific.config;

import bg.is.eidas.connector.specific.config.SpecificConnectorProperties.HsmProperties;
import bg.is.eidas.connector.specific.config.SpecificConnectorProperties.ResponderMetadata;
import bg.is.eidas.connector.specific.config.SpecificConnectorProperties.SupportedAttribute;
import eu.eidas.auth.commons.attribute.AttributeRegistries;
import eu.eidas.auth.commons.attribute.AttributeRegistry;
import eu.eidas.auth.commons.protocol.eidas.spec.LegalPersonSpec;
import eu.eidas.auth.commons.protocol.eidas.spec.NaturalPersonSpec;
import eu.eidas.auth.commons.protocol.eidas.spec.RepresentativeLegalPersonSpec;
import eu.eidas.auth.commons.protocol.eidas.spec.RepresentativeNaturalPersonSpec;
import java.io.File;
import java.nio.file.Files;
import lombok.extern.slf4j.Slf4j;
import org.opensaml.security.x509.BasicX509Credential;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.io.IOException;
import java.security.*;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.util.ResourceUtils;

@Slf4j
@Configuration
public class ResponderMetadataConfiguration {

    @Value("#{environment.SPECIFIC_CONNECTOR_CONFIG_REPOSITORY}")
    private String repo;

    @Value("${javax.net.ssl.trustStore}")
    private String samlKeystore;

    @Bean
    public AttributeRegistry supportedAttributesRegistry(ResponderMetadata responderMetadata) {
        List<SupportedAttribute> supportedAttributes = responderMetadata.getSupportedAttributes();
        AttributeRegistry eidasAttributeRegistry = eidasAttributesRegistry();
        return AttributeRegistries.of(supportedAttributes.stream().map(attr -> eidasAttributeRegistry.getByName(attr.getName())).collect(Collectors.toList()));
    }

    @Bean
    public AttributeRegistry eidasAttributesRegistry() {
        return AttributeRegistries.copyOf(NaturalPersonSpec.REGISTRY, LegalPersonSpec.REGISTRY, RepresentativeNaturalPersonSpec.REGISTRY, RepresentativeLegalPersonSpec.REGISTRY);
    }

    @Bean
    public KeyStore responderMetadataKeyStore(ResponderMetadata responderMetadata, ResourceLoader resourceLoader) throws KeyStoreException,
            IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keystore = KeyStore.getInstance(responderMetadata.getKeyStoreType());
        File storeFile = ResourceUtils.getFile(repo + File.separator + samlKeystore);
        keystore.load(Files.newInputStream(storeFile.toPath()), responderMetadata.getKeyStorePassword().toCharArray());
        return keystore;
    }

    @Bean
    public KeyStore responderMetadataTrustStore(ResponderMetadata responderMetadata) throws KeyStoreException,
            IOException, CertificateException, NoSuchAlgorithmException {
        KeyStore keystore = KeyStore.getInstance(responderMetadata.getTrustStoreType());
        File trustStoreFile = ResourceUtils.getFile(repo + File.separator + samlKeystore);
        keystore.load(Files.newInputStream(trustStoreFile.toPath()), responderMetadata.getTrustStorePassword().toCharArray());
        return keystore;
    }

    @Bean
    @ConditionalOnProperty(prefix = "eidas.connector.hsm", name = "enabled", havingValue = "true")
    public KeyStore responderMetadataHardwareKeyStore(HsmProperties hsmProperties) throws KeyStoreException, CertificateException, NoSuchAlgorithmException, IOException {
        log.info("Hardware security module enabled. Slot/slot index: {}/{}, Library: {}",
                hsmProperties.getSlot(), hsmProperties.getSlotListIndex(),
                hsmProperties.getLibrary());

        String configName = hsmProperties.toString();
        Provider provider = Security.getProvider("SunPKCS11");
        provider = provider.configure(configName);
        Security.addProvider(provider);
        KeyStore keyStore = KeyStore.getInstance("PKCS11", provider);
        keyStore.load(null, hsmProperties.getPin().toCharArray());
        return keyStore;
    }

    @Bean
    @ConditionalOnProperty(name = "eidas.connector.hsm.enabled", havingValue = "false", matchIfMissing = true)
    public BasicX509Credential signingCredential(ResponderMetadata responderMetadata, KeyStore responderMetadataKeyStore) throws Exception {
        String alias = responderMetadata.getKeyAlias();
        PrivateKey privateKey = (PrivateKey) responderMetadataKeyStore.getKey(alias, responderMetadata.getKeyStorePassword().toCharArray());
        X509Certificate x509Cert = (X509Certificate) responderMetadataKeyStore.getCertificate(alias);
        BasicX509Credential basicX509Credential = new BasicX509Credential(x509Cert, privateKey);
        basicX509Credential.setEntityId(alias);
        return basicX509Credential;
    }

    @Bean("signingCredential")
    @ConditionalOnProperty(prefix = "eidas.connector.hsm", name = "enabled", havingValue = "true")
    public BasicX509Credential signingCredentialHsm(ResponderMetadata responderMetadata, HsmProperties hsmProperties, KeyStore responderMetadataKeyStore,
                                                    KeyStore responderMetadataHardwareKeyStore) throws Exception {
        String alias = responderMetadata.getKeyAlias();
        char[] password = hsmProperties.getPin().toCharArray();
        PrivateKey privateKey = (PrivateKey) responderMetadataHardwareKeyStore.getKey(alias, password);
        X509Certificate x509Cert = hsmProperties.isCertificatesFromHsm() ?
                (X509Certificate) responderMetadataHardwareKeyStore.getCertificate(alias) : (X509Certificate) responderMetadataKeyStore.getCertificate(alias);
        BasicX509Credential basicX509Credential = new BasicX509Credential(x509Cert, privateKey);
        basicX509Credential.setEntityId(alias);
        return basicX509Credential;
    }
}
