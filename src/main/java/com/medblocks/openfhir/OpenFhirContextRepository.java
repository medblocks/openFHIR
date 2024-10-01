package com.medblocks.openfhir;

import com.medblocks.openfhir.fc.model.FhirConnectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
@Data
public class OpenFhirContextRepository {
    private Map<String, List<FhirConnectMapper>> mappers;
    private Map<String, List<FhirConnectMapper>> slotMappers;
    private OPERATIONALTEMPLATE operationaltemplate;
    private WebTemplate webTemplate;
}
