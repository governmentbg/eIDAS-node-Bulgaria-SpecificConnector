package bg.is.eidas.connector.specific.controller;

import bg.is.eidas.connector.specific.responder.saml.OpenSAMLUtils;
import bg.is.eidas.connector.specific.responder.metadata.ResponderMetadataGenerator;
import lombok.extern.slf4j.Slf4j;
import org.opensaml.core.xml.io.MarshallingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javax.servlet.http.HttpServletRequest;

@Slf4j
@Controller
public class ResponderMetadataController {

    @Autowired
    private ResponderMetadataGenerator responderMetadataGenerator;

    @GetMapping(value = "${eidas.connector.responder-metadata.path:/ConnectorResponderMetadata}", produces = {"application/xml", "text/xml"})
    @ResponseBody
    public String metadata(HttpServletRequest request) throws MarshallingException {
        String metadata = OpenSAMLUtils.getXmlString(responderMetadataGenerator.createSignedMetadata());
        log.info("Metadata requested");
        return metadata;
    }

    /*@GetMapping(value = "/ConnectorResponderMetadata", produces = {"application/xml", "text/xml"})
    @ResponseBody
    public String metadata2(HttpServletRequest request) throws MarshallingException {
        String metadata = OpenSAMLUtils.getXmlString(responderMetadataGenerator.createSignedMetadata());
        log.info("Metadata requested");
        return metadata;
    }*/
}
