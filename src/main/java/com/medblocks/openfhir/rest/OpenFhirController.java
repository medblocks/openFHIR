package com.medblocks.openfhir.rest;

import com.medblocks.openfhir.OpenFhirEngine;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for the openFHIR Engine; mapping from openEHR to FHIR and vice versa REST endpoints are created here
 */
@RestController
public class OpenFhirController {

    private final OpenFhirEngine openFhirEngine;

    @Autowired
    public OpenFhirController(OpenFhirEngine openFhirEngine) {
        this.openFhirEngine = openFhirEngine;
    }

    /**
     * Accepts an openEHR Composition and maps it corresponding FHIR Resources according to the state of the openFHIR
     * engine
     *
     * @param composition can either be a flat json or a canonical format of a Composition
     * @param templateId  is an optional parameter if composition is of canonical format; if composition is in flat format,
     *                    this parameter is required, because the engine can not determine templateId in that case (yet
     *                    requires it to find the correct state of the Engine)
     *                                       todo: remove this if the first part of the flat path is template id (has to be normalized == space=_ and upper case=lower case
     * @param reqId       request id that will be logged
     * @return FHIR Bundle with mapped Resources inside
     */
    @PostMapping("/openfhir/tofhir")
    ResponseEntity toFhir(@RequestBody String composition, @RequestParam(required = false) String templateId, @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(openFhirEngine.toFhir(composition, templateId));
    }

    /**
     * Accepts a FHIR Resource (Bundle or any other) and maps it corresponding openEHR Composition  according to the
     * state of the openFHIR
     *
     * @param fhirResource incoming FHIR Resource (Bundle or any other), R4
     * @param templateId   template id is an optional parameter if you want to force a specific context mapper; if no
     *                     templateId is provided, then out of all context mappers, the engine will try to find one that
     *                     matches the given incoming FHIR Resource (based on context mapper context.profileUrl)
     * @param flat         if you want the mapped Composition to be provided in a flat format, default is false meaning it will
     *                     be returned in a canonical format
     * @param reqId        request id that will be logged
     * @return openEHR Composition in either flat or canonical format, depending on "flat" argument (default is canonical)
     */
    @PostMapping("/openfhir/toopenehr")
    ResponseEntity toOpenEhr(@RequestBody String fhirResource,
                             @RequestParam(required = false) String templateId,
                             @RequestParam(required = false) Boolean flat,
                             @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(openFhirEngine.toOpenEhr(fhirResource, templateId, flat));
    }

    @GetMapping("/fc/profiles")
    List<String> getValidProfiles(@RequestHeader(value = "x-req-id", required = false) final String reqId) {
        return openFhirEngine.getValidProfiles(reqId);
    }
}
