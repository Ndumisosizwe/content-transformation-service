package com.lexisnexis.cts.core.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;

/**
 * Extracts the content_id from a legal XML document using StAX streaming.
 * This is a lightweight, memory-efficient approach — it reads only the header
 * without loading the entire document into a DOM tree.
 */
@Component
public class ContentIdExtractor {

    private static final Logger log = LoggerFactory.getLogger(ContentIdExtractor.class);
    private static final String CONTENT_ID_ELEMENT = "content_id";
    private static final String NAMESPACE = "urn:lex:content:1";

    private final XMLInputFactory xmlInputFactory;

    public ContentIdExtractor() {
        this.xmlInputFactory = XMLInputFactory.newInstance();
        // Disable external entities for security
        this.xmlInputFactory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        this.xmlInputFactory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
    }

    /**
     * Extracts content_id from the XML document header using StAX.
     * Returns null if not found.
     *
     * @param xmlContent the raw XML bytes
     * @return the content_id value, or null if extraction fails
     */
    public String extract(byte[] xmlContent) {
        try {
            XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(
                    new ByteArrayInputStream(xmlContent));
            try {
                while (reader.hasNext()) {
                    int event = reader.next();
                    if (event == XMLStreamReader.START_ELEMENT) {
                        String localName = reader.getLocalName();
                        String namespaceUri = reader.getNamespaceURI();

                        if (CONTENT_ID_ELEMENT.equals(localName)
                                && NAMESPACE.equals(namespaceUri)) {
                            String contentId = reader.getElementText().trim();
                            log.debug("Extracted content_id: {}", contentId);
                            return contentId;
                        }
                    }
                }
            } finally {
                reader.close();
            }
        } catch (XMLStreamException e) {
            log.warn("Failed to extract content_id from XML: {}", e.getMessage());
        }

        return null;
    }
}
