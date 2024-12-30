package com.medblocks.openfhir;

import com.medblocks.openfhir.fc.OpenFhirFhirConnectModelMapper;
import java.util.List;
import java.util.Map;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.openehr.schemas.v1.OPERATIONALTEMPLATE;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Data
public class OpenFhirContextRepository {
    private Map<String, List<OpenFhirFhirConnectModelMapper>> mappers;
    /**
     * @deprecated Not used anymore, can be removed
     */
    @Deprecated
    private Map<String, List<OpenFhirFhirConnectModelMapper>> slotMappers;
    private OPERATIONALTEMPLATE operationaltemplate;
    private WebTemplate webTemplate;
}
