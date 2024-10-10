package com.medblocks.openfhir.db;

import com.medblocks.openfhir.OpenFhirMappingContext;
import com.medblocks.openfhir.db.entity.OptEntity;
import com.medblocks.openfhir.db.repository.OptRepository;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.xmlbeans.XmlException;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Slf4j
@Transactional
public class OptService {
    @Autowired
    private OptRepository optRepository;

    @Autowired
    private OpenEhrCachedUtils openEhrApplicationScopedUtils;

    /**
     * Creates an operational template in the database.
     *
     * @param opt string payload of the operational template
     * @return created OptEntity without the content (just with the ID assigned by the database)
     * @throws IllegalArgumentException if validation of a template fails (if it can not be parsed)
     */
    public OptEntity upsert(final String opt, final String id, final String reqId) {
        log.debug("Receive CREATE/UPDATE OPT, id {}, reqId: {}", id, reqId);
        // parse opt to validate it's ok
        try {
            final OPERATIONALTEMPLATE operationaltemplate = parseOptFromString(opt);
            final String normalizedTemplateId = OpenFhirMappingContext.normalizeTemplateId(operationaltemplate.getTemplateId().getValue());
            openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);
            final OptEntity existing = optRepository.findByTemplateId(normalizedTemplateId);
            if (existing != null) {
                throw new IllegalArgumentException("Template with templateId " + operationaltemplate.getTemplateId() + " (normalized to: " + normalizedTemplateId + ") already exists.");
            }
            // get name from it
            final OptEntity entity = new OptEntity(StringUtils.isEmpty(id) ? null : id, opt, normalizedTemplateId, operationaltemplate.getTemplateId().getValue(), operationaltemplate.getTemplateId().getValue());
            final OptEntity insert = optRepository.save(entity);
            final OptEntity copied = insert.copy();
            copied.setContent("redacted");
            return copied;
        } catch (final Exception e) {
            log.error("Couldn't create a template, reqId: {}", reqId, e);
            throw new IllegalArgumentException("Couldn't create a template. " + e.getMessage());
        }
    }

    public List<OptEntity> all(final String reqId) {
        return optRepository.findAll();
    }


    public String getContent(final String templateId, final String reqId) {
        return optRepository.findByTemplateId(templateId).getContent();
    }

    /**
     * Ignore any white character at the beginning of the payload and parse content to OPERATIONALTEMPLATE
     *
     * @param content XML that represents a serialized operational template
     * @return parsed OPERATIONALTEMPLATE from the given payload
     * @throws XmlException if content is invalid XML after removing the white characters
     */
    private OPERATIONALTEMPLATE parseOptFromString(final String content) throws XmlException {
        return TemplateDocument.Factory.parse(content.trim().replaceFirst("^([\\W]+)<", "<")).getTemplate();
    }
}
