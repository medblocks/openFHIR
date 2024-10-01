package com.medblocks.openfhir.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.model.FhirConnectContext;
import com.medblocks.openfhir.fc.model.FhirConnectMapper;
import com.medblocks.openfhir.fc.model.Mapping;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.hl7.fhir.r4.hapi.fluentpath.FhirPathR4;
import org.hl7.fhir.r4.model.Base;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
@Slf4j
public class FhirConnectValidator {

    @Autowired
    private FhirPathR4 fhirPathR4;

    @Autowired
    private OpenFhirStringUtils openFhirStringUtils;

    public List<String> validateAgainstContextSchema(final FhirConnectContext parsed) {
        return validateAgainstSchema(parsed, "/contextual-mapping.schema.json");
    }

    public List<String> validateAgainstModelSchema(final FhirConnectMapper parsed) {
        return validateAgainstSchema(parsed, "/model-mapping.schema.json");
    }

    public List<String> validateAgainstSchema(final Object parsed, final String schemaName) {
        final ObjectMapper objectMapper = new ObjectMapper();

        try {
            String schemaString = IOUtils.toString(getClass().getResourceAsStream(schemaName),
                    StandardCharsets.UTF_8);

            JsonNode schemaNode = objectMapper.readTree(schemaString);
            JsonNode jsonNode = objectMapper.convertValue(parsed, JsonNode.class);
            JsonSchemaFactory schemaFactory = JsonSchemaFactory.byDefault();
            JsonSchema schema = schemaFactory.getJsonSchema(schemaNode);
            ProcessingReport report = schema.validate(jsonNode);
            if (!report.isSuccess()) {
                final List<String> errors = new ArrayList<>();
                report.iterator().forEachRemaining(err -> {
                    if (err.getLogLevel() == LogLevel.ERROR || err.getLogLevel() == LogLevel.FATAL) {
                        errors.add(err.getMessage());
                    }
                });
                return errors;
            }
        } catch (final Exception e) {
            log.error("Couldn't validate model mapper against the schema", e);
            throw new IllegalArgumentException("Couldn't validate model mapper against the schema");
        }
        return null;
    }

    public List<String> validateFhirConnectMapper(final FhirConnectMapper mapper) {
        final FhirConnectMapper toValidateOn = mapper.copy();
        final List<String> errors = new ArrayList<>();
//        if (!mapper.getFormat().equals("0.0.2")) {
//            errors.add("Format of the model mapper needs to be 0.0.2");
//        }

        // validate fhirpaths in there
        if (toValidateOn.getFhirConfig() != null && toValidateOn.getFhirConfig().getCondition() != null) {
            final String path = openFhirStringUtils.amendFhirPath(FhirConnectConst.FHIR_RESOURCE_FC,
                    toValidateOn.getFhirConfig().getCondition(), toValidateOn.getFhirConfig().getResource());
            try {
                fhirPathR4.evaluate(new Bundle(), path, Base.class); // we don't care about the result, only if it passes the validation
            } catch (final Exception e) {
                errors.add("FhirPath constructed from fhirConfig.conditions is not a valid one. Constructed: " + path);
            }
        }

        for (Mapping mapping : toValidateOn.getMappings()) {
            if(toValidateOn.getFhirConfig() == null) {
                continue;
            }
            final String path = openFhirStringUtils.amendFhirPath(FhirConnectConst.FHIR_RESOURCE_FC,
                    Arrays.asList(mapping.getCondition()), toValidateOn.getFhirConfig().getResource());
            try {
                fhirPathR4.evaluate(new Bundle(), path, Base.class); // we don't care about the result, only if it passes the validation
            } catch (final Exception e) {
                errors.add("FhirPath constructed from mapping name '" + mapping.getName() + "' is not a valid one. Constructed: " + path);
            }
        }


        return errors;
    }

}
