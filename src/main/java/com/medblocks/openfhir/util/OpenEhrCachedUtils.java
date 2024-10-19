package com.medblocks.openfhir.util;

import com.medblocks.openfhir.OpenFhirMappingContext;
import com.medblocks.openfhir.db.entity.OptEntity;
import com.medblocks.openfhir.db.repository.OptRepository;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.parser.OPTParser;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.openehr.schemas.v1.TemplateDocument;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.SessionScope;

import java.util.HashMap;
import java.util.Map;

/**
 * Utility class used for cached OpenEhr RM that can be otherwise performance issue if
 * created/parsed every time
 */
@Component
@SessionScope
@Slf4j
public class OpenEhrCachedUtils {
    final Map<String, WebTemplate> webTemplates = new HashMap<>();
    final Map<String, OPERATIONALTEMPLATE> operationalTemplates = new HashMap<>();

    private final OptRepository optRepository;

    @Autowired
    public OpenEhrCachedUtils(OptRepository optRepository) {
        this.optRepository = optRepository;
    }

    public WebTemplate parseWebTemplate(final OPERATIONALTEMPLATE operationaltemplate) {
        // todo: what if it's changed in the db?
        if (webTemplates.containsKey(operationaltemplate.getTemplateId().getValue())) {
            return webTemplates.get(operationaltemplate.getTemplateId().getValue());
        }
        final WebTemplate parser = createParser(operationaltemplate);
        webTemplates.put(operationaltemplate.getTemplateId().getValue(), parser);
        return parser;
    }

    private WebTemplate createParser(final OPERATIONALTEMPLATE operationaltemplate) {
        return new OPTParser(operationaltemplate).parse();
    }

    public OPERATIONALTEMPLATE getOperationalTemplate(final String templateId) {
        // todo: what if it's changed in the db?
        final String normalizedTemplateId = OpenFhirMappingContext.normalizeTemplateId(templateId);
        if (operationalTemplates.containsKey(normalizedTemplateId)) {
            return operationalTemplates.get(normalizedTemplateId);
        }
        final OptEntity byTemplateIdAndUser = optRepository.findByTemplateId(normalizedTemplateId);
        if (byTemplateIdAndUser != null) {
            final OPERATIONALTEMPLATE operationalTemplate = parseOperationalTemplate(byTemplateIdAndUser.getContent());
            if (operationalTemplate == null) {
                return null;
            }
            operationalTemplates.put(normalizedTemplateId, operationalTemplate);
        }
        return operationalTemplates.get(normalizedTemplateId);
    }

    private OPERATIONALTEMPLATE parseOperationalTemplate(final String templateContent) {
        try {
            return TemplateDocument.Factory.parse(templateContent).getTemplate();
        } catch (final Exception e) {
            log.error("Couldn't parse OPT even though it came from the db?", e);
            return null;
        }
    }
}
