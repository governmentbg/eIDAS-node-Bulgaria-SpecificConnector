package bg.is.eidas.connector.specific.monitoring.health;

import java.io.File;
import java.nio.file.Files;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.AbstractHealthIndicator;
import org.springframework.boot.actuate.health.Health;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.util.ResourceUtils;

import static java.lang.String.format;
import static java.time.Instant.now;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

@Slf4j
@Component
public class TruststoreHealthIndicator extends AbstractHealthIndicator {
    public static final String X_509 = "X.509";
    public static final String TRUSTSTORE_WARNING = "Truststore certificate '%s' with serial number '%s' is expiring at %s";
    private final Map<String, CertificateInfo> trustStoreCertificates = new HashMap<>();
    @Getter
    private final Clock systemClock;
    @Value("${javax.net.ssl.trustStore}")
    private String trustStore;
    @Value("${javax.net.ssl.trustStorePassword}")
    private String trustStorePassword;
    @Value("${javax.net.ssl.trustStoreType}")
    private String trustStoreType;
    @Value("${eidas.connector.health.trust-store-expiration-warning:30d}")
    private Period trustStoreExpirationWarningPeriod;
    @Value("#{environment.SPECIFIC_CONNECTOR_CONFIG_REPOSITORY}")
    private String repo;

    public TruststoreHealthIndicator() {
        super("Truststore certificates expiration check failed");
        this.systemClock = Clock.systemUTC();
    }

    @Override
    protected void doHealthCheck(Health.Builder builder) {
        if (getCertificatesExpiredAt(now(getSystemClock())).isEmpty()) {
            builder.up().withDetails(trustStoreCertificates).build();
        } else {
            builder.down().withDetails(trustStoreCertificates).build();
        }
    }

    public List<String> getCertificateExpirationWarnings() {
        return getCertificatesExpiredAt(now(getSystemClock())
                .plus(trustStoreExpirationWarningPeriod)).values().stream()
                .map(certificateInfo -> format(TRUSTSTORE_WARNING, certificateInfo.getSubjectDN(),
                        certificateInfo.getSerialNumber(), certificateInfo.getValidTo()))
                .collect(toList());
    }

    private Map<String, CertificateInfo> getCertificatesExpiredAt(Instant expired) {
        return trustStoreCertificates.entrySet().stream()
                .filter(es -> expired.isAfter(es.getValue().getValidTo()))
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    @SneakyThrows
    @PostConstruct
    private void setupTruststoreCertificatesInfo() {
        KeyStore keyStore = KeyStore.getInstance(trustStoreType);
        File trustStoreFile = ResourceUtils.getFile(repo + File.separator + trustStore);
        //File trustStoreFile = ResourceUtils.getFile(trustStore);
        keyStore.load(Files.newInputStream(trustStoreFile.toPath()), trustStorePassword.toCharArray());
        Enumeration<String> aliases = keyStore.aliases();
        while (aliases.hasMoreElements()) {
            String alias = aliases.nextElement();
            if (keyStore.isCertificateEntry(alias)) {
                Certificate certificate = keyStore.getCertificate(alias);
                if (X_509.equals(certificate.getType())) {
                    X509Certificate x509 = (X509Certificate) certificate;
                    trustStoreCertificates.put(alias, CertificateInfo.builder()
                            .validTo(x509.getNotAfter().toInstant())
                            .subjectDN(x509.getSubjectDN().getName())
                            .serialNumber(x509.getSerialNumber().toString())
                            .build());
                }
            }
        }
    }
}
