package com.medblocks.openfhir.rest;

import com.medblocks.openfhir.db.OptService;
import com.medblocks.openfhir.db.entity.OptEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class OptController {
    @Autowired
    private OptService optService;

    /**
     * creates an operational template as part of the state configuration of the engine
     *
     * @param opt   payload that is the operational template
     * @param reqId
     * @return OptEntity without the actual context of the template, just the metadata and the assigned database ID
     */
    @PostMapping("/opt")
    OptEntity newOpt(@RequestBody String opt, @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        return optService.create(opt, reqId);
    }

    /**
     * returns all existing operational templates in the database, but without the actual content of it (to avoid
     * huge payloads being returned). If you want to get a specific template and it's content, trigger a GET on a specific
     * operational template id.
     *
     * @param reqId
     * @return returns all existing operational templates without the content
     */
    @GetMapping("/opt")
    List<OptEntity> usersOpts(@RequestHeader(value = "x-req-id", required = false) final String reqId) {
        return optService.all();
    }

    /**
     * reads a specific operational template with content
     *
     * @param templateId id of the template as it was assigned to the OptEntity at the time of the creation
     * @param reqId
     * @return a specific operational template with content
     */
    @GetMapping("/opt/{templateId}")
    ResponseEntity<String> read(@PathVariable String templateId, @RequestHeader(value = "x-req-id", required = false) final String reqId) {
        final String content = optService.getContent(templateId);
        return ResponseEntity.ok().contentType(MediaType.TEXT_XML).body(content);
    }

}
