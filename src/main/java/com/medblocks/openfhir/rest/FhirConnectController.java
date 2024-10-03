package com.medblocks.openfhir.rest;

import com.medblocks.openfhir.db.FhirConnectService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller for FHIR Connect specific models, namely for state configuration of the openFHIR Engine
 */
@RestController
public class FhirConnectController {

    @Autowired
    private FhirConnectService service;

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
            return ResponseEntity.ok(service.createModelMapper(yamlMapper, reqId));
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
            return ResponseEntity.ok(service.createContextMapper(yamlMapper, reqId));
        } catch (Exception e) {
            if (e instanceof RequestValidationException) {
                return ResponseEntity.badRequest().body(((RequestValidationException) e).getMessages());
            }
            return ResponseEntity.internalServerError().build();
        }
    }

}
