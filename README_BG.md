
# Специфичена конектор услуга за eIDAS възела на България

- [1. Компилиране на специфичния конектор](#build)
- [2. Интеграция с eIDAS възела](#integrate_with_eidasnode)
  * [2.1. Конфигурация на комуникацията с EidasNode](#integrate_eidasnode)
  * [2.2. Конфигурация на Ignite](#ignite_conf)
- [3. Генериране на метаданни](#metdata_generation)
- [4. Интегриране на доставчици на услуги](#service_providers)  
- [5. Събиране на събития](#logging)
  * [5.1. Настройване на събирането на събития](#log_conf)
  * [5.2. Формат на файла за събиране на събития](#log_file)
- [6. Наблюдение на услугата](#heartbeat)
- [7. Сигурност](#security)

<a name="build"></a>

## 1. Компилиране на специфичния конектор

Първо, подсигурете наличието на компилиран [eIDAS-Node](https://ec.europa.eu/digital-building-blocks/wikis/display/DIGITAL/eIDAS-Node+version+2.6) и сте инсталирали артифактите в локално хранилище на Maven:
```
cd EIDAS-Parent && mvn -DskipTests clean install -P NodeOnly,DemoToolsOnly -PnodeJcacheIgnite,specificCommunicationJcacheIgnite
```

След това изпълнете следната команда:
````
./mvnw clean package
````
**Важно!** Необходимо е `application.properties` и `jks` файловете да са поставени в папката `SpecificConnector` налична в хранилището за конфигурация на `EidasNode` - `$SPECIFIC_CONNECTOR_CONFIG_REPOSITORY/application.properties`, `$SPECIFIC_CONNECTOR_CONFIG_REPOSITORY/samlKeystore.jks`.

<a name="integrate_with_eidasnode"></a>
## 2. Интеграция с eIDAS възела

За да може да се осъществи комуникацията между `EidasNode` и `SpecificConnector` услугата, двете трябва да имат достъп до същия `Ignite` клъстер и да имат еднаква конфигурация на комуникацията (споделени тайни и т.н.)

**Важно!** Предполага се, че `SpecificConnector` приложението е инсталирано в същия приложен сървър, както и `EidasNode`, както и двете приложения имат достъп до същата конфигурация. 

<a name="integrate_eidasnode"></a>
### 2.1 Конфигурация на комуникацията с EidasNode

Изисква се `SpecificConnector` да има достъп до дефинициите за комуникация предоставени в следните конфигурационни файлове на `EidasNode`:
`$EIDAS_CONFIG_REPOSITORY/eidas.xml`,
`$SPECIFIC_CONNECTOR_CONFIG_REPOSITORY/specificCommunicationDefinitionConnector.xml`

| Параметър        | Задължително | Описание, пример |
| :---------------- | :---------- | :----------------|
| `eidas.connector.specific-connector-request-url` | Да | Адреса на `EidasNode`, който приема lighttoken, рефериращи заявката за автентикация към съответната страна членка. Примерна стойност: https://eidas-test.egov.bg:8443/EidasNode/SpecificConnectorRequest|

<a name="ignite_conf"></a>
### 2.2 Конфигурация на Ignite

Изисква се `EidasNode` и `SpecificConnector` да споделят същия xml конфигурационен файл: `$EIDAS_CONFIG_REPOSITORY/igniteSpecificCommunication.xml`

`SpecificConnector` стартира Ignite възела в клиентски режим, като използва конфигурацията за Ignite на `EidasNode`. Клиента на Ignite се стартира мързеливо (инициализира се при първо запитване).

Основно е изискването за достъп на `SpecificConnector` до предефинираните ключове в клъстера - виж таблица 1 за детайли.

| Име на ключ        |  Описание |
| :---------------- | :---------- |
| `specificNodeConnectorRequestCache` | Съдържа текущите LightRequest от EidasNode. |
| `nodeSpecificConnectorResponseCache` | Съдържа текущите LightResponse от EidasNode. |
| `specificMSSpRequestCorrelationMap` | Корелация между заявките постъпили от доставчиците на услуги. |

Таблица 1 - Споделени ключове използвани в `SpecificConnector`.

Примерна конфигурация по долу:

```
...
<property name="cacheConfiguration">
    <list>
        <!--Specific Communication Caches-->
        <!-- Partitioned cache example configuration (Atomic mode). -->
        <bean class="org.apache.ignite.configuration.CacheConfiguration">
            <property name="name" value="specificNodeConnectorRequestCache"/>
            <property name="atomicityMode" value="ATOMIC"/>
            <property name="backups" value="1"/>
            <property name="expiryPolicyFactory" ref="7_minutes_duration"/>
        </bean>
        <!-- Partitioned cache example configuration (Atomic mode). -->
        <bean class="org.apache.ignite.configuration.CacheConfiguration">
            <property name="name" value="nodeSpecificProxyserviceRequestCache"/>
            <property name="atomicityMode" value="ATOMIC"/>
            <property name="backups" value="1"/>
            <property name="expiryPolicyFactory" ref="7_minutes_duration"/>
        </bean>
        <!-- Partitioned cache example configuration (Atomic mode). -->
        <bean class="org.apache.ignite.configuration.CacheConfiguration">
            <property name="name" value="specificNodeProxyserviceResponseCache"/>
            <property name="atomicityMode" value="ATOMIC"/>
            <property name="backups" value="1"/>
            <property name="expiryPolicyFactory" ref="7_minutes_duration"/>
        </bean>
        <!-- Partitioned cache example configuration (Atomic mode). -->
        <bean class="org.apache.ignite.configuration.CacheConfiguration">
            <property name="name" value="nodeSpecificConnectorResponseCache"/>
            <property name="atomicityMode" value="ATOMIC"/>
            <property name="backups" value="1"/>
            <property name="expiryPolicyFactory" ref="7_minutes_duration"/>
        </bean>
        <!-- specificMSSpRequestCorrelationMap -->
        <bean class="org.apache.ignite.configuration.CacheConfiguration">
            <property name="name" value="specificMSSpRequestCorrelationMap"/>
            <property name="atomicityMode" value="ATOMIC"/>
            <property name="backups" value="1"/>
            <property name="expiryPolicyFactory" ref="7_minutes_duration"/>
        </bean>
    </list>
</property>
...
```

<a name="metdata_generation"></a>
## 3. Генериране на метаданни

| Параметър                                                                  | Задължителен        | Описание, пример                                                                                                                                                                                                                                                                         |
|:---------------------------------------------------------------------------|:-----------------|:--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `eidas.connector.responder-metadata.key-store`                             | Да               | Път до хранилището за ключове. Пример: `file:/etc/eidasconf/keystore/responder-metadata-keystore.p12`                                                                                                                                                                                       |
| `eidas.connector.responder-metadata.key-store-password`                    | Да               | Парола за хранилището на ключове.                                                                                                                                                                                                                                                           |
| `eidas.connector.responder-metadata.key-store-type`                        | Не               | Тип хранилище на ключове. Стойност по подразбиране: `PKCS12`                                                                                                                                                                                                                                |
| `eidas.connector.responder-metadata.key-alias`                             | Да <sup>5</sup>  | Наименование на ключа от хранилището.                                                                                                                                                                                                                                                       |
| `eidas.connector.responder-metadata.key-password`                          | Да <sup>4</sup>  | Парола за ключа от хранилището.                                                                                                                                                                                                                                                             |
| `eidas.connector.responder-metadata.trust-store`                           | Да               | Път до хранилище на доверени сертификати. Пример: `file:/etc/eidasconf/keystore/responder-metadata-truststore.p12`                                                                                                                                                                          |
| `eidas.connector.responder-metadata.trust-store-password`                  | Да               | Парола за хранилището на доверени сертификати.                                                                                                                                                                                                                                              |
| `eidas.connector.responder-metadata.trust-store-type`                      | Не               | Тип хранилище на доверени сертификати. Стойност по подразбиране: `PKCS12`                                                                                                                                                                                                                   |
| `eidas.connector.responder-metadata.signature-algorithm`                   | Не               | Алгоритъм за подписване, използван за подписване на публикуваните метаданни, SAML отговори и твърдения (дефиниран в RFC 4051). Стойност по подразбиране: `http://www.w3.org/2001/04/xmldsig-more#rsa-sha512`                                                                                |
| `eidas.connector.responder-metadata.key-transport-algorithm`               | Не               | Тип на транспортия алгоритм за криптиране на SAML отговорите (твърденията). Стойност по подразбиране: `http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p`                                                                                                                                     |
| `eidas.connector.responder-metadata.encryption-algorithm`                  | Не               | Алгоритъм използван в SAML отговорите (твърденията) за криптиране. Стойност по подразбиране: `http://www.w3.org/2009/xmlenc11#aes256-gcm`                                                                                                                                                   |
| `eidas.connector.responder-metadata.path`                                  | Не               | Път на точката за метаданни. https://eidas-specificconnector:8443/SpecificConnector/{eidas.connector.responder-metadata.path}. Стойност по подразбиране: `ConnectorResponderMetadata`                                                                                                       |
| `eidas.connector.responder-metadata.entity-id`                             | Да               | Точния HTTPS URL адрес, където са поубликувани метаданните. Пример: `https://eidas-specificconnector:8443/SpecificConnector/ConnectorResponderMetadata`                                                                                                                                     |
| `eidas.connector.responder-metadata.sso-service-url`                       | Да               | Точния HTTPS URL адрес, където се публикува точкат за автентикация от доставчиците на услуги. Пример: `https://eidas-specificconnector:8443/SpecificConnector/ServiceProvider`                                                                                                              |
| `eidas.connector.responder-metadata.name-id-format`                        | Не               | Формат за name-id. Възможни стойности: `urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified`,`urn:oasis:names:tc:SAML:2.0:nameid-format:transient`,`urn:oasis:names:tc:SAML:2.0:nameid-format:persistent`                                                                                 |
| `eidas.connector.responder-metadata.validity-interval`                     | Не               | Валидност на метаданните. [Дефинирана по ISO-8601 формат използван от java.time.Duration](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration). Стойност по подразбиране: `1d`                       |
| `eidas.connector.responder-metadata.assertion-validity-interval`           | Не               | Валидност на отговорите (твърденията) от автентикация. [Дефинирана по ISO-8601 формат използван от java.time.Duration](https://docs.spring.io/spring-boot/docs/current/reference/html/spring-boot-features.html#boot-features-external-config-conversion-duration). Примерна стойност: `5m` |
| `eidas.connector.responder-metadata.supported-member-states`               | Да               | Поддържани страни членки за автентикация (дефинирани по ISO 3166-1 alpha-2).                                                                                                                                                                                                                |
| `eidas.connector.responder-metadata.supported-bindings`                    | Не               | SAML2 поддържани bindings. Възможни стойности: `urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST`, `urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect`. Стойност по подразбиране:`urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST,urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect`         |
| `eidas.connector.responder-metadata.digest-methods`                        | Не               | Поддържания методи за digest. Стойност по подразбиране: `http://www.w3.org/2001/04/xmlenc#sha256,http://www.w3.org/2001/04/xmlenc#sha512`                                                                                                                                                   |
| `eidas.connector.responder-metadata.signing-methods[X].name`               | Да               | Поддържани алгоритми за подписване (дефиниран по RFC 4051)                                                                                                                                                                                                                                  |
| `eidas.connector.responder-metadata.signing-methods[X].minKeySize`         | Да               | Минимален размер на ключа                                                                                                                                                                                                                                                                   |
| `eidas.connector.responder-metadata.signing-methods[X].maxKeySize`         | Да               | Максимален размер на ключа                                                                                                                                                                                                                                                                  |
| `eidas.connector.responder-metadata.supported-attributes[X].name`          | Да               | Поддържан eIDAS атрибут по име (дефиниран по [eIDAS SAML Attribute Profile v1.2, paragraphs 2.2 and 2.3](https://ec.europa.eu/cefdigital/wiki/display/CEFDIGITAL/eIDAS+eID+Profile?preview=/82773108/148898847/eIDAS%20SAML%20Attribute%20Profile%20v1.2%20Final.pdf)                       |
| `eidas.connector.responder-metadata.supported-attributes[X].friendly-name` | Да               | Поддържан eIDAS атрибут по познато име (дефиниран по [eIDAS SAML Attribute Profile v1.2, paragraphs 2.2 and 2.3](https://ec.europa.eu/cefdigital/wiki/display/CEFDIGITAL/eIDAS+eID+Profile?preview=/82773108/148898847/eIDAS%20SAML%20Attribute%20Profile%20v1.2%20Final.pdf)               |
| `eidas.connector.responder-metadata.organization.name`                     | Да               | Име на организацията, публикувано в метаданните.                                                                                                                                                                                                                                            |
| `eidas.connector.responder-metadata.organization.display-name`             | Да               | Публично име на организацията, публикувано в метаданните.                                                                                                                                                                                                                                   |
| `eidas.connector.responder-metadata.organization.url`                      | Да               | Страниця на организацията, публикувана в метаданните.                                                                                                                                                                                                                                       |
| `eidas.connector.responder-metadata.contacts[X].surname`                   | Да               | Фамилия за контакт, публикувана в метаданните.                                                                                                                                                                                                                                              |
| `eidas.connector.responder-metadata.contacts[X].given-name`                | Да               | Име за контакт, публикувана в метаданните.                                                                                                                                                                                                                                                  |
| `eidas.connector.responder-metadata.contacts[X].company`                   | Да               | Компания за контакт, публикувана в метаданните.                                                                                                                                                                                                                                             |
| `eidas.connector.responder-metadata.contacts[X].phone`                     | Да               | Телефон за контакт, публикувана в метаданните.                                                                                                                                                                                                                                              |
| `eidas.connector.responder-metadata.contacts[X].email`                     | Да               | Е-mail за контакт, публикувана в метаданните.                                                                                                                                                                                                                                               |
| `eidas.connector.responder-metadata.contacts[X].type`                      | Да               | Тип на контакта, публикувана в метаданните. Възможни стойности: `technical`,`support`,`administrative`,`billing`,`other`                                                                                                                                                                    |

* За X се поставя индексно число, стартиращо от 0 и нарастващо за всеки нов метод за подписване, контакт, поддържан атрибут.


| Стойности по подразбиране                                                                                                                                 |
|:----------------------------------------------------------------------------------------------------------------------------------------------------------|
| `eidas.connector.hsm.enabled=false`                                                                                                                       |
| `eidas.connector.hsm.certificates-from-hsm=false`                                                                                                         |
| `eidas.connector.responder-metadata.path=ConnectorResponderMetadata`                                                                                      |
| `eidas.connector.responder-metadata.validity-in-days=1`                                                                                                   |
| `eidas.connector.responder-metadata.key-store-type=PKCS12`                                                                                                |
| `eidas.connector.responder-metadata.trust-store-type=PKCS12`                                                                                              |
| `eidas.connector.responder-metadata.digest-methods=http://www.w3.org/2001/04/xmlenc#sha256,http://www.w3.org/2001/04/xmlenc#sha512`                       |
| `eidas.connector.responder-metadata.signing-methods[0].name=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512`                                          |
| `eidas.connector.responder-metadata.signing-methods[0].minKeySize=384`                                                                                    |
| `eidas.connector.responder-metadata.signing-methods[0].maxKeySize=384`                                                                                    |
| `eidas.connector.responder-metadata.signing-methods[1].name=http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256`                                          |
| `eidas.connector.responder-metadata.signing-methods[1].minKeySize=384`                                                                                    |
| `eidas.connector.responder-metadata.signing-methods[1].maxKeySize=384`                                                                                    |
| `eidas.connector.responder-metadata.signing-methods[2].name=http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1`                                       |
| `eidas.connector.responder-metadata.signing-methods[2].minKeySize=4096`                                                                                   |
| `eidas.connector.responder-metadata.signing-methods[2].maxKeySize=4096`                                                                                   |
| `eidas.connector.responder-metadata.supported-bindings=urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST,urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect` |
| `eidas.connector.responder-metadata.supported-attributes[0]name=http://eidas.europa.eu/attributes/naturalperson/BirthName`                                |
| `eidas.connector.responder-metadata.supported-attributes[0]friendlyName=BirthName`                                                                        |
| `eidas.connector.responder-metadata.supported-attributes[1]name=http://eidas.europa.eu/attributes/naturalperson/CurrentAddress`                           |
| `eidas.connector.responder-metadata.supported-attributes[1]friendlyName=CurrentAddress`                                                                   |
| `eidas.connector.responder-metadata.supported-attributes[2]name=http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName`                        |
| `eidas.connector.responder-metadata.supported-attributes[2]friendlyName=FamilyName`                                                                       |
| `eidas.connector.responder-metadata.supported-attributes[3]name=http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName`                         |
| `eidas.connector.responder-metadata.supported-attributes[3]friendlyName=FirstName`                                                                        |
| `eidas.connector.responder-metadata.supported-attributes[4]name=http://eidas.europa.eu/attributes/naturalperson/DateOfBirth`                              |
| `eidas.connector.responder-metadata.supported-attributes[4]friendlyName=DateOfBirth`                                                                      |
| `eidas.connector.responder-metadata.supported-attributes[5]name=http://eidas.europa.eu/attributes/naturalperson/Gender`                                   |
| `eidas.connector.responder-metadata.supported-attributes[5]friendlyName=Gender`                                                                           |
| `eidas.connector.responder-metadata.supported-attributes[6]name=http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier`                         |
| `eidas.connector.responder-metadata.supported-attributes[6]friendlyName=PersonIdentifier`                                                                 |
| `eidas.connector.responder-metadata.supported-attributes[7]name=http://eidas.europa.eu/attributes/naturalperson/PlaceOfBirth`                             |
| `eidas.connector.responder-metadata.supported-attributes[7]friendlyName=PlaceOfBirth`                                                                     |
| `eidas.connector.responder-metadata.supported-attributes[8]name=http://eidas.europa.eu/attributes/legalperson/D-2012-17-EUIdentifier`                     |
| `eidas.connector.responder-metadata.supported-attributes[8]friendlyName=D-2012-17-EUIdentifier`                                                           |
| `eidas.connector.responder-metadata.supported-attributes[9]name=http://eidas.europa.eu/attributes/legalperson/EORI`                                       |
| `eidas.connector.responder-metadata.supported-attributes[9]friendlyName=EORI`                                                                             |
| `eidas.connector.responder-metadata.supported-attributes[10]name=http://eidas.europa.eu/attributes/legalperson/LEI`                                       |
| `eidas.connector.responder-metadata.supported-attributes[10]friendlyName=LEI`                                                                             |
| `eidas.connector.responder-metadata.supported-attributes[11]name=http://eidas.europa.eu/attributes/legalperson/LegalName`                                 |
| `eidas.connector.responder-metadata.supported-attributes[11]friendlyName=LegalName`                                                                       |
| `eidas.connector.responder-metadata.supported-attributes[12]name=http://eidas.europa.eu/attributes/legalperson/LegalPersonAddress`                        |
| `eidas.connector.responder-metadata.supported-attributes[12]friendlyName=LegalAddress`                                                                    |
| `eidas.connector.responder-metadata.supported-attributes[13]name=http://eidas.europa.eu/attributes/legalperson/LegalPersonIdentifier`                     |
| `eidas.connector.responder-metadata.supported-attributes[13]friendlyName=LegalPersonIdentifier`                                                           |
| `eidas.connector.responder-metadata.supported-attributes[14]name=http://eidas.europa.eu/attributes/legalperson/SEED`                                      |
| `eidas.connector.responder-metadata.supported-attributes[14]friendlyName=SEED`                                                                            |
| `eidas.connector.responder-metadata.supported-attributes[15]name=http://eidas.europa.eu/attributes/legalperson/SIC`                                       |
| `eidas.connector.responder-metadata.supported-attributes[15]friendlyName=SIC`                                                                             |
| `eidas.connector.responder-metadata.supported-attributes[16]name=http://eidas.europa.eu/attributes/legalperson/TaxReference`                              |
| `eidas.connector.responder-metadata.supported-attributes[16]friendlyName=TaxReference`                                                                    |
| `eidas.connector.responder-metadata.supported-attributes[17]name=http://eidas.europa.eu/attributes/legalperson/VATRegistrationNumber`                     |
| `eidas.connector.responder-metadata.supported-attributes[17]friendlyName=VATRegistration`                                                                 |

Примерни метаданни публикувани на адрес https://eidas-specificconnector:8443/SpecificConnector/ConnectorResponderMetadata

```xml
<?xml version="1.0" encoding="UTF-8"?>
<md:EntityDescriptor xmlns:md="urn:oasis:names:tc:SAML:2.0:metadata" ID="_nbkbamxofhndwguwkbskhwr0untehl1kyvypnpq" entityID="https://eidas-specificconnector:8443/SpecificConnector/ConnectorResponderMetadata" validUntil="2020-10-13T18:10:11.090Z">
   <ds:Signature xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
      <ds:SignedInfo>
         <ds:CanonicalizationMethod Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
         <ds:SignatureMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#rsa-sha512" />
         <ds:Reference URI="#_nbkbamxofhndwguwkbskhwr0untehl1kyvypnpq">
            <ds:Transforms>
               <ds:Transform Algorithm="http://www.w3.org/2000/09/xmldsig#enveloped-signature" />
               <ds:Transform Algorithm="http://www.w3.org/2001/10/xml-exc-c14n#" />
            </ds:Transforms>
            <ds:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha512" />
            <ds:DigestValue>2Z7PUIrB5sXVPt6TkHfX8J7WwDvM7OER0lBKo3vZpNQK3YaWR4ukjw7OFKzDqS6fB8QWTVt0tJcESP12GRXEPw==</ds:DigestValue>
         </ds:Reference>
      </ds:SignedInfo>
      <ds:SignatureValue>Q8kTcdIugmGkosaq/7Z3HG9jiv9+mfNkOlErK0igXZXQfqABDXFg1BpMAM4ooxwe422982AB+tVO
jLYi+BikTzPFUKN3KBPN+Lr9rRAt107fuv9jGIAZzTrPr+f0UAPO61qhBs2n/YbjwMxgqgxxnisO
htwgueo5HpD3ciAiFRx+4dRLh++6caIKZdfO9Ko9cH8P1hcG4tvsj5VR4bfZwBH2gqU4XeDoKsQG
CIET5QIqdgRklDoyg4OJ0MinbYwHy6BFC3KEC34xDxE2qyXqAnRog8f5/BPRnlEonwvPM9A6xnTR
kYrzQf02gFfoEesHnZ9eZOkw2sTH/2rA/LfFH/QuPvvRVl3XITaYSRv8GH0JDxOy3eYUu+Aopcrr
l8SSLQUJS9xOyBLobaGHCPAAZX6miHKe0MRWM/UHHL5eXVAh+GkLRk9u/WolZUdz0sa0F8PIeaea
Z0GHBBRCfiCG5ImXa1sTCwSVv2a1oNY7SjzSM1XxNPtmIBDA8eE9wrTcZXYlAD/0LL4N0BOgjZ5B
g+jnoSrITvq3nBF522nn6dgQxYy4HOT3tb5c+slSAdDCnKWjNaKwJIPIfaCy+LR6P7oJuSHLaWkk
4Nu9BBoXUhnQWzO49QPotVJ7FMrr4n+4q9AbtFiH1PPsLkCokhUPm/uy/VPRMCGgiVcyx8ief40=</ds:SignatureValue>
      <ds:KeyInfo>
         <ds:X509Data>
            <ds:X509Certificate>MIIFmzCCA4OgAwIBAgIEX2eUsTANBgkqhkiG9w0BAQsFADB+MQswCQYDVQQGEwJFRTENMAsGA1UE
CAwEdGVzdDENMAsGA1UEBwwEdGVzdDENMAsGA1UECgwEdGVzdDENMAsGA1UECwwEdGVzdDEzMDEG
A1UEAwwqZWlkYXMtc3BlY2lmaWNjb25uZWN0b3ItcmVzcG9uZGVyLW1ldGFkYXRhMB4XDTIwMDky
MDE3NDMxM1oXDTMwMDkyMDE3NDMxM1owfjELMAkGA1UEBhMCRUUxDTALBgNVBAgMBHRlc3QxDTAL
BgNVBAcMBHRlc3QxDTALBgNVBAoMBHRlc3QxDTALBgNVBAsMBHRlc3QxMzAxBgNVBAMMKmVpZGFz
LXNwZWNpZmljY29ubmVjdG9yLXJlc3BvbmRlci1tZXRhZGF0YTCCAiIwDQYJKoZIhvcNAQEBBQAD
ggIPADCCAgoCggIBANP/gTFt+ToahE842QQQyDr1PPXAECy5GeZ7OcViVSoU239EG+edTGgdfvJn
p+Ek/kjL/vgG5X5uMIlvMcjssu7xuJZndyJUPrWQM6FGnEtJ1Qv+tKRvmD8ZDz4e/kFjPXr+9/W6
z4jt/TOGcVKwiB4luYD54GWGCoqoX55WCsJzYUVrMS4paHoftp/iUVRyQf4DSijtbvZl/Ypbmy6i
LNVOKhFbDaFw5j1DFmO6pOf41IFMxCANBUhq2PHignZjSx0eGP0675o/i9QbLyc59dVwnUhnPQvs
EQWyAiiS+61ZzDkAyIVTbQdx/RyQwLyFJh4QO00aocLpBud9FsYuSN1wLKuZSjbbM/ClbiIgs9F9
3tzijCLkEU9Tx62NhwExq4P5PY1O8cEl9BX4ema+aoyvYDd5eifvI7iTvHv621jxqT2LDvCQjlNA
OWelNOGsbZuCiQJjln3I1fscN6SB2cdxYebyVCQrfhsZZv7iawp8WCnw/wd/XVyise0lt0asTZWy
haOmTFv+nVIbHLcbiFzcEHoPfA31uQa4AOb6mTC9LIZDcelEKJIRiWuTv7hrBw5EAIUVhSdoGv9M
ReYq+vhMtT8lHg8m+IK0iEOfTRUs8x3YJYt2GH9+DC6onyhCm1WdEKuxYU5lPJVJgyg0ejxk0pmt
sjrHdAeEdbZF4wrPAgMBAAGjITAfMB0GA1UdDgQWBBSz7x9rg/0/MDKXc1gISohsoUXXHDANBgkq
hkiG9w0BAQsFAAOCAgEAVy6kdgxstQCsWvtq1PuNMnYanzveW1l/jrH8u80r/tBQ29yLjlvSj4e6
MQdA6EIKwFsKjmH2ZrdpqXyUdFjlfF2LYgpQamf7b8U6s+oFsX3IYRj73fDGJbvlE6gahv4Euadu
HrivtfHpgtNXdVF2ZrsrY6LbgiMPFZto938M0xmdxDxpGXp2Q2PXu0LGXXptidudikcvD09sciAP
7RBFPmxSQG2o+RgoJKAsvEQnEPCfSvhlK/SZR/iBmYyxXPhLCBpszFq91xXrD0h2w1KCXKIWTDb8
w2JuHs7P1PkcmrqSXXYHIf7dBNFKU6AuA/uKteqOO5i0hh7wL7gA56YDghbFGi+UHCft7TrWssso
GaQkM/YLaFApayHuqQ7J7F5hQvfkwBErPR6uIvFyHMjL5NtoFF2kzVTDx4j/uNzxHXk4XqDX3ZDw
6hiQmV7Tk7cJRUqU+q5TkYu4TgkBeE1quscVK7gsfFaWv7MBTIT4IBelEFtCU97cNzTqy6TTHnbo
aTqRc1cqN5cA6tebLp+cP0+pIsu6RM69eive+RJJBOMh7Dfd/EVp/EYPmc2AFiNVNMRnq4SVa1Ac
2nr1ewvm5yJAkefV8w7TNbQ/QKKpPZRfgCH5/5bWp6Q9T3T+6s0ydiIUJQ7fLMR8zEj50+UT/iuf
OF6TawGAOCgZSsptJbU=</ds:X509Certificate>
         </ds:X509Data>
      </ds:KeyInfo>
   </ds:Signature>
   <md:Extensions xmlns:alg="urn:oasis:names:tc:SAML:metadata:algsupport">
      <bg:SupportedMemberStates xmlns:bg="http://eidas.europa.eu/saml-extensions">
         <bg:MemberState>EE</bg:MemberState>
         <bg:MemberState>BG</bg:MemberState>
      </bg:SupportedMemberStates>
      <alg:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha256" />
      <alg:DigestMethod Algorithm="http://www.w3.org/2001/04/xmlenc#sha512" />
      <alg:SigningMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512" MaxKeySize="384" MinKeySize="384" />
      <alg:SigningMethod Algorithm="http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha256" MaxKeySize="384" MinKeySize="384" />
      <alg:SigningMethod Algorithm="http://www.w3.org/2007/05/xmldsig-more#sha256-rsa-MGF1" MaxKeySize="4096" MinKeySize="4096" />
      <mdattr:EntityAttributes xmlns:mdattr="urn:oasis:names:tc:SAML:metadata:attribute">
         <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" Name="http://macedir.org/entity-category" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri">
            <saml2:AttributeValue>http://eidas.europa.eu/entity-attributes/termsofaccess/requesterid</saml2:AttributeValue>
         </saml2:Attribute>
      </mdattr:EntityAttributes>
   </md:Extensions>
   <md:IDPSSODescriptor WantAuthnRequestsSigned="true" protocolSupportEnumeration="urn:oasis:names:tc:SAML:2.0:protocol">
      <md:KeyDescriptor use="signing">
         <ds:KeyInfo xmlns:ds="http://www.w3.org/2000/09/xmldsig#">
            <ds:X509Data>
               <ds:X509Certificate>MIIFmzCCA4OgAwIBAgIEX2eUsTANBgkqhkiG9w0BAQsFADB+MQswCQYDVQQGEwJFRTENMAsGA1UE
CAwEdGVzdDENMAsGA1UEBwwEdGVzdDENMAsGA1UECgwEdGVzdDENMAsGA1UECwwEdGVzdDEzMDEG
A1UEAwwqZWlkYXMtc3BlY2lmaWNjb25uZWN0b3ItcmVzcG9uZGVyLW1ldGFkYXRhMB4XDTIwMDky
MDE3NDMxM1oXDTMwMDkyMDE3NDMxM1owfjELMAkGA1UEBhMCRUUxDTALBgNVBAgMBHRlc3QxDTAL
BgNVBAcMBHRlc3QxDTALBgNVBAoMBHRlc3QxDTALBgNVBAsMBHRlc3QxMzAxBgNVBAMMKmVpZGFz
LXNwZWNpZmljY29ubmVjdG9yLXJlc3BvbmRlci1tZXRhZGF0YTCCAiIwDQYJKoZIhvcNAQEBBQAD
ggIPADCCAgoCggIBANP/gTFt+ToahE842QQQyDr1PPXAECy5GeZ7OcViVSoU239EG+edTGgdfvJn
p+Ek/kjL/vgG5X5uMIlvMcjssu7xuJZndyJUPrWQM6FGnEtJ1Qv+tKRvmD8ZDz4e/kFjPXr+9/W6
z4jt/TOGcVKwiB4luYD54GWGCoqoX55WCsJzYUVrMS4paHoftp/iUVRyQf4DSijtbvZl/Ypbmy6i
LNVOKhFbDaFw5j1DFmO6pOf41IFMxCANBUhq2PHignZjSx0eGP0675o/i9QbLyc59dVwnUhnPQvs
EQWyAiiS+61ZzDkAyIVTbQdx/RyQwLyFJh4QO00aocLpBud9FsYuSN1wLKuZSjbbM/ClbiIgs9F9
3tzijCLkEU9Tx62NhwExq4P5PY1O8cEl9BX4ema+aoyvYDd5eifvI7iTvHv621jxqT2LDvCQjlNA
OWelNOGsbZuCiQJjln3I1fscN6SB2cdxYebyVCQrfhsZZv7iawp8WCnw/wd/XVyise0lt0asTZWy
haOmTFv+nVIbHLcbiFzcEHoPfA31uQa4AOb6mTC9LIZDcelEKJIRiWuTv7hrBw5EAIUVhSdoGv9M
ReYq+vhMtT8lHg8m+IK0iEOfTRUs8x3YJYt2GH9+DC6onyhCm1WdEKuxYU5lPJVJgyg0ejxk0pmt
sjrHdAeEdbZF4wrPAgMBAAGjITAfMB0GA1UdDgQWBBSz7x9rg/0/MDKXc1gISohsoUXXHDANBgkq
hkiG9w0BAQsFAAOCAgEAVy6kdgxstQCsWvtq1PuNMnYanzveW1l/jrH8u80r/tBQ29yLjlvSj4e6
MQdA6EIKwFsKjmH2ZrdpqXyUdFjlfF2LYgpQamf7b8U6s+oFsX3IYRj73fDGJbvlE6gahv4Euadu
HrivtfHpgtNXdVF2ZrsrY6LbgiMPFZto938M0xmdxDxpGXp2Q2PXu0LGXXptidudikcvD09sciAP
7RBFPmxSQG2o+RgoJKAsvEQnEPCfSvhlK/SZR/iBmYyxXPhLCBpszFq91xXrD0h2w1KCXKIWTDb8
w2JuHs7P1PkcmrqSXXYHIf7dBNFKU6AuA/uKteqOO5i0hh7wL7gA56YDghbFGi+UHCft7TrWssso
GaQkM/YLaFApayHuqQ7J7F5hQvfkwBErPR6uIvFyHMjL5NtoFF2kzVTDx4j/uNzxHXk4XqDX3ZDw
6hiQmV7Tk7cJRUqU+q5TkYu4TgkBeE1quscVK7gsfFaWv7MBTIT4IBelEFtCU97cNzTqy6TTHnbo
aTqRc1cqN5cA6tebLp+cP0+pIsu6RM69eive+RJJBOMh7Dfd/EVp/EYPmc2AFiNVNMRnq4SVa1Ac
2nr1ewvm5yJAkefV8w7TNbQ/QKKpPZRfgCH5/5bWp6Q9T3T+6s0ydiIUJQ7fLMR8zEj50+UT/iuf
OF6TawGAOCgZSsptJbU=</ds:X509Certificate>
            </ds:X509Data>
         </ds:KeyInfo>
      </md:KeyDescriptor>
      <md:NameIDFormat>urn:oasis:names:tc:SAML:2.0:nameid-format:persistent</md:NameIDFormat>
      <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST" Location="https://eidas-specificconnector:8443/SpecificConnector/ServiceProvider" />
      <md:SingleSignOnService Binding="urn:oasis:names:tc:SAML:2.0:bindings:HTTP-Redirect" Location="https://eidas-specificconnector:8443/SpecificConnector/ServiceProvider" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="BirthName" Name="http://eidas.europa.eu/attributes/naturalperson/BirthName" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="CurrentAddress" Name="http://eidas.europa.eu/attributes/naturalperson/CurrentAddress" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="FamilyName" Name="http://eidas.europa.eu/attributes/naturalperson/CurrentFamilyName" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="FirstName" Name="http://eidas.europa.eu/attributes/naturalperson/CurrentGivenName" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="DateOfBirth" Name="http://eidas.europa.eu/attributes/naturalperson/DateOfBirth" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="Gender" Name="http://eidas.europa.eu/attributes/naturalperson/Gender" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="PersonIdentifier" Name="http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="PlaceOfBirth" Name="http://eidas.europa.eu/attributes/naturalperson/PlaceOfBirth" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="D-2012-17-EUIdentifier" Name="http://eidas.europa.eu/attributes/legalperson/D-2012-17-EUIdentifier" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="EORI" Name="http://eidas.europa.eu/attributes/legalperson/EORI" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="LEI" Name="http://eidas.europa.eu/attributes/legalperson/LEI" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="LegalName" Name="http://eidas.europa.eu/attributes/legalperson/LegalName" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="LegalAddress" Name="http://eidas.europa.eu/attributes/legalperson/LegalPersonAddress" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="LegalPersonIdentifier" Name="http://eidas.europa.eu/attributes/legalperson/LegalPersonIdentifier" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="SEED" Name="http://eidas.europa.eu/attributes/legalperson/SEED" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="SIC" Name="http://eidas.europa.eu/attributes/legalperson/SIC" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="TaxReference" Name="http://eidas.europa.eu/attributes/legalperson/TaxReference" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
      <saml2:Attribute xmlns:saml2="urn:oasis:names:tc:SAML:2.0:assertion" FriendlyName="VATRegistration" Name="http://eidas.europa.eu/attributes/legalperson/VATRegistrationNumber" NameFormat="urn:oasis:names:tc:SAML:2.0:attrname-format:uri" />
   </md:IDPSSODescriptor>
</md:EntityDescriptor>
```

<a name="service_providers"></a>
## 4. Интегриране на доставчици на услуги

За да добавите нов доставчик на услуги, следните конфигурационни параметри трябва да бъдат заложени за всеки отделен доставчик.

| Параметър        | Задължителен | Описание, пример |
| :---------------- | :---------- | :----------------|
| `eidas.connector.service-providers[X].id` | Да | Идентификатор на доставчика. Трябва да е уникален. |
| `eidas.connector.service-providers[X].entity-id` | Да | `entityId` публикувано в метаданните на доставчика. Представлява HTTPS URL адреса сочещ до местоположението на метаданните. Трябва да е уникален. |
| `eidas.connector.service-providers[X].key-alias` | Да | Наименование на сертификата от хранилището за доверени сертификати. Трябва да е уникалнен. |

* За X индекс се използва цяло число, започващо от 0 и нарастващо за всеки нов доставчик.

Приложението периодично проверява за промени в метаданните на доставчиците на усуги. Всеки доставчик публикува `validUntil` и `cacheDuration` 
в метаданните си, за да укаже как/кога трябва да се обновят те.

Времето между всеки интервал за опресняване се калкулира по следния начин:

Ако има проблем с връзката до метаданните на доставчика, тогава `eidas.connector.service-provider-metadata-min-refresh-delay` се използва за интервал между опитите. Ако липсват `validUntil` и `cacheDuration` в метаданните, тогава `eidas.connector.service-provider-metadata-max-refresh-delay` се използва. Ако този интервал е по голям от максималното време, тогава се използва `eidas.connector.service-provider-metadata-max-refresh-delay`. В противен случа, се калкулира чрез умножение с `eidas.connector.service-provider-metadata-refresh-delay-factor`. Използвайки този фактор, приложението ще опита да опресни данните преди изтичане на кеша, позволявайки възможност за възстановяване при грешки. Приемайки този фактор не надвишава 1.0 и минималното време за опресняване не е прекалено голямо, това ще се осъществи няколко пъти преди изтичането на кеша.

| Параметър        | Задължителен | Описание, пример |
| :---------------- | :---------- | :----------------|
| `eidas.connector.service-provider-metadata-min-refresh-delay` | Не | Минимално време в милисекунди, между опресняванията. Стойност по подразбиране: `60000` (60 секунди) |
| `eidas.connector.service-provider-metadata-max-refresh-delay` | Не | Интервал на опресняване, използван при липса на `validUntil` или `cacheDuration`. Стойност по подразбиране: `14400000` (4 часа) |
| `eidas.connector.service-provider-metadata-refresh-delay-factor` | Не | Залага фактор на забавяне, използван за премсятане на следващите интервал за опресняване. Необходимо е да е между `0.0` и `1.0`. |
| `eidas.connector.add-saml-error-assertion` | Не | Мехънизъм за обратна съвместимост, който позволява eIDAS-Client да добави криптиране, при провал на автентикацията. Стойност по подразбиране: `false` |

<a name="logging"></a>
## 5. Събиране на събития

Събирането на събития в SpecificConnectorService се осъществява чрез [Logback framework](http://logback.qos.ch/documentation.html) посредством [SLF4J facade](http://www.slf4j.org/).

<a name="log_conf"></a>
### 5.1 Настройване на събирането на събития

Събирането на събития може да бъде конфигурирано чрез xml конфигурационния файл (logback-spring.xml). По подразбиране SpecificConnectorService използва примерна конфигурация в приложението, която събира събитията във файл - `/var/log/SpecificConnector-yyyy-mm-dd.log` и сменя файла ежедневно. Събитията в конзолата са спрени по подразбиране.

Поведението за събирането на събития може да бъде променено по следните начини:

1. Чрез превъзлагане на специфичните параметри във logback-spring.xml конфигурацията чрез системни променливи (виж таблица 5.1.1)

    Таблица 5.1.1 - Стойности по подразбиране

    | Параметър        | Задължителен | Описание, пример |
    | :---------------- | :---------- | :----------------|
    | `LOG_HOME` | Не | Директория в която се записват файловете със събития. По подразбиране е `/var/log`, ако не е посочена. |
    | `LOG_CONSOLE_LEVEL` | Не | Ниво на детайлност за събитята извеждани в конзолата. Валидните стойности са: `OFF`, `FATAL`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`. По подразбиране е `OFF`, ако не е посочена. |
    | `LOG_CONSOLE_PATTERN` | Не | Шаблон за редовете при извеждане на събитията към конзолата. |
    | `LOG_FILE_LEVEL` | Не | Ниво на детайлите за събитията събирани във файл. Валидните стойности са: `OFF`, `FATAL`, `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`. По подразбиране е `INFO`, ако не е посочена. |
    | `LOG_FILES_MAX_COUNT` | Не | Брой на дните, за които се запазват файловете при подмяната им. По подразбиране е `31`, ако не е посочена. |    

2. Специфичен файл може да бъде посочен, за по детайлен контрол върху записванеот на събитията. Неговото местоположение се оказва чрез системната променлива `LOGGING_CONFIG`, Java системната променлива `logging.config` или променлива `logging.config` в application.properties файла на приложението. 

   Пример 1: Промяна на конфигурацията по подразбиране чрез системна променлива:
    
   ````
   LOGGING_CONFIG=/etc/eidas/config/logback.xml
   ````
   
   Пример 2: Промяна чрез Java системна променлива:
       
   ````
   -Dlogging.config=/etc/eidas/config/logback.xml
   ````      

   Пример 3: Промяна чрез стойност в application.properties:
    
   ````
   logging.config=file:/etc/eidas/config/logback.xml
   ````   

<a name="log_file"></a>
### 5.2 Формат на файла за събиране на събития

По подразбиране SpecificConnectorService използва примерна конфигурация вградена в приложението, която записва събитията в файл - `/var/log/SpecificConnector-yyyy-mm-dd.log`. 

JSON формата се използва за оформление на редовете. Полетата в JSON-a sledwat [ECS Field reference](https://www.elastic.co/guide/en/ecs/current/ecs-field-reference.html).  

Следните полета се поддържат:

| Параметър        | Задължителен | Описание, пример                                                                                                                                                       |
| :---------------- | :---------- |:---------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `@timestamp`  | Да | Дата/час когато се е случило събитието.                                                                                                                                       |
| `log.level`   | Да | Оригинално ниво на събитието. Възможни стойности: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE`                                                                            |
| `log.logger`  | Да | Наименованието на компонента от приложението.                                                                                                                              |
| `process.pid` | Да | ID на процеса.                                                                                                                                                                |
| `process.thread.name` | Да | Име на нишката.                                                                                                                                                               |
| `service.name` | Да | Име на услугата от която е събрано събитието. Константна стойност: `bg-eidas-connector`.                                                                                          |
| `service.type` | Да | Типа на услугата от която е събрано събитието. Константна стойност: `specific`.                                                                                                |
| `service.node.name` | Да | Уникално име на нода. Това позволява два нода с една и съща услуга, върху един и същи хост да се разграничат.                                                |
| `service.version` | Не | Версия на приложението.                                                                                                                                                    |
| `session.id` | Не | Уникален идентификатор на сесията. Базиран е на http бисквитка, която позволява корелация между `EidasNode` и `SpecificConnectorService`.                         |
| `trace.id` | Не | Уникален идентификатор на сесията. Групира множество събития (транзакции) които са свързани помежду им. На пример, потребителска заявка преминала между няколко вътрешно-свързани услуги. |
| `transaction.id` | Не | Уникален идентификатор на транзакцията. Тя е най-високото ниво за измерване на работата на приложението, като заявка до сървъра.                                 |
| `message` | Да | Същинското съобщение/събитие.                                                                                                                                                           |
| `error.type` | Не | Типа на грешката - името на проблемния клас.                                                                                                                   |
| `error.stack_trace` | Не | Проследяване по стека за грешката в текстови формат.                                                                                                                               |
| `event.kind` | Не | [ECS Event Categorization Field](https://www.elastic.co/guide/en/ecs/current/ecs-allowed-values-event-kind.html)                                                           |
| `event.category` | Не | [ECS Event Categorization Field](https://www.elastic.co/guide/en/ecs/current/ecs-allowed-values-event-category.html)                                                       |
| `event.type` | Не | [ECS Event Categorization Field](https://www.elastic.co/guide/en/ecs/current/ecs-allowed-values-event-type.html)                                                       |
| `event.outcome` | Не | [ECS Event Categorization Field](https://www.elastic.co/guide/en/ecs/current/ecs-allowed-values-event-outcome.html)                                                        |

Полета по избор, относими към автентикацията

| Параметър        | Задължителен | Описание, пример |
| :---------------- | :---------- | :----------------|
| `authn_request` | No | Полета относно SAML 2.0 AuthnRequest |
| `saml_response` | No | Полета относно SAML 2.0 Response |

Примерно съобщение за събитие съдържащо инициализиращо запитване за автентикация (authn_request):

```json
{
  "@timestamp": "2020-10-23T14:22:50.750Z",
  "log.level": "INFO",
  "log.logger": "e.r.e.c.s.c.ServiceProviderController",
  "process.pid": 1,
  "process.thread.name": "https-openssl-nio-8443-exec-7",
  "service.name": "bg-eidas-connector",
  "service.type": "specific",
  "service.node.name": "25848971-d261-4686-ac78-cbda659a6c9b",
  "service.version": "1.0.0-SNAPSHOT",
  "session.id": "35537975F280B3B9CDA37AFEC90177E2",
  "trace.id": "d28dac609b3fff64",
  "transaction.id": "d28dac609b3fff64",
  "message": "AuthnRequest received",
  "authn_request": {
    "AssertionConsumerServiceURL": "https://eidas-bgserviceprovider:8889/returnUrl",
    "Destination": "https://eidas-specificconnector:8443/SpecificConnector/ServiceProvider",
    "ForceAuthn": "true",
    "ID": "_19e7fe372ac0f1c2c2600c36aa26411d",
    "IsPassive": "false",
    "IssueInstant": "2020-10-23T14:22:50.325Z",
    "ProtocolBinding": "urn:oasis:names:tc:SAML:2.0:bindings:HTTP-POST",
    "ProviderName": "eidas-bgserviceprovider",
    "Version": "2.0",
    "Issuer": "https://eidas-bgserviceprovider:8889/metadata",
    "Signature": {
      "SignedInfo": {
        "CanonicalizationMethod": {
          "Algorithm": "http://www.w3.org/2001/10/xml-exc-c14n#"
        },
        "SignatureMethod": {
          "Algorithm": "http://www.w3.org/2001/04/xmldsig-more#ecdsa-sha512"
        },
        "Reference": {
          "URI": "#_19e7fe372ac0f1c2c2600c36aa26411d",
          "Transforms": {
            "Transform": {
              "Algorithm": "http://www.w3.org/2001/10/xml-exc-c14n#"
            }
          },
          "DigestMethod": {
            "Algorithm": "http://www.w3.org/2001/04/xmlenc#sha512"
          },
          "DigestValue": "07/aMjC0XXGXd0au+50fvBhu1jOF53Aw6Wv9AaY0zYpV1w7lrGPL2169YYun1ns7BEHL99ecmJzW\r\naA82zhyTIA=="
        }
      },
      "SignatureValue": "\nOPxK3sDj2tnbYDn6cPxO0JvDen/MTd9s1wzVgx+lxfvIBlRE6Nb3iY6xNZ0M5KnW2XVr3brxT8Jj\r\n/4JCjSr8ZQ==\n",
      "KeyInfo": {
        "X509Data": {
          "X509Certificate": "MIIB8zCCAZmgAwIBAgIEX2d0kTAKBggqhkjOPQQDAjBwMQswCQYDVQQGEwJFRTENMAsGA1UECAwE\ndGVzdDENMAsGA1UEBwwEdGVzdDENMAsGA1UECgwEdGVzdDENMAsGA1UECwwEdGVzdDElMCMGA1UE\nAwwcZWlkYXMtZWVzZXJ2aWNlcHJvdmlkZXItc2lnbjAeFw0yMDA5MjAxNTI2MDlaFw0zMDA5MjAx\nNTI2MDlaMHAxCzAJBgNVBAYTAkVFMQ0wCwYDVQQIDAR0ZXN0MQ0wCwYDVQQHDAR0ZXN0MQ0wCwYD\nVQQKDAR0ZXN0MQ0wCwYDVQQLDAR0ZXN0MSUwIwYDVQQDDBxlaWRhcy1lZXNlcnZpY2Vwcm92aWRl\nci1zaWduMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAESgy4lzSY6vB2Ib1EhydCW+jcfnyVeUmc\nWgFVcAOGyMmhUmk6TnBELevewzntc8X1QHQuLdwIh1a4ZXH3s0aY/6MhMB8wHQYDVR0OBBYEFIXa\nAerTmJkYtRDGTUFcjUVyZKBPMAoGCCqGSM49BAMCA0gAMEUCIQCdcQ6jbf20NXI+o+MdPio4xQeH\nONDcFT9alMGzNpqWyQIgGsm8We8T5QGE+e4g8KDE85ucxpDR2iLGiqnr8k5k5Vs="
        }
      }
    },
    "Extensions": {
      "SPType": "public",
      "RequesterID": "SAMPLE-REQUESTER-ID",
      "RequestedAttributes": {
        "RequestedAttribute": {
          "FriendlyName": "PersonIdentifier",
          "Name": "http://eidas.europa.eu/attributes/naturalperson/PersonIdentifier",
          "NameFormat": "urn:oasis:names:tc:SAML:2.0:attrname-format:uri",
          "isRequired": "true"
        }
      }
    },
    "NameIDPolicy": {
      "AllowCreate": "true",
      "Format": "urn:oasis:names:tc:SAML:1.1:nameid-format:unspecified"
    },
    "RequestedAuthnContext": {
      "Comparison": "minimum",
      "AuthnContextClassRef": "http://eidas.europa.eu/LoA/high"
    }
  },
  "authn_request.country": "CA",
  "authn_request.relay_state": "12345",
  "event.kind": "event",
  "event.category": "authentication",
  "event.type": "start"
}
```

Примерно съобщение за успешна автентикация (saml_response):

```json
{
  "@timestamp": "2020-10-23T14:23:03.221Z",
  "log.level": "INFO",
  "log.logger": "e.r.e.c.s.c.ConnectorResponseController",
  "process.pid": 1,
  "process.thread.name": "https-openssl-nio-8443-exec-5",
  "service.name": "bg-eidas-connector",
  "service.type": "specific",
  "service.node.name": "25848971-d261-4686-ac78-cbda659a6c9b",
  "service.version": "1.0.0-SNAPSHOT",
  "session.id": "35537975F280B3B9CDA37AFEC90177E2",
  "trace.id": "4cf9d54b8ed39d83",
  "transaction.id": "4cf9d54b8ed39d83",
  "message": "SAML response created",
  "saml_response": {
    "Destination": "https://eidas-bgserviceprovider:8889/returnUrl",
    "ID": "_KlrvIqsRSR_lmeSP5ULVlGLTesFFb-OC0kUTs9-20E3WQPIkDsqQMAiuTEJM0-O",
    "InResponseTo": "_19e7fe372ac0f1c2c2600c36aa26411d",
    "IssueInstant": "2020-10-23T14:23:03.142Z",
    "Version": "2.0",
    "Issuer": {
      "Format": "urn:oasis:names:tc:SAML:2.0:nameid-format:entity",
      "": "https://eidas-specificconnector:8443/SpecificConnector/ConnectorResponderMetadata"
    },
    "Signature": {
      "SignedInfo": {
        "CanonicalizationMethod": {
          "Algorithm": "http://www.w3.org/2001/10/xml-exc-c14n#"
        },
        "SignatureMethod": {
          "Algorithm": "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512"
        },
        "Reference": {
          "URI": "#_KlrvIqsRSR_lmeSP5ULVlGLTesFFb-OC0kUTs9-20E3WQPIkDsqQMAiuTEJM0-O",
          "Transforms": {
            "Transform": {
              "Algorithm": "http://www.w3.org/2001/10/xml-exc-c14n#"
            }
          },
          "DigestMethod": {
            "Algorithm": "http://www.w3.org/2001/04/xmlenc#sha512"
          },
          "DigestValue": "E6YDbMaZddP64Vw2y8bmMM21nwAH+aT4ke6X8A/FNJPeAmP3wecbLWZS74xxwqJ3zOwD7CWN09Ny\r\nrr+DVU2OUg=="
        }
      },
      "SignatureValue": "\nsVlOTiy+0QDCuK9eIXK8ZGQGFZg2m4br8cbfRs81HFiWyIrF5BALKC5ZmfaI5D3roMRl+bVxQKks\r\nKFTJwDs7a1pwVf8RvhupswzWtTJyo2Tc0FkBEJ+ZaLTfjOUiQQuTK/E10rzeTo0ewilrs4OdTfyr\r\n/kjchILHhfqwAMpE8wqxxFdboknlhFpWH61aOeeGk1pj/hnArtirATihmzbtxxyn6ZBBH4UDqpQP\r\nGe3Wy14RkY+KGXvv31kaPZ8E0Ogn0YYLqcb2hv8gjCDFx9q0P+pj33F8Dw90pEQAQlrXad//jrI/\r\neBoGCgxvYfzm+DjQmvpuZmsR2Q35ZWUBqH72gkyTG3+4NDmF9ibECq85L7TMkT2hdKWX/NbVGO+m\r\n5QRpB+sxu5oSNvMH2KNzDq0ASWtGWYVw+edaAV2O/Xa0/3DsCHI/nkg/A0UnLZZPahf+Pu0wZ337\r\n5DiCuLg9mmuk8Wy3WYn3FczSAPjyzbU/Wpdsj8X1u/xcsiGt7KUpR37hwRJS6VVcNEt025xzl3+E\r\n8MoF1e5bHQJQVif9YWMIcPVZZ9YVocIxaSiUtKmGQt5e2iLsIY7vJ7g1FFPTUHtoGcT30bM3e4ff\r\nvYXOyXCVjOn7LF7hfQCybN+esyk26eRp0WjL3Nxh6+OGWwIh5IhPz6A4JAmOXhsHfIthjK+o9mI=\n",
      "KeyInfo": {
        "X509Data": {
          "X509Certificate": "MIIFmzCCA4OgAwIBAgIEX2eUsTANBgkqhkiG9w0BAQsFADB+MQswCQYDVQQGEwJFRTENMAsGA1UE\nCAwEdGVzdDENMAsGA1UEBwwEdGVzdDENMAsGA1UECgwEdGVzdDENMAsGA1UECwwEdGVzdDEzMDEG\nA1UEAwwqZWlkYXMtc3BlY2lmaWNjb25uZWN0b3ItcmVzcG9uZGVyLW1ldGFkYXRhMB4XDTIwMDky\nMDE3NDMxM1oXDTMwMDkyMDE3NDMxM1owfjELMAkGA1UEBhMCRUUxDTALBgNVBAgMBHRlc3QxDTAL\nBgNVBAcMBHRlc3QxDTALBgNVBAoMBHRlc3QxDTALBgNVBAsMBHRlc3QxMzAxBgNVBAMMKmVpZGFz\nLXNwZWNpZmljY29ubmVjdG9yLXJlc3BvbmRlci1tZXRhZGF0YTCCAiIwDQYJKoZIhvcNAQEBBQAD\nggIPADCCAgoCggIBANP/gTFt+ToahE842QQQyDr1PPXAECy5GeZ7OcViVSoU239EG+edTGgdfvJn\np+Ek/kjL/vgG5X5uMIlvMcjssu7xuJZndyJUPrWQM6FGnEtJ1Qv+tKRvmD8ZDz4e/kFjPXr+9/W6\nz4jt/TOGcVKwiB4luYD54GWGCoqoX55WCsJzYUVrMS4paHoftp/iUVRyQf4DSijtbvZl/Ypbmy6i\nLNVOKhFbDaFw5j1DFmO6pOf41IFMxCANBUhq2PHignZjSx0eGP0675o/i9QbLyc59dVwnUhnPQvs\nEQWyAiiS+61ZzDkAyIVTbQdx/RyQwLyFJh4QO00aocLpBud9FsYuSN1wLKuZSjbbM/ClbiIgs9F9\n3tzijCLkEU9Tx62NhwExq4P5PY1O8cEl9BX4ema+aoyvYDd5eifvI7iTvHv621jxqT2LDvCQjlNA\nOWelNOGsbZuCiQJjln3I1fscN6SB2cdxYebyVCQrfhsZZv7iawp8WCnw/wd/XVyise0lt0asTZWy\nhaOmTFv+nVIbHLcbiFzcEHoPfA31uQa4AOb6mTC9LIZDcelEKJIRiWuTv7hrBw5EAIUVhSdoGv9M\nReYq+vhMtT8lHg8m+IK0iEOfTRUs8x3YJYt2GH9+DC6onyhCm1WdEKuxYU5lPJVJgyg0ejxk0pmt\nsjrHdAeEdbZF4wrPAgMBAAGjITAfMB0GA1UdDgQWBBSz7x9rg/0/MDKXc1gISohsoUXXHDANBgkq\nhkiG9w0BAQsFAAOCAgEAVy6kdgxstQCsWvtq1PuNMnYanzveW1l/jrH8u80r/tBQ29yLjlvSj4e6\nMQdA6EIKwFsKjmH2ZrdpqXyUdFjlfF2LYgpQamf7b8U6s+oFsX3IYRj73fDGJbvlE6gahv4Euadu\nHrivtfHpgtNXdVF2ZrsrY6LbgiMPFZto938M0xmdxDxpGXp2Q2PXu0LGXXptidudikcvD09sciAP\n7RBFPmxSQG2o+RgoJKAsvEQnEPCfSvhlK/SZR/iBmYyxXPhLCBpszFq91xXrD0h2w1KCXKIWTDb8\nw2JuHs7P1PkcmrqSXXYHIf7dBNFKU6AuA/uKteqOO5i0hh7wL7gA56YDghbFGi+UHCft7TrWssso\nGaQkM/YLaFApayHuqQ7J7F5hQvfkwBErPR6uIvFyHMjL5NtoFF2kzVTDx4j/uNzxHXk4XqDX3ZDw\n6hiQmV7Tk7cJRUqU+q5TkYu4TgkBeE1quscVK7gsfFaWv7MBTIT4IBelEFtCU97cNzTqy6TTHnbo\naTqRc1cqN5cA6tebLp+cP0+pIsu6RM69eive+RJJBOMh7Dfd/EVp/EYPmc2AFiNVNMRnq4SVa1Ac\n2nr1ewvm5yJAkefV8w7TNbQ/QKKpPZRfgCH5/5bWp6Q9T3T+6s0ydiIUJQ7fLMR8zEj50+UT/iuf\nOF6TawGAOCgZSsptJbU="
        }
      }
    },
    "Status": {
      "StatusCode": {
        "Value": "urn:oasis:names:tc:SAML:2.0:status:Success"
      },
      "StatusMessage": "urn:oasis:names:tc:SAML:2.0:status:Success"
    },
    "EncryptedAssertion": {
      "EncryptedData": {
        "Id": "_3f4f5a72328705e98056f3c72c99ad53",
        "Type": "http://www.w3.org/2001/04/xmlenc#Element",
        "EncryptionMethod": {
          "Algorithm": "http://www.w3.org/2009/xmlenc11#aes256-gcm"
        },
        "KeyInfo": {
          "EncryptedKey": {
            "Id": "_fde6312a14823bf5cb2f95d7a2a72773",
            "EncryptionMethod": {
              "Algorithm": "http://www.w3.org/2001/04/xmlenc#rsa-oaep-mgf1p",
              "DigestMethod": {
                "Algorithm": "http://www.w3.org/2000/09/xmldsig#sha1"
              }
            },
            "KeyInfo": {
              "X509Data": {
                "X509Certificate": "MIIFhTCCA22gAwIBAgIEX2dz8zANBgkqhkiG9w0BAQsFADBzMQswCQYDVQQGEwJFRTENMAsGA1UE\nCAwEdGVzdDENMAsGA1UEBwwEdGVzdDENMAsGA1UECgwEdGVzdDENMAsGA1UECwwEdGVzdDEoMCYG\nA1UEAwwfZWlkYXMtZWVzZXJ2aWNlcHJvdmlkZXItZW5jcnlwdDAeFw0yMDA5MjAxNTIzMzFaFw0z\nMDA5MjAxNTIzMzFaMHMxCzAJBgNVBAYTAkVFMQ0wCwYDVQQIDAR0ZXN0MQ0wCwYDVQQHDAR0ZXN0\nMQ0wCwYDVQQKDAR0ZXN0MQ0wCwYDVQQLDAR0ZXN0MSgwJgYDVQQDDB9laWRhcy1lZXNlcnZpY2Vw\ncm92aWRlci1lbmNyeXB0MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAy7CmpMU9eoP8\neOsxtc3IAK/c9GFat4nCNJyv9e1QXJc8GCla3QdrNcnRDPx2Yvf0cMm1OMlnSfmCfzSh5joglJY1\nBJTKDa8m5JYVZQE8w0aeNNs7ksAZBZ+dXDVN3Tak3qt1lvOYWjcV55BEpLeU6TDBCXpUXKpBDgym\nIeG7oAi+sco8yFd2XbZqHE6hQ3B7LGPvMhYtzitNm0RvYOPbc2ush6MoFTIsxFSf4V7mos5q0iyK\nRBrKWs6ldE0Hj/EZavC+h18gMIRGfccTbTlWvihRUG+dp5asQrMfkMcH1wwfZZBWVnv6Fc9JShUf\nYTPETXvyUpU/NgRFcdN6B7FFQkIqk476Ugvi6nZo6smWoY3XzetKe2ahnqZ1D5FoGa2vFj4a6EGH\nsie9v4BstB9nWZC5mrF/cSopQLSOeLc9V/1l5FwM6u4NhKkd3/0Gk9MYjlwTT/nlz/j/d6NctBMl\nx5EnGhDPxwJUf+Ca+XbI93z8/mFnhig9eroj10fNo4EY/x6agcat2PHxpTVUAbuX13q2olVE1l80\nPujTw6chItPQbzvUnxR4ynS0HYg6FgQym9g3IoweSOGMffOh0qbgmatFWerL2WShaQYn91cHlcT3\n1WPyLjXWynReRnaNPPmEHMMMcWqHJrB5NTthb5xIBLi7hOl+QlSGqmoTnqBFyz8CAwEAAaMhMB8w\nHQYDVR0OBBYEFF21oZNP2zq46biZrKJVF0fE1U7mMA0GCSqGSIb3DQEBCwUAA4ICAQA4ezcCHign\n/yQo16ldwsi40O7oeQekNsiUvEnI97LQ6wXWN/hor4jZguqu1xfBqMue9htbIVl/1ck2/e+rM7vF\neQNL9SevuZsHsCAPLw7wTgJFuShq9MQbnPt/o9+MKYLC8fJkljZcl4PdNRJFY5/nNIlGDWGho89C\nORohxbdppURQGv3QYQGjxXuqsCunVASlstpLpgVl8GPkRnvCQIdWr18dV0G0O8Tesv25WLrud5Np\nM9QclPvPMaSJ3vzkKYYlyVxrHC6JhFLobrSdCv7xZUeOytFU5zwJl+ZWv2SEFr+/g3ZCAYt8JhD4\nrBWeo3iqrSXTWIu2RPJXIgBPYfSayRT3zdSP9xcDmYct6vXbZMyf1ICyTWPUT/rc/9UbejGY0nNc\nRx6nDhLONzA8q0CukLc4jFMjamjTlzWqnWuLcKwfD76+X+Nq3CYjqBtcrJ64O3+v4HN6miDvE6fL\nsTvT7JtYvlnUFvNpMREB1NN3c+O9lS5z1eKYHJabMxXyQKJd13Md6tq9xnDxjKD1lz0DluGomZDL\nxzlEiE1vJeUD9Edgi091D3qsC3kLwuwPiULJor88R1D2hmE6BLC0YfQfB4IvQRcKsoKsl4WUVqZB\nYQMBRK/tmts8yreDLOKGlIThu9SkwLkC43r5xGgfKK/5shXzE1SKI2NiOCgtgqrOQQ=="
              }
            },
            "CipherData": {
              "CipherValue": "i65FqK7vkmN2kkMJNtKSg8C2c6fQIPUmIJYCMG/ZNfS6JWWgBI6f46jxbgZD9lLnoCdB5Cln0HsB\r\n3XF9rOTbLQfeR7wZt9rXN/oGdmVlmBBuxEIdXT/CrhKhfxL+QmKeF+EtYDvYvy7qZCBMIoWxjAEa\r\nFUQ/JHSRmd05JAUp5y4nYUpLGj2o7hJ+3bjuKKBHSqWHhn7W2rJkgSqnk056jy72wZYGbmR3lGd9\r\nbk7YpAiUkHUrwF+2cebXwf51H36MDTm1MW37aEnnLEoAp1RCKdCFhiJnfNIwwbPrhq1+/cCkGlPE\r\n0mmsJC6EJZIXKNyKbI7XOM/USC6EYc9P/RBQeASrd0RpJ6OZq5p2Y05z/77Vy3OkCLDSOEmDvkKV\r\nh5TpZgS0pitqhz5XWXTpsDRKs+9bVRYXGI/HasM3ul3REKaRWFfZcR+AbSzTn7BVgbX1JhCo0Dgn\r\nGvRtXDN48vjQpOCN31HcXtbWeylNB7gMXME17GPg687vCj3R+K+IftR5a0d1eKx61DY89PqyBsQE\r\nEv60XQKPibCxA4rVsGxPbDsiJyICc6p/mKyYxl27RmViz6Fm8akEeq3wT7tV8hBmx5QCtmBK37G0\r\nlJEiyUW36i0WVtS0kCM5w1/Cwn5xWqqPq+GYQLXxmgjaPDmmi/BbFJs75p6CE/F7D8dGoIAa3Fg="
            }
          }
        },
        "CipherData": {
          "CipherValue": "aHpOeEaVBa+tLfcEcadykweXjcopOfayM2UVjOD8nqhN6gjWdpxLjeBYog+Mut4AfGNjeQvd0fWl\r\nhFw0QNXKxZeMvkzHG21AWD5IAPOLCwwhDIugLF19QMicyfydeJJ3rCZN1kOEgnukleKz3zYqHaAf\r\nAhhNtPTwy6isN+bF6N/QhXe71Unvx/PFnhWNEsOhrdApHSfLH/X8SUY/2fiwxLf/60qsS1ENMFTd\r\nAU1XJzmJBHAcJBJjmfMJYObjYdpXpQGkQsPvJH3WVYIGFNxgOloUN2IwU+Pb6sFKz3igkTcd3p43\r\nwmA0hObXyoZUB55Go8bPcDjzAJQE7pAlx0rJqnwQjmbHHiN/DoVRCpiWCT4Bt4DhYEYrwFNd8Gz+\r\nfef2hYMOyyKcSE0zvVcHnP4mNBxx4c245oT7S444w6VzHrvzEQAV8ChjrB4Fu6uVklNBb4AMRxlc\r\nEWaAfdIR31rXZdob24wBggKD4LQtVbK6cQ3ij5u8cN2YxFSiWKGc2PENpMVguxNkNpxUo2ZVPKAc\r\n18Dhqp3pizFn9Dzxwn/Zw+Vgc6TsuibwfPjjGaXMj9nmNkZ7idy4AHeISFfXm8xzUgKE2mhOV9J/\r\noY8hM6v2AOScCbkxOp0RyyqAyiu7ScOGy8T1tlTbj1yihwvGeVsJ+5HvwDxiJKg+89PCjey4ZfL0\r\nvhAxy+TrEp0wNC0J29CNjvZ3h0kUayVOqmZm2Lq7YH9t+EEN9HZArKHevYbUZlPFaUqOETFdUpC6\r\nzvlVrc0t4ETXxOeeaTrImZtGm37cjPOU0oYqsgyHyPsEyntqS3xEVG87BFcO0YKHsvKdOGfs9/Qg\r\ntf1V0jQkhQGWmyejXx7gs1jbEpPQFBN/v7NxB7+EdIc8JJxdg4GLnDANQKRovsRU27Z5v2hZTMJ8\r\nUBIgMrKVcvRFxa7j5xNDbEE3QnntFl5rvSKmlNP46rc+9auKXVIHnKvUHdW8IYT/3VOTcmWBwhbS\r\n/aPW/18bJjfshLwmj/LzgC89+Q829t90f03mA0//QaSsQfOnHUbmrE1J/7fCfPyIQ3tSQQ4ILrsB\r\n4Xfnec9Z49ZmIrZLlA29SNUSCZDZuF/kIQCkqaMHogMQWKy5V7r5zaf04SHPzlN2W2oRscViZL9b\r\nNUgOLH2dWPpS/OrrXL4vNKwMXTtHFc/1d8w1SBij8hm/OPBuXxU/Sz3H6uEeReUZVGz0N5N2QaDZ\r\nkGegkexlE5/jEEHrpPOkssg8dHajTZPi62auVgSQXoOS2TIE2BznhAushEhADtpzUOgV9TueUJuU\r\nlTCwCBQCp95Z8hPdIvish+xsEkru1FlM6CD1kfEuG9pRapBF70Oma1gdO2ekLwLFw1+BeB5KKDCt\r\nf867deLI+A6mQAZ5AVSUPsERkijKSq2E//6YWjWYUK14q1ZZCgFqutHcFOtJa/dJXygqbiiFA7C3\r\nOFsqGinLsL8R6WwWY9WsX1QLSwVgQuYb9H9o2S/YHJQmSq79JfzdrmC+P3kKAl6Oc0qGIwax2zbF\r\nkbfHaYFRe/iaWe3uKEyaBUwxxpEjTl0XCCCzlyFCOJ3srIC5mOQSHIabP5du8ILb/275JX9OQpHW\r\nW9vbwqCpEpVke3xYmO9zy1Ol9xrW8LSerTtCAeJw9uq5F/VOz7SGeduUuXIV9pr+OQLSADQT9TQO\r\nkUTKfVlhJJRZlTtqnVESvZO/ueeczo0ec51EyVrtMur8VqjRfaA95dM0+IzEEvGtaJ5HnnCpKLsN\r\nsSD3C8nsx5ARRNr+t9f+d2OGWkx48XOT71+ounp8Mrxn/XCdqk5a6lgjXFXnD6j/TiARZri/b3Ez\r\n0U5u7E/k5BSbv4X/JECDqaVQg4Q1/b6iDbQtnf522kVPML2xKTD2nrpiXjgTxtTnP/cVa1pqigtc\r\n4nf6BhPzjPc3WxG2RajZiALdHxWKifmtODv5PrQfX4O54jvpOSR8e0F1vwcaSXowpgW2O9doM6bI\r\nluiE+Qr+9v84gwSsGkWpYez0QMkTUNaHCHCd2Waayr/QG3bDa9+CtTNEXvKKdlUWc8r+V+VxxTcw\r\nD2YXsKe5Pm4At4G9Qk72JIWHXbQgfUxkQgCKg47IkALMDssIwD2Ii5V9KUkneK1R0xVKLTPIQxKu\r\n9xdstQergEftcFJzB/gBHJrlLL/tyNQ9p+Xbq5rLQETXI4sm9Wd/JDYfdJegazi2VXTlh72ehrck\r\n+saVosOHiRZO7TKLc/YsDxzvLE9k2EwKRgxGxrmPRUdU5HDpxM35HcSBlwjRedcgpRoVjC+bJ/jx\r\nEUDnZMhxwub3f8vxYSDJQQtF7lGw3n28JIUhMvDuE/J1+rIqHs+iktzsGQ1ZsxTD8U6keS0KzUgK\r\nwryOvxBHyI7DYbLzHNK5R1KC85JkBGLHXUmDfBsMs2VVWlMqffnLR7N/t6KIN30GlCENjFYZ7khD\r\nBXQGclUpmumGMuhXHscMlyy3Q1JlsedhHICJBJjKjjow5C81XxXMFlNPs5+KJBYzc9puorkzPzlk\r\nITyB0WSKN5bEu1WSqHC1VXMtcosyQOkfMWXnw2ylnpIC7VcENvg5dqIV6x80Y5BPbP7VvIEqSu2g\r\n8FeIMF0qIxkVvgWSEXioKUK8zu+TGTMjVEreQCEoSYoznc49uTramQfgUd/KM36KixTw6Zgd6e1d\r\nSxrXXK2AC1xRqKqh3nBQ21bihVGoqWp4PfrWnwwZjQI//dZ7WbG2NmOMyeYuyqVjKhpHjBr1nt7x\r\nK7xwfjakPBiPIS5zU73uDEdjYkHh3OASQG5/f3BObqM9rX8wc5BJOyIxJuahyolNL77xuXFGZBRT\r\nketuW+5oLf48ZNBvZiHEZlhdHryesHZqbRMQSF1oOaNsYAa6pirzltE8OuwQKiyo8nutwUwnmRD8\r\niTTSx8MLJSeOCCEYpyKDuOA/4W3dojuHGHI1edeQHDs7Z9HwODjQz+tSmBe5dA9Th2SH1DyTwPgH\r\nh/pfI/rLnFhE/P8iHggshqaOiU2RFpHVOqZECPvoc95p7X651ZlcDHC0xnraddOh0D3l4yWJp3EG\r\nWZnQFRIh0RfheDm6VnjVddylOmkj88ZjP/OUtF5YEBTMGRXb4hMUE3Pgvdmu8YphWM3bTjE6TkuL\r\ngzUScNzznHqZhuOlfBylXW9G0LwEK8CVEeI0LIPboiCE6voYh2c18n3ClfqXPf82nMPoAfhgt32X\r\npw0Tfo9BMDUqgRQMVzktvAxRxLN3x4V2rhNegYKmN3zkIfJiKND9YJzZALLOod9UFK/UIXW3T+GH\r\n8YQO56fP0+3IFyiUCN3OBt9Mt6mLxpedpvBJCqFdtptazqREUG3DF8Qo+wQidkPObaZxSXLDZBfP\r\nC4xZPBi8c7JJn1gEq+1MiI+zJw+bxXgaL9WE8ZyqZ/nWojjeNKlkEkTaqnxTOLD2Rkn4lULXEEsG\r\nPxDjH7RRzp8eCB8rEPkbW9sVdv4rnVRq9mYswncrIOiO8MHZFplr+f3WxW5BX0+yDg9oznB9I/XX\r\nGdGRIsI5w/MC51/VUHz8ca48jBUCaqNQPDjkYeoiIiysP69o1mfRguNRemA16ixBolSfCtFeqjU6\r\nRkvLhpP0uKxiVm2Q3/XtBb2b2gM3vmXMjmDTThWXUzRkAOTmpwjSlk/6dd2VhEXoqCcuvW/fpHHY\r\n6PVw4Dnfl3S51ooPZwqojs4sHHdHgyY6CkRQSa0NP+2ge2qZc7vet9TTMbXIO2JVpQ8+bzC4sgd0\r\nw9q8/XUwyGwy2PA/+RMltPXgWbsgYtKpDGCxOaegyMaLy9Q0Kae2tLouTxQxhhnfydB2thdu6/uG\r\n83cfHcJ4faCaOlD+SBMiplCwN3T7rDH/XKZfCfNkuNcPz1Zi10QCxtj+i9qdfEJWZONrFvcLeg8R\r\n6Jym+7kpIhFdxqJ4DjV8rxt/iu8Ly6k4in6t1RpWcTXAvupqEBybhTNgU5As3J2veYlrNsZkdIl6\r\ns1bKV6n9d49RYJrXDQtdCAJvq7uoGNofQNbMqykenCN3OG/SjBuxoSjlfp7Rlf5dEpfixGcu5oRB\r\n+beMoc5h8y3pk3EF2TlsQdqevrC/M50Pf3q5uaZ0AfZbGtL1Vrm6J0rE7cpw3bHHOjPjGifiitHc\r\nVG8EKAQsUlDg3xgOXVOVbr+5qRWMS6+oaXdLq2WLOdvD91jzxQm9neRZZnmmucE3y7Fg28I4Bccl\r\n37LJrIPnuNfKoRD2Dv4Im/UrxjJT/jggzlHrudVI91uX0VnpgscwvJ3O1xEGOZ4rPiXK84/G7VYd\r\nfcJ+1lTiXyD2TvgcMx+O8HFAHkmxEMR5mp35h0roK3k5XKkCMx5pKM3kkPhvUwYdudFDhBfvJsIC\r\na+LJVS8VIA2FQouFd7rPHENRLxCzIDJH4nQ+zIIw0vj52EK4UYAFNFQfMAWkd9V+nTUu48aJKsYK\r\nBJ8BgwGA/WF1Ejj75MXPI4W5r/BfJCL4dPquB6VQDNbhI8SMlvXCbWte+y0AuS6rEaIUE79AhT0d\r\ncWxavnn2Optsy82MRbsCe/xKDHTQXUUlpq5cyZFBnxFSoH9ThqWtTSUt/BVIwhzh9bKeUp4txQZs\r\nGCQQMwxmLARuLMyP0lbslT0WyjElF2mww6X+X1Sv1FX0n9qVcLTTqJxthDpjP/Oa7kRKt6mKNZCA\r\nFbZd+GlKOJKkKfJIrVX7doz3T8CgpLCh1w/++cCOiG1l+G7qyZGJDr3XVNg49LfDn5NLhF072pst\r\nXgXvDdchp6FoHCYdBquGu35LrSGnZgSlIU2qIZHPaUM3EpVw3PWQW2/VxWWw4X5YJdI7/rYmxHYQ\r\nFqLCy0dxLgI0DiszCUOw5hcAQd8Fa9O3yVJuKIsZynbuFgypOwQhpGrO6ACLDDGLJl4rsIwfVo1g\r\n9OJVMtJnovikqfCwoMYczPlacp9WCmjq1dq34T/gELxQzfJumxwZPVNuS0V/3NaDEbTeDh9ANwT4\r\nSU+qigOhogfu4zpntn1CYQS+WzpvWo/nvxbhAVuGdZLbkE/ZxFpXwiDTznQ/zjeGynHroWS+hKql\r\ndIprvUqnAfol+CSDq9qe0r3Xicit0BdV/EGy+d8dLkc/k2Y0oO1wFHQx2YCbU/n9Z8jB9Pqa2Yzy\r\nPgHeQpf+wc+6HaafyHnrj4RM/T5mPTtQVs8BxTzcGK7qKPVFBdwIjSqEKSUizRWN5C0ipWh/n0Zb\r\nESwpSxRYDXdRoCbelZgNggMp2ZfKahv2v6evk5UEAypSF07aPIxTYrZdOoHwTOcWRJX7vGN5MZjr\r\nPURNq+PG8L1tjhrW+qghPQwYu8iYVsnG53mIw9l+OjED2qiVqJU4bAzXmL08RJG3bEYlt0MZUr6k\r\nj2mAyllNHh9eG1JC6M0bWhP033jevwE1eiGJYgmMgZdtMseBOhw9fZuoYE8UG08pBUnNxozVBV4L\r\ncuJsAaFLjxmfQ38TLrNof2gtvP6gYqFEmq+qQtgAZKOOsJ3b+9FGE431MLrhT75kei3460Z9vHOt\r\nXq2/OTQW5QAcvOrFU9eSkQr61sxBI3lQrXL86qEJRUqfe0gklMuVEKpJl7oXCrl51vnL91BXEnzV\r\nOHfeBC5JadEccnkDPysIiM3QtkgtHFJBZc10krD382xOyScFvc6LmS6BaLWcCdTJeG/neuATw3yU\r\nVRg26h+2+c5+1sKEDlsBRI7UIFCkfwFvZk99XAOkXQZucFzon+19UjIpis15z3WUi/Ob+t/ytwXl\r\n8gRlGgkVF7BQ5+Q36FWq/lxKkz5pYHkvOcGoUYp/W9FEt0Eyc9pr0a92+xYGHT68G6T9Do6NohAD\r\n6DguqYClKSp5za/byPAfj/4B9IjVJgRqaQBArnq31NUnaHXxsM/Q4vgXZkqD4dvmgRymlDU8gO55\r\n3kHeh8MD9SFHJrCjLgXJ3xDQGfaClEFA8bce/2Y9Ifw0UJI1XrPPHkfp/tpPgJedPXt8TYeM0J/j\r\n1DpF4k9VSS7sGEu8BOJZXPg9FyVHNR3KU2arYjNbpQhGkHtDDq262YqWPlRSrXj2QfkUWMmMYgO4\r\nvnLHD5I2T+4FFjLXv9hlMAq8C8cVF1sGu4i/sJ9TH8PjtLLzH8TW7lp3vEDTkR0hX8aO3jSebWMA\r\nADjZpxoZ1Q+58pJjgOmNb2FuR1eV/q6T7wbimytOmjYcXTqUup/hiVee+bOjVBVp2p/p7A1ZTK7+\r\nErsli6RuJNPmrxi24iVxOnXyvZbp0ynWL1LRdDjcceD7j5V+cpzntUD/2OGYxsiiVa6jsTpRkDKY\r\novSKn4LhOPNIfALKNbuyyMvOI/1vPquyO7A7Kpm+Ua5muguoSv7N8eGpIKCauQo06IMxajnUflYo\r\nER+mT4VMPRvH74Pf3TkdwSljkUcPN5mUjj2dXR2rhwZMBO6xysHIrd8XgEbdUQpnqE18iejtlpHF\r\n73CGZP0/eukZu5+6jm6kldeWIBVesWi1uo6F/VDlz2dF4+CZ8CS8Vwi/VNmU1DwajJ3hIB7XjD38\r\n2vmylOP3J7l+5J5CqZpDlOBKalkyg4hO6+wCjCrwWuoNoOnqfb3ogDx0VbJW/14PmrgqbKEPRn0u\r\nHnEHicsyLxa3EWEeEtCyPTcEd0Vn6yJ+LNb63oz/hF5cjiwUso+XtQ2awY6ItQxhlqZKg4/8dosx\r\nBdiKby9s5x7ECdcvoBLnvyaHJf+DH5Awfp+/wcW0F7uu1KsiDbCdJDP9RnGGD/h/KDIYQSmhMT9X\r\nR0UdiIRZvDNz0C43DKdetwtEy7jputXbX9SsJ+5Ef3ZHkcBSJmfvOW7SiN4Dvx3Ypk1zgjTJhFf1\r\nHjhdJX7j8mt5GY8N6CPMXysiNVVZHdxyqb+Kc5c2gg5eGxInQhtRrmu2kkBcZFBmz7l9Q7GbmJEG\r\nBP2VJhmq0veFBAIFeG6ApimsC351vLVR66T9l/DpNKkO17EFw2rH0HzLkiTJkWH3M+b5dUtYR2wF\r\ndiFYCMjU/CBPcc48pXdFjABSVnYfundsoqWz2U/6oZNT1O3OHH1sFPYgaOQkzLJr9KXipfeZW0gI\r\nCgmoCnQitZkmTVqA7aa+BxiTTWROtk7RGSPe3ZVFmcJhGDLiAgitavPHeyZzgWIHjv3CEqwMVKxS\r\n+1O6R8Z466UCYoxPpWBUU/Sl6KEnHDqePgkdHATxlTJGtO3LsyHTsk9sTNhA4EQn6t6v882BWNTW\r\n0HNgg/k/+f3s+9BABus3RbSO0Mss3PDHJgf/K0CYp/6uLvW1+OvVGMNp9YgFIh13QJJGWxpyjsGz\r\nA02pO6YJfbgw3sv7Ls4iVExKUPRKol2JlbYPHQr0SL5cvxBk/3UKoQUaw9lcXiCQD6E6Up6i8dP3\r\ns4s3NzDK2auKjudsKu//P4tSq7w7AX1OsUwuMO8jItV16pTSiIF82zk1YYt/qCdqJSGsydrQUpMZ\r\n7efVpWmHmZsQmquVhbPQSTjkOZ+EtDpt/WKVDEW/L5JiUAANEYNyvQNsSsG+jKuKy44IvJyftwmD\r\n7K4srns1WnQYmyBl/BKjlN8EYnHyMMyZ0zt2dX9TlkP9PGo2903Wp/nfpG4ISu2M8J3v8hzYo+kD\r\nob9bLYRAZ5aE1+cc9OsVFdyY/x8e3+O9qbR3xgn7IWpAKMQkbg2Pcx+rnFHWOP0d8VrFeLI1L7QK\r\nXVcBXYajbQCYnxfzYh/btiYJwYY7QZOafXoAuhFo7A6PNIQO0SaVGUIC+keTrbbEa5BNXp961fXc\r\nigVFYz91OjSQIgZm24Nx/55qEWgbiEWGF7hFlfJVzF1mOZMU07sJfyCJO9YQaf+jg5mvDG+nmc+N\r\n4UuYauqpk28b/rt/ozt9I5yN+CTKJ2ebC6XddoZhrecM5lVXQfTl8sNN7qwdzh3m5WsT9VHWPzny\r\nrLtpGIzc+YcEUYww8GNx1TGPpB0LnN5IE178DIm46dWtGtKIMWX8IL0cvniEQ+etjld/+LHMpTnY\r\n5P5wj9FOQpkdgwSDtKdHQTZYVYXQoultiOwMVzw2v87qT+HDLFDtQ6qG/AdZbQblG/iCDpW/zing\r\ny0ZO0TJda96vE192WpOv79u4IdtWmg8FRTO/vfNtm117GfXhF9kzKVMt3R7KbMCy1EP3UoEtTn15\r\neHKsSOlgmkLfaTup7enCRooQjxtf3Ggmme9IJREr87dlNjUPPSS4OPSY0jSgrDkB8wE9LP5Dx14u\r\nDihcwCI/QSBoeU67qu/3BvGUUxQAYGde7LcJ0PPXXkagjNMahDg68LZPOd62uJKAVeeu8c7a4vcb\r\nBm16dz0Gxewnf69MYDsrV2hMPN4rf/gXyxA+LlePW0YIUfRzZ2tBrLuvYS5U1P027PxhadJpVciZ\r\n7Bdl8wFWeKy3F7Xs3STtEQkuwLdusSySn5B4oBcF/XRy28Q+tSHtfseEefmZqXAdnc9eamC3FAtf\r\n4bjqMjLwMcUO9pBW85vr3pYiopilqASS/+hH+RdBxuCo0QhUBDXEevFrHcnz3a1ehY1RisTlwxgH\r\n3P96oqgaXBoqLtjSznyKLjfkZd+mPJkKc7c3rjyDNohDF5RT4gs+dq9XVX/VYy85LZ/M4bgp77FW\r\nvcU2lOs2cc0ndjTLaZwfZXIV3dlppQLvTMKSEaGU37CLVSAfjZGw5eHztbScjetFdzto7RcOVwCz\r\nHSl4bpDxLN9uwFqI068gD3WOojcn2yTY7mZolkfySEYAwgyf1T9WlwNVK2U/x55g0g=="
        }
      }
    }
  },
  "authn_request.relay_state": "12345",
  "event.kind": "event",
  "event.category": "authentication",
  "event.type": "end",
  "event.outcome": "success"
}
```

Примерно съобщение за провалена автентикация (saml_response):

```json
{
  "@timestamp": "2020-10-23T14:36:09.447Z",
  "log.level": "ERROR",
  "log.logger": "e.r.e.c.s.e.SpecificConnectorExceptionHandler",
  "process.pid": 1,
  "process.thread.name": "https-openssl-nio-8443-exec-6",
  "service.name": "eidas-connector",
  "service.type": "specific",
  "service.node.name": "25848971-d261-4686-ac78-cbda659a6c9b",
  "service.version": "1.0.0-SNAPSHOT",
  "session.id": "597CF877D3E37F5E9840601D1A5E8D11",
  "trace.id": "c7f421136e5dfb9b",
  "transaction.id": "c7f421136e5dfb9b",
  "message": "Authentication failed: LoA is missing or invalid",
  "saml_response": {
    "Destination": "https://eidas-serviceprovider:8889/returnUrl",
    "ID": "_3f5c43c067a79b2512ae2d41a545befb",
    "InResponseTo": "_8c0384b4fbf8b3e58cd4637d7807aeb7",
    "IssueInstant": "2020-10-23T14:36:09.425Z",
    "Version": "2.0",
    "Issuer": {
      "Format": "urn:oasis:names:tc:SAML:2.0:nameid-format:entity",
      "": "https://eidas-specificconnector:8443/SpecificConnector/ConnectorResponderMetadata"
    },
    "Signature": {
      "SignedInfo": {
        "CanonicalizationMethod": {
          "Algorithm": "http://www.w3.org/2001/10/xml-exc-c14n#"
        },
        "SignatureMethod": {
          "Algorithm": "http://www.w3.org/2001/04/xmldsig-more#rsa-sha512"
        },
        "Reference": {
          "URI": "#_3f5c43c067a79b2512ae2d41a545befb",
          "Transforms": {
            "Transform": {
              "Algorithm": "http://www.w3.org/2001/10/xml-exc-c14n#"
            }
          },
          "DigestMethod": {
            "Algorithm": "http://www.w3.org/2001/04/xmlenc#sha512"
          },
          "DigestValue": "xjP1cavpaoVsjTBt6bvlGI1u6l2juXQn7Yr7kSRm45Xe2xnjKNciFGvMxjDJlMExo3N0Geu6aVI9\r\nNiGyPzhSVw=="
        }
      },
      "SignatureValue": "\nezeIya/f3erx9a5SmyI1Nd4hnjf2hEkWH7NAgL8FJdsPcdArM9UaNTW9jcsRcMO8tkHU6UEyXwtK\r\nM6OwELnGtRnbCEECCJLPcj5OETAXsgqkBGoWJ92d5yqEvL5HbdJxYcRc1sUkiYiTT3CYCqZ60ubU\r\nG0DQva8agJnc7YzSbtnZJsh/GEuPuAEe81RRZhVNDfkBjKcBQcrLwVMDpU5lRyo+GckCT56DiGzt\r\n8Y9/b0VYOZIt+uZLLTXmmQ6bQtLkE+jGcNR3Jeh1gZ8bxHlAVYxWwX2fu239gQmRJXzGxrYrd2CP\r\nJhzCEqaofFUE8/W1DRmrpbaLZdjSkCG28DffIZTHFuSmENRmIS5K8eo6Q0eIz24N690gzHJqgMuS\r\niUAO7zQTzSPJJrPX8NCorageRcPftjYASgco7fal3X2w34tUd2vySnluGN+/PvtyyQUMdVkko6p5\r\nVSZRy/1RnIbAUeAmg+RRZzK5sCu0Jz2rAPWRaWcTdh1iXbCFkLVAFPcIXhNU8Z5BqZXki71j3VqS\r\n1C0KSAWn+yll2lT0uZ1ZnEcvm3hu9q900iQBvmCtlt7OE/3Ylx3HEfvlsB7ZfLOZ3JQMzpfq1Cyn\r\npFep+mzbgGLQwFMo2SXgbHQdAHd+RiP/20oLSUt2e9a0UoDVIyAcizyFJCP5Co7ZxejDtZZAiv0=\n",
      "KeyInfo": {
        "X509Data": {
          "X509Certificate": "MIIFmzCCA4OgAwIBAgIEX2eUsTANBgkqhkiG9w0BAQsFADB+MQswCQYDVQQGEwJFRTENMAsGA1UE\nCAwEdGVzdDENMAsGA1UEBwwEdGVzdDENMAsGA1UECgwEdGVzdDENMAsGA1UECwwEdGVzdDEzMDEG\nA1UEAwwqZWlkYXMtc3BlY2lmaWNjb25uZWN0b3ItcmVzcG9uZGVyLW1ldGFkYXRhMB4XDTIwMDky\nMDE3NDMxM1oXDTMwMDkyMDE3NDMxM1owfjELMAkGA1UEBhMCRUUxDTALBgNVBAgMBHRlc3QxDTAL\nBgNVBAcMBHRlc3QxDTALBgNVBAoMBHRlc3QxDTALBgNVBAsMBHRlc3QxMzAxBgNVBAMMKmVpZGFz\nLXNwZWNpZmljY29ubmVjdG9yLXJlc3BvbmRlci1tZXRhZGF0YTCCAiIwDQYJKoZIhvcNAQEBBQAD\nggIPADCCAgoCggIBANP/gTFt+ToahE842QQQyDr1PPXAECy5GeZ7OcViVSoU239EG+edTGgdfvJn\np+Ek/kjL/vgG5X5uMIlvMcjssu7xuJZndyJUPrWQM6FGnEtJ1Qv+tKRvmD8ZDz4e/kFjPXr+9/W6\nz4jt/TOGcVKwiB4luYD54GWGCoqoX55WCsJzYUVrMS4paHoftp/iUVRyQf4DSijtbvZl/Ypbmy6i\nLNVOKhFbDaFw5j1DFmO6pOf41IFMxCANBUhq2PHignZjSx0eGP0675o/i9QbLyc59dVwnUhnPQvs\nEQWyAiiS+61ZzDkAyIVTbQdx/RyQwLyFJh4QO00aocLpBud9FsYuSN1wLKuZSjbbM/ClbiIgs9F9\n3tzijCLkEU9Tx62NhwExq4P5PY1O8cEl9BX4ema+aoyvYDd5eifvI7iTvHv621jxqT2LDvCQjlNA\nOWelNOGsbZuCiQJjln3I1fscN6SB2cdxYebyVCQrfhsZZv7iawp8WCnw/wd/XVyise0lt0asTZWy\nhaOmTFv+nVIbHLcbiFzcEHoPfA31uQa4AOb6mTC9LIZDcelEKJIRiWuTv7hrBw5EAIUVhSdoGv9M\nReYq+vhMtT8lHg8m+IK0iEOfTRUs8x3YJYt2GH9+DC6onyhCm1WdEKuxYU5lPJVJgyg0ejxk0pmt\nsjrHdAeEdbZF4wrPAgMBAAGjITAfMB0GA1UdDgQWBBSz7x9rg/0/MDKXc1gISohsoUXXHDANBgkq\nhkiG9w0BAQsFAAOCAgEAVy6kdgxstQCsWvtq1PuNMnYanzveW1l/jrH8u80r/tBQ29yLjlvSj4e6\nMQdA6EIKwFsKjmH2ZrdpqXyUdFjlfF2LYgpQamf7b8U6s+oFsX3IYRj73fDGJbvlE6gahv4Euadu\nHrivtfHpgtNXdVF2ZrsrY6LbgiMPFZto938M0xmdxDxpGXp2Q2PXu0LGXXptidudikcvD09sciAP\n7RBFPmxSQG2o+RgoJKAsvEQnEPCfSvhlK/SZR/iBmYyxXPhLCBpszFq91xXrD0h2w1KCXKIWTDb8\nw2JuHs7P1PkcmrqSXXYHIf7dBNFKU6AuA/uKteqOO5i0hh7wL7gA56YDghbFGi+UHCft7TrWssso\nGaQkM/YLaFApayHuqQ7J7F5hQvfkwBErPR6uIvFyHMjL5NtoFF2kzVTDx4j/uNzxHXk4XqDX3ZDw\n6hiQmV7Tk7cJRUqU+q5TkYu4TgkBeE1quscVK7gsfFaWv7MBTIT4IBelEFtCU97cNzTqy6TTHnbo\naTqRc1cqN5cA6tebLp+cP0+pIsu6RM69eive+RJJBOMh7Dfd/EVp/EYPmc2AFiNVNMRnq4SVa1Ac\n2nr1ewvm5yJAkefV8w7TNbQ/QKKpPZRfgCH5/5bWp6Q9T3T+6s0ydiIUJQ7fLMR8zEj50+UT/iuf\nOF6TawGAOCgZSsptJbU="
        }
      }
    },
    "Status": {
      "StatusCode": {
        "Value": "urn:oasis:names:tc:SAML:2.0:status:Requester",
        "StatusCode": {
          "Value": "urn:oasis:names:tc:SAML:2.0:status:RequestDenied"
        }
      },
      "StatusMessage": "LoA is missing or invalid"
    }
  },
  "event.kind": "event",
  "event.category": "authentication",
  "event.type": "end",
  "event.outcome": "failure"
}
```
<a name="heartbeat"></a>
## 6. Наблюдение на услугата

`SpecificConnector` използва `Spring Boot Actuator` за мониторинг. За да се измени мониторирането, метриките, одита и други вижте [Spring Boot Actuator documentation](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready).

### 6.1 Спиране на конфигурацията за всички точки за мониториране

| Параметър        | Задължително | Описание, пример |
| :---------------- | :---------- | :----------------|
| `management.endpoints.jmx.exposure.exclude` | Не | Идентификатор на точките, които трябва да бъдат премахнати при използване на JMX или `*` за всички. Препоръчителна стойност `*`. |
| `management.endpoints.web.exposure.exclude` | Не | Идентификатор на точките, които трябва да бъдат премахнати при използване на HTTPS или `*` за всички. Препоръчителна стойност `*`. |

### 6.2 Персонализирани точки за състоянието на приложението

`SpecificConnector` имплементира [персонализирана точка за състоянието](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#production-ready-endpoints-custom) с идентификатор `heartbeat` и [персонализирани точки за работоспособността](https://docs.spring.io/spring-boot/docs/current/reference/html/production-ready-features.html#writing-custom-healthindicators) с идентификатори `igniteCluster`, `connectorMetadata`, `responderMetadata`, `truststore`, `sp-%{service-provider-id}-metadata`. Тази точки са спрени по подразбиране.

Заявка:

````
curl -X GET https://eidas-connector:8084/SpecificConnector/heartbeat
````

Отговор:
```json
{
  "currentTime": "2020-09-28T15:45:34.091Z",
  "upTime": "PT28M13S",
  "buildTime": "2020-09-28T14:53:39.502Z",
  "name": "bg-specific-connector",
  "startTime": "2020-09-28T15:17:37.850Z",
  "commitId": "dbdb7bfa1e48237b3f69fb1de9357f55a1e2df9d",
  "version": "1.0.0-SNAPSHOT",
  "commitBranch": "develop",
  "status": "UP",
  "dependencies": [
    {
      "name": "igniteCluster",
      "status": "UP"
    },
    {
      "name": "truststore",
      "status": "UP"
    },
    {
      "name": "sp-ca-metadata",
      "status": "UP"
    },
    {
      "name": "connectorMetadata",
      "status": "UP"
    },
    {
      "name": "responderMetadata",
      "status": "UP"
    }
  ]
}
```

#### 6.2.1 Минимална препоръчителна конфигурация за позволяване единствено на точката `heartbeat`:

| Параметър        | Задължително | Описание, пример |
| :---------------- | :---------- | :----------------|
| `management.endpoints.jmx.exposure.exclude` | Не | Идентификатори на точки, които трябва да бъдат пропуснати при експортирането им чрез `JMX` или `*` за всички. Препоръчителна стойност: `*` |
| `management.endpoints.web.exposure.include` | Не | Идентификатори на точки, които трябва да бъдат включени при експортирането им чрез `HTTP` или `*` за всички. Препоръчителна стойност: `hearbeat` |
| `management.endpoints.web.base-path` | Не |  Базов път за уеб точките. В релация спрямо `server.servlet.context-path` или `management.server.servlet.context-path` ако `management.server.port` е конфигуриран. Препоръчителна стойност: `/` |
| `management.health.defaults.enabled` | Не | Дали да са включени индикаторите по подразбиране от Spring Boot Actuator за работоспособност. Препоръчителна стойност: `false` |
| `management.info.git.mode` | Не | Режим, в който се извежда git информацията. Препоръчителна стойност: `full` |
| `eidas.connector.health.dependencies.connect-timeout` | Не | Време за изчакване за `connectorMetadata` индикатора. По подразбиране: `3s` |
| `eidas.connector.health.hsm-test-interval` | Не<sup>1</sup> | Минимален интервал за тестовете към HSM относно модула за `responderMetadata` индикатора.<sup>2</sup> По подразбиране: `60s` |
| `eidas.connector.health.key-store-expiration-warning` | Не | Период за предупреждение преди изтичането на сертификата за `responderMetadata` индикатора. По подразбиране: `30d` |
| `eidas.connector.health.trust-store-expiration-warning` | Не | Период за предупреждение преди изтичането на сертификати от `truststore` индикатора. Стойност по подразбиране: `30d` |

<sup>1</sup> Приложимо само при `eidas.connector.hsm.enabled=true`

<sup>2</sup> Тестовете на HSM се изпълняват само когато `heartbeat` точката е извикана. За да се минимизира ефекта върху HSM `eidas.connector.health.hsm-test-interval` определя времевия интервал преди да е възможно изпълнението на последващ тест. Ако интервала не е изминал, предишния резултат се връща в случаите, не е имало проблем при използването на модула от приложението. Когато е имало проблеми с HSM в работата на приложението, тестовете се изпълняват на всяка заявка към `hearbeat` точката, до отпадане на грешките.

<a name="security"></a>
## 7. Сигурност

| Параметър        | Задължителен | Описание, пример |
| :---------------- | :---------- | :----------------|
| `eidas.connector.content-security-policy` | Не | HTTP политика за сигурност на съдържанието (CSP). Стойност по подразбиране: `block-all-mixed-content; default-src 'self'; object-src: 'none'; frame-ancestors 'none'; script-src 'self' 'sha256-8lDeP0UDwCO6/RhblgeH/ctdBzjVpJxrXizsnIk3cEQ='` |
