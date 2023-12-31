package bg.is.eidas.connector.specific.responder.serviceprovider;

import bg.is.eidas.connector.specific.config.SpecificConnectorProperties;
import bg.is.eidas.connector.specific.exception.TechnicalException;
import bg.is.eidas.connector.specific.monitoring.health.ResponderMetadataHealthIndicator.FailedSigningEvent;
import bg.is.eidas.connector.specific.responder.metadata.ResponderMetadataSigner;
import bg.is.eidas.connector.specific.responder.saml.OpenSAMLUtils;
import com.google.common.collect.ImmutableSet;
import eu.eidas.auth.commons.attribute.AttributeValue;
import eu.eidas.auth.commons.attribute.*;
import eu.eidas.auth.commons.light.ILightResponse;
import eu.eidas.auth.commons.light.IResponseStatus;
import lombok.extern.slf4j.Slf4j;
import net.shibboleth.utilities.java.support.resolver.ResolverException;
import net.shibboleth.utilities.java.support.security.RandomIdentifierGenerationStrategy;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.opensaml.core.xml.XMLObject;
import org.opensaml.core.xml.io.MarshallingException;
import org.opensaml.core.xml.schema.XSAny;
import org.opensaml.core.xml.schema.impl.XSAnyBuilder;
import org.opensaml.saml.saml2.core.*;
import org.opensaml.saml.saml2.core.impl.*;
import org.opensaml.security.SecurityException;
import org.opensaml.xmlsec.encryption.support.EncryptionException;
import org.opensaml.xmlsec.signature.support.SignatureException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import javax.xml.namespace.QName;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static java.lang.Math.toIntExact;
import static org.opensaml.saml.common.SAMLVersion.VERSION_20;

@Slf4j
@Component
public class ResponseFactory {

    @Autowired
    private SpecificConnectorProperties connectorProperties;

    @Autowired
    private ResponderMetadataSigner responderMetadataSigner;

    @Autowired
    private ServiceProviderMetadataRegistry serviceProviderMetadataRegistry;

    @Autowired
    private String specificConnectorIP;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    private static final RandomIdentifierGenerationStrategy secureRandomIdGenerator = new RandomIdentifierGenerationStrategy(32);

    public String createSamlResponse(AuthnRequest authnRequest, ILightResponse lightResponse, ServiceProviderMetadata spMetadata) {
        try {
            Response response = createResponse(authnRequest, lightResponse, spMetadata);
            responderMetadataSigner.sign(response);
            return OpenSAMLUtils.getXmlString(response);
        } catch (EncryptionException | AttributeValueMarshallingException | SecurityException | MarshallingException | SignatureException | ResolverException ex) {
            applicationEventPublisher.publishEvent(new FailedSigningEvent());
            throw new TechnicalException("Unable to create SAML Response", ex);
        }
    }

    public String createSamlErrorResponse(AuthnRequest authnRequest, String statusCode, String subStatusCode, String statusMessage) {
        try {
            Status status = createStatus(statusCode, subStatusCode, statusMessage);
            Response response = createErrorResponse(authnRequest, status);
            responderMetadataSigner.sign(response);
            return OpenSAMLUtils.getXmlString(response);
        } catch (Exception ex) {
            applicationEventPublisher.publishEvent(new FailedSigningEvent());
            throw new TechnicalException("Unable to create SAML Error Response", ex);
        }
    }

    private Response createErrorResponse(AuthnRequest authnRequest, Status status) {
        ServiceProviderMetadata spMetadata = serviceProviderMetadataRegistry.get(authnRequest.getIssuer().getValue());
        if (spMetadata == null) {
            throw new TechnicalException("Unable to create SAML Error response. Service provider metadata not found: %s", authnRequest.getIssuer().getValue());
        }
        Response response = new ResponseBuilder().buildObject();
        response.setID(secureRandomIdGenerator.generateIdentifier());
        response.setDestination(spMetadata.getAssertionConsumerServiceUrl());
        response.setInResponseTo(authnRequest.getID());
        response.setIssueInstant(new DateTime(DateTimeZone.UTC));
        response.setVersion(VERSION_20);
        response.setIssuer(createIssuer());
        response.setStatus(status);
        if (connectorProperties.isAddSamlErrorAssertion()) {
            try {
                response.getEncryptedAssertions().add(createErrorAssertion(authnRequest, response.getIssueInstant(), spMetadata));
            } catch (Exception ex) {
                log.error("Unable to add encrypted error assertion", ex);
            }
        }
        return response;
    }

    private Response createResponse(AuthnRequest authnRequest, ILightResponse lightResponse, ServiceProviderMetadata spMetadata) throws EncryptionException,
            AttributeValueMarshallingException, SecurityException, MarshallingException, SignatureException, ResolverException {
        Response response = new ResponseBuilder().buildObject();
        response.setID(lightResponse.getId());
        response.setDestination(spMetadata.getAssertionConsumerServiceUrl());
        response.setInResponseTo(authnRequest.getID());
        response.setIssueInstant(new DateTime(DateTimeZone.UTC));
        response.setVersion(VERSION_20);
        response.setIssuer(createIssuer());
        response.setStatus(createStatus(lightResponse.getStatus()));
        response.getEncryptedAssertions().add(createAssertion(authnRequest, lightResponse, response.getIssueInstant(), spMetadata));
        return response;
    }

    private EncryptedAssertion createAssertion(AuthnRequest authnRequest, ILightResponse lightResponse, DateTime issueInstant, ServiceProviderMetadata spMetadata)
            throws AttributeValueMarshallingException, MarshallingException, SecurityException, SignatureException, ResolverException,
            EncryptionException {
        AssertionBuilder assertionBuilder = new AssertionBuilder();
        Assertion assertion = assertionBuilder.buildObject();
        assertion.setID(secureRandomIdGenerator.generateIdentifier());
        assertion.setIssuer(createIssuer());
        assertion.setIssueInstant(issueInstant);
        assertion.setSubject(createSubject(authnRequest, lightResponse, spMetadata.getAssertionConsumerServiceUrl(), issueInstant));
        assertion.getAttributeStatements().add(createAttributeStatement(lightResponse));
        assertion.getAuthnStatements().add(createAuthnStatement(issueInstant, lightResponse.getLevelOfAssurance()));
        assertion.setConditions(createConditions(issueInstant, spMetadata));
        if (spMetadata.isWantAssertionsSigned()) {
            responderMetadataSigner.sign(assertion);
        }
        return spMetadata.encrypt(assertion);
    }

    private EncryptedAssertion createErrorAssertion(AuthnRequest authnRequest, DateTime issueInstant, ServiceProviderMetadata spMetadata)
            throws MarshallingException, SecurityException, SignatureException, ResolverException, EncryptionException {
        AssertionBuilder assertionBuilder = new AssertionBuilder();
        Assertion assertion = assertionBuilder.buildObject();
        assertion.setID(secureRandomIdGenerator.generateIdentifier());
        assertion.setIssuer(createIssuer());
        assertion.setIssueInstant(issueInstant);
        assertion.setSubject(createSubject(authnRequest, spMetadata.getAssertionConsumerServiceUrl(), issueInstant));
        Optional<AuthnContextClassRef> classRef = authnRequest.getRequestedAuthnContext().getAuthnContextClassRefs().stream().findFirst();
        String levelOfAssurance = classRef.map(AuthnContextClassRef::getAuthnContextClassRef).orElse(null);
        assertion.getAuthnStatements().add(createAuthnStatement(issueInstant, levelOfAssurance));
        assertion.setConditions(createConditions(issueInstant, spMetadata));
        if (spMetadata.isWantAssertionsSigned()) {
            responderMetadataSigner.sign(assertion);
        }
        return spMetadata.encrypt(assertion);
    }

    private Issuer createIssuer() {
        Issuer responseIssuer = new IssuerBuilder().buildObject();
        responseIssuer.setValue(connectorProperties.getResponderMetadata().getEntityId());
        responseIssuer.setFormat(NameIDType.ENTITY);
        return responseIssuer;
    }

    private Status createStatus(IResponseStatus status) {
        if (status == null) {
            throw new TechnicalException("LightResponse status cannot be null");
        }
        return createStatus(status.getStatusCode(), status.getSubStatusCode(), status.getStatusMessage());
    }

    private Status createStatus(String eidasStatusCode, String eidasSubStatusCode, String message) {
        Status status = new StatusBuilder().buildObject();
        StatusCode statusCode = null;

        if (eidasStatusCode != null) {
            statusCode = new StatusCodeBuilder().buildObject();
            statusCode.setValue(eidasStatusCode);
            status.setStatusCode(statusCode);
        }

        if (statusCode != null && eidasSubStatusCode != null && !"##".equals(eidasSubStatusCode)) {
            StatusCode subStatusCode = new StatusCodeBuilder().buildObject();
            subStatusCode.setValue(eidasSubStatusCode);
            statusCode.setStatusCode(subStatusCode);
        }

        StatusMessage statusMessage = new StatusMessageBuilder().buildObject();
        statusMessage.setMessage(message);
        status.setStatusMessage(statusMessage);
        return status;
    }

    private AttributeStatement createAttributeStatement(ILightResponse lightResponse) throws AttributeValueMarshallingException {
        AttributeStatement attributeStatement = new AttributeStatementBuilder().buildObject();
        ImmutableAttributeMap responseAttributes = lightResponse.getAttributes();

        for (Map.Entry<AttributeDefinition<?>, ImmutableSet<? extends AttributeValue<?>>> entry : responseAttributes.getAttributeMap().entrySet()) {
            attributeStatement.getAttributes().add(createAttribute(entry));
        }
        return attributeStatement;
    }

    @SuppressWarnings("unchecked")
    private Attribute createAttribute(Map.Entry<AttributeDefinition<?>, ImmutableSet<? extends AttributeValue<?>>> entry)
            throws AttributeValueMarshallingException {
        AttributeDefinition<?> definition = entry.getKey();
        ImmutableSet<? extends AttributeValue<?>> values = entry.getValue();
        Attribute attribute = createAttribute(definition.getFriendlyName(), definition.getNameUri().toString());
        List<XMLObject> attributeValues = attribute.getAttributeValues();
        AttributeValueMarshaller<?> attributeValueMarshaller = definition.getAttributeValueMarshaller();

        for (AttributeValue<?> attributeValue : values) {
            String value = attributeValueMarshaller.marshal((AttributeValue) attributeValue);
            attributeValues.add(createAttributeValue(definition.getXmlType().toString(), value));
        }
        return attribute;
    }

    private Subject createSubject(AuthnRequest authnRequest, String assertionConsumerServiceUrl, DateTime issueInstant) {
        return createSubject(authnRequest, null, assertionConsumerServiceUrl, issueInstant);
    }

    private Subject createSubject(AuthnRequest authnRequest, ILightResponse lightResponse, String assertionConsumerServiceUrl, DateTime issueInstant) {
        Subject subject = new SubjectBuilder().buildObject();
        NameID nameID = new NameIDBuilder().buildObject();
        if (lightResponse != null) {
            nameID.setValue(lightResponse.getSubject());
            nameID.setFormat(lightResponse.getSubjectNameIdFormat());
        } else {
            nameID.setValue("NotAvailable");
            nameID.setFormat(NameIDType.UNSPECIFIED);
        }
        subject.setNameID(nameID);

        SubjectConfirmation subjectConfirmation = new SubjectConfirmationBuilder().buildObject();
        subjectConfirmation.setMethod(SubjectConfirmation.METHOD_BEARER);
        SubjectConfirmationData subjectConfirmationData = new SubjectConfirmationDataBuilder().buildObject();
        subjectConfirmationData.setAddress(specificConnectorIP);
        subjectConfirmationData.setInResponseTo(authnRequest.getID());
        int validityInterval = toIntExact(connectorProperties.getResponderMetadata().getAssertionValidityInterval().getSeconds());
        subjectConfirmationData.setNotOnOrAfter(issueInstant.plusSeconds(validityInterval));
        subjectConfirmationData.setRecipient(assertionConsumerServiceUrl);
        subjectConfirmation.setSubjectConfirmationData(subjectConfirmationData);
        subject.getSubjectConfirmations().add(subjectConfirmation);
        return subject;
    }

    private Conditions createConditions(DateTime issueInstant, ServiceProviderMetadata spMetadata) {
        Conditions conditions = new ConditionsBuilder().buildObject();
        conditions.setNotBefore(issueInstant);
        int validityInterval = toIntExact(connectorProperties.getResponderMetadata().getAssertionValidityInterval().getSeconds());
        conditions.setNotOnOrAfter(issueInstant.plusSeconds(validityInterval));

        Audience audience = new AudienceBuilder().buildObject();
        audience.setAudienceURI(spMetadata.getEntityId());

        AudienceRestriction audienceRestriction = new AudienceRestrictionBuilder().buildObject();
        audienceRestriction.getAudiences().add(audience);
        conditions.getAudienceRestrictions().add(audienceRestriction);
        return conditions;
    }

    private AuthnStatement createAuthnStatement(DateTime issueInstant, String levelOfAssurance) {
        AuthnStatement authnStatement = new AuthnStatementBuilder().buildObject();
        authnStatement.setAuthnInstant(issueInstant);
        AuthnContext authnContext = new AuthnContextBuilder().buildObject();
        if (levelOfAssurance != null) {
            AuthnContextClassRef authnContextClassRef = new AuthnContextClassRefBuilder().buildObject();
            authnContextClassRef.setAuthnContextClassRef(levelOfAssurance);
            authnContext.setAuthnContextClassRef(authnContextClassRef);
            authnStatement.setAuthnContext(authnContext);
        }
        return authnStatement;
    }

    private Attribute createAttribute(String friendlyName, String name) {
        Attribute attribute = new AttributeBuilder().buildObject();
        attribute.setFriendlyName(friendlyName);
        attribute.setName(name);
        attribute.setNameFormat("urn:oasis:names:tc:SAML:2.0:attrname-format:uri");
        return attribute;
    }

    private XSAny createAttributeValue(String xsiType, String value) {
        XSAny attributevalue = new XSAnyBuilder().buildObject(org.opensaml.saml.saml2.core.AttributeValue.DEFAULT_ELEMENT_NAME);
        attributevalue.getUnknownAttributes().put(new QName("http://www.w3.org/2001/XMLSchema-instance", "type", "xsi"), xsiType);
        attributevalue.setTextContent(value);
        return attributevalue;
    }
}
