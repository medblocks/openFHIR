package com.medblocks.openfhir.rest;

import com.medblocks.openfhir.OpenFhirEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Controller for the openFHIR Engine; mapping from openEHR to FHIR and vice versa REST endpoints are created here
 */
@RestController
@Slf4j
@Tag(name = "openFHIR API", description = "Operations related to openFHIR (mapping between openEHR and FHIR)")
public class OpenFhirController {

    private final OpenFhirEngine openFhirEngine;

    @Autowired
    public OpenFhirController(final OpenFhirEngine openFhirEngine) {
        this.openFhirEngine = openFhirEngine;
    }

    /**
     * Accepts an openEHR Composition and maps it corresponding FHIR Resources according to the state of the openFHIR
     * engine
     *
     * @param composition can either be a flat json or a canonical format of a Composition
     * @param templateId is an optional parameter if composition is of canonical format; if composition is in
     *         flat format,
     *         this parameter is required, because the engine can not determine templateId in that case (yet
     *         requires it to find the correct state of the Engine)
     * @return FHIR Bundle with mapped Resources inside
     */
    @PostMapping(value = "/openfhir/tofhir", produces = "application/json")
    @Operation(
            summary = "Maps incoming openEHR Composition to a FHIR Resource",
            description = "Maps incoming openEHR Composition to a FHIR Resource according to FHIR Connect state of the engine",
            responses = {
                    @ApiResponse(responseCode = "200", description = "OK")
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "openEHR Composition in either flat or canonical format",
                    content = {
                            @Content(mediaType = "application/json")
                    }
            )
    )
    ResponseEntity toFhir(@RequestBody String composition, @RequestParam(required = false) String templateId,
                          @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {

            final String fhir = openFhirEngine.toFhir(composition, templateId);

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(fhir);
        } catch (ResponseStatusException | IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    /**
     * Accepts a FHIR Resource (Bundle or any other) and maps it corresponding openEHR Composition  according to the
     * state of the openFHIR
     *
     * @param fhirResource incoming FHIR Resource (Bundle or any other), R4
     * @param templateId template id is an optional parameter if you want to force a specific context mapper; if
     *         no
     *         templateId is provided, then out of all context mappers, the engine will try to find one that
     *         matches the given incoming FHIR Resource (based on context mapper context.profileUrl)
     * @param flat if you want the mapped Composition to be provided in a flat format, default is false meaning
     *         it will
     *         be returned in a canonical format
     * @param reqId request id that will be logged
     * @return openEHR Composition in either flat or canonical format, depending on "flat" argument (default is
     *         canonical)
     */
    @PostMapping(value = "/openfhir/toopenehr", produces = "application/json")
    @Operation(
            summary = "Maps incoming FHIR Resource to openEHR Composition",
            description = "Maps incoming FHIR Resource to openEHR Composition according to FHIR Connect state of the engine",
            responses = {
                    @ApiResponse(responseCode = "200", description = "openEHR Composition in either flat or canonical format")
            },
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "FHIR Resource",
                    content = {
                            @Content(mediaType = "application/json")
                    }
            )
    )
    ResponseEntity toOpenEhr(@RequestBody String fhirResource,
                             @RequestParam(required = false) String templateId,
                             @RequestParam(required = false) Boolean flat,
                             @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {

            final String openEhr = openFhirEngine.toOpenEhr(fhirResource, templateId, flat);

            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(openEhr);
        } catch (ResponseStatusException | IllegalArgumentException e) {

            return ResponseEntity.badRequest().body(e.getMessage());
        }catch (Exception e) {

            throw e;
        }
    }

    /**
     *
     * @return - List of profiles from mappings
     */
    @Operation(
        summary = "Get all valid profiles and return",
        description = "Get all valid profiles available in openfhir engine",
        responses = {
                @ApiResponse(responseCode = "200", description = "List of all valid profiles")
        }
    )
    @GetMapping(value = "/fc/profiles")
    List<String> getValidProfiles(@RequestHeader(value = "x-req-id", required = false) final String reqId) {
    return openFhirEngine.getValidProfiles(reqId);
    }
}
