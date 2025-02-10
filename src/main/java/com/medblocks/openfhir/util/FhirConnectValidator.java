package com.medblocks.openfhir.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.fge.jsonschema.core.report.LogLevel;
import com.github.fge.jsonschema.core.report.ProcessingReport;
import com.github.fge.jsonschema.main.JsonSchema;
import com.github.fge.jsonschema.main.JsonSchemaFactory;
import com.medblocks.openfhir.fc.schema.context.FhirConnectContext;
import com.medblocks.openfhir.fc.schema.model.FhirConnectModel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class FhirConnectValidator {

    public List<String> validateAgainstContextSchema(final FhirConnectContext parsed) {
        return validateAgainstSchema(parsed, "/contextual-mapping.schema.json");
    }

    public List<String> validateAgainstModelSchema(final FhirConnectModel parsed) {
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
                        if (!err.getMessage().contains("(matched 2 out of 2)")) {
                            // ugly yes, but this error is there because we have a helper method getAttributes to wrap a getAttribute in a list and return it,
                            // which makes this validator think both attributes are present
                            errors.add(err.getMessage());
                        }
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

    public List<String> validateFhirConnectModel(final FhirConnectModel modelMapper) {
        final List<String> errors = new ArrayList<>();
//        if (!modelMapper.getFormat().equals("0.0.2")) {
//            errors.add("Format of the model modelMapper needs to be 0.0.2");
//        }

        // todo: imeplement other validation (fhir path, openehr paths, ..)


        return errors;
    }

}
