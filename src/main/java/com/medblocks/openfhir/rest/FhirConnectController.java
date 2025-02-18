package com.medblocks.openfhir.rest;

import com.medblocks.openfhir.db.FhirConnectService;
import com.medblocks.openfhir.db.entity.FhirConnectModelEntity;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for FHIR Connect specific models, namely for state configuration of the openFHIR Engine
 */
@RestController
public class FhirConnectController {

    private final FhirConnectService service;

    @Autowired
    public FhirConnectController(FhirConnectService service) {
        this.service = service;
    }

    /**
     * creation of the model mapper
     *
     * @return 200 OK if context is successfully created with the created Context entity; 400 BAD REQUEST if validation
     * of the given YAML failed and is not according to specification or semantic validation; 500 INTERNAL SERVER
     * ERROR if an unknown error occurred.
     */
    @PostMapping("/fc/model")
    ResponseEntity newModel(@RequestBody String yamlMapper, @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {
            return ResponseEntity.ok(service.upsertModelMapper(yamlMapper, null, reqId));
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/fc/model/{id}")
    ResponseEntity updateModel(@PathVariable String id, @RequestBody String yamlMapper, @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("Id must no be empty when updating.");
        }
        try {
            return ResponseEntity.ok(service.upsertModelMapper(yamlMapper, id, reqId));
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * creation of the context mapper
     *
     * @return 200 OK if context is successfully created with the created Context entity; 400 BAD REQUEST if validation
     * of the given YAML failed and is not according to specification or semantic validation; 500 INTERNAL SERVER
     * ERROR if an unknown error occurred.
     */
    @PostMapping("/fc/context")
    ResponseEntity newContext(@RequestBody String yamlMapper, @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {
            return ResponseEntity.ok(service.upsertContextMapper(yamlMapper, null, reqId));
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @PutMapping("/fc/context/{id}")
    ResponseEntity updateContext(@PathVariable String id, @RequestBody String yamlMapper, @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        if (StringUtils.isEmpty(id)) {
            throw new IllegalArgumentException("Id must no be empty when updating.");
        }
        try {
            return ResponseEntity.ok(service.upsertContextMapper(yamlMapper, id, reqId));
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

    @GetMapping("/fc")
    List<FhirConnectModelEntity> userModelMappers(@RequestHeader(value = "x-req-id", required = false) final String reqId) {
        return service.all(reqId);
    }

    @GetMapping("/fc/profiles")
    List<String> getValidProfiles(@RequestHeader(value = "x-req-id", required = false) final String reqId) {
        return service.getValidProfiles(reqId);
    }

}
