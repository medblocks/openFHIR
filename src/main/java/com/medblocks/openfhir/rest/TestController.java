package com.medblocks.openfhir.rest;

import com.medblocks.openfhir.db.repository.FhirConnectContextRepository;
import com.medblocks.openfhir.db.repository.FhirConnectMapperRepository;
import com.medblocks.openfhir.db.repository.OptRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {


    @Autowired
    private OptRepository optRepository;
    @Autowired
    private FhirConnectMapperRepository fhirConnectMapperRepository;
    @Autowired
    private FhirConnectContextRepository fhirConnectContextRepository;

    @GetMapping("/$purge")
    ResponseEntity purge(@RequestHeader(value = "x-req-id", required = false) final String reqId) {
        try {
            optRepository.deleteAll();
            fhirConnectMapperRepository.deleteAll();
            fhirConnectContextRepository.deleteAll();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }
}
