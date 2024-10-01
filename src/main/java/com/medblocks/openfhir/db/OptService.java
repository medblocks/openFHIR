package com.medblocks.openfhir.db;

import com.medblocks.openfhir.OpenFhirMappingContext;
import com.medblocks.openfhir.db.entity.OptEntity;
import com.medblocks.openfhir.db.repository.OptRepository;
import com.medblocks.openfhir.util.OpenEhrCachedUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.xmlbeans.XmlException;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@Slf4j
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
    public OptEntity create(final String opt, final String reqId) {
        log.debug("Receive CREATE OPT, reqId: {}", reqId);
        // parse opt to validate it's ok
        try {
            final OPERATIONALTEMPLATE operationaltemplate = parseOptFromString(opt);
            final String normalizedTemplateId = OpenFhirMappingContext.normalizeTemplateId(operationaltemplate.getTemplateId().getValue());
            openEhrApplicationScopedUtils.parseWebTemplate(operationaltemplate);
            // get name from it
            final OptEntity entity = new OptEntity(UUID.randomUUID().toString(), opt, normalizedTemplateId, operationaltemplate.getTemplateId().getValue(), operationaltemplate.getTemplateId().getValue());
            final OptEntity insert = optRepository.insert(entity);
            return OptEntity.builder().id(insert.getId()).templateId(operationaltemplate.getTemplateId().getValue()).build();
        } catch (final Exception e) {
            log.error("Couldn't create a template, reqId: {}", reqId, e);
            throw new IllegalArgumentException("Couldn't create a template. Invalid one.");
        }
    }

    public List<OptEntity> all() {
        return optRepository.findEmptyContent();
    }

    public String getContent(final String templateId) {
        return optRepository.findByTemplateId(templateId).getContent();
    }

    private OPERATIONALTEMPLATE parseOptFromString(final String content) throws XmlException {
        return TemplateDocument.Factory.parse(content.trim().replaceFirst("^([\\W]+)<", "<")).getTemplate();
    }
}
