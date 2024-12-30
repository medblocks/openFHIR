package com.medblocks.openfhir.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.schema.model.Condition;
import com.medblocks.openfhir.fc.schema.model.Mapping;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class OpenEhrConditionEvaluator {

    private OpenFhirStringUtils openFhirStringUtils;

    @Autowired
    public OpenEhrConditionEvaluator(final OpenFhirStringUtils openFhirStringUtils) {
        this.openFhirStringUtils = openFhirStringUtils;
    }

    /**
     * If openehrCondition restricts a mapping only to a certain criteria (i.e. something has to be empty for a
     * mapping to be executed), this is done here. Returns true if a mapping can be executed (condition passes)
     * or false if it mustn't be.
     *
     * @param mapping mapping to be checked
     * @param jsonObject json object holding Composition in a flat path format
     * @return true if a mapping has to be executed or false if not
     */
    public boolean checkOpenEhrCondition(final Mapping mapping, final JsonObject jsonObject,
                                         final String mainOpenEhrPath) {
        if (mapping.getOpenehrCondition() == null) {
            return true;
        }
        final String operator = mapping.getOpenehrCondition().getOperator();
        switch (operator) {
            case FhirConnectConst.CONDITION_OPERATOR_EMPTY -> {
                return checkEmptyCondition(mapping.getOpenehrCondition(), jsonObject, mainOpenEhrPath);
            }
        }
        return true;
    }

    public boolean checkEmptyCondition(final Condition openEhrCondition,
                                       final JsonObject jsonObject,
                                       final String mainOpenEhrPath) {
        final String openEhrPath = openFhirStringUtils.fixOpenEhrPath(openEhrCondition.getTargetRoot(),
                                                                      mainOpenEhrPath);
        final List<String> targetAttributes = openEhrCondition.getTargetAttributes();
        for (final String targetAttribute : targetAttributes) {
            // if array, then OR is implied between them. So as long as one of these fits the operator, return true
            final String fullOpenEhrPath = String.format("%s/%s", openEhrPath, targetAttribute);
            final String withRegex = openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(fullOpenEhrPath);

        }
        return false;
    }

    private JsonObject handleOneOfOperatorSplit(final Condition openEhrCondition,
                                                final List<String> extractedValueKeys,
                                                final JsonObject fullFlatPath) {
        if (extractedValueKeys.isEmpty()) {
            // no such flat path even exists, so let's just consider all entries?
            return fullFlatPath;
        }
        final String targetAttribute = openEhrCondition.getTargetAttribute();
        final JsonObject modifiedJsonObject = new JsonObject();
        for (final String extractedValueKey : extractedValueKeys) {
            final String preparedTargetAttribute = openFhirStringUtils.prepareOpenEhrSyntax(
                    targetAttribute,
                    "");
            final String openEhrKey = String.format("%s/%s", extractedValueKey, preparedTargetAttribute);
            final JsonPrimitive extractedValueJson = fullFlatPath.getAsJsonPrimitive(openEhrKey);
            final String extractedValue = extractedValueJson == null ? "" : extractedValueJson.getAsString();

            final String operator = openEhrCondition.getOperator();

            if (FhirConnectConst.CONDITION_OPERATOR_ONE_OF.equals(operator)
                    && openEhrCondition.getCriteria().contains(extractedValue)) {
                continue;
            }

            log.info(
                    "Flat path {} evaluated to {}, condition.criteria requires it to be {}, therefore excluding all {} from mapping.",
                    openEhrKey, extractedValue, openEhrCondition.getCriteria(), extractedValueKey);

            fullFlatPath.entrySet().forEach((entry) -> {
                if (!entry.getKey().startsWith(extractedValueKey)) {
                    modifiedJsonObject.add(entry.getKey(), entry.getValue());
                }
            });
        }
        return modifiedJsonObject;
    }

    private JsonObject handleEmptyOperatorSplit(final Condition openEhrCondition,
                                                final List<String> extractedValueKeys,
                                                final JsonObject fullFlatPath) {
        if (extractedValueKeys.isEmpty()) {
            // no such flat path even exists, so let's just consider all entries?
            return fullFlatPath;
        }
        final JsonObject modifiedJsonObject = new JsonObject();
        for (final String extractedValueKey : extractedValueKeys) {
            for (final String targetAttribute : openEhrCondition.getTargetAttributes()) {
                final String preparedTargetAttribute = openFhirStringUtils.prepareOpenEhrSyntax(
                        targetAttribute,
                        "");
                final String openEhrKey = String.format("%s/%s", extractedValueKey, preparedTargetAttribute);
                final List<String> matchingEntries = openFhirStringUtils.getAllEntriesThatMatchIgnoringPipe(openEhrKey,
                                                                                                            fullFlatPath);

                final String operator = openEhrCondition.getOperator();

                if (FhirConnectConst.CONDITION_OPERATOR_EMPTY.equals(operator)
                        && !matchingEntries.isEmpty()) {
                    log.info(
                            "Flat path {} didn't evaluate to empty, as per condition, therefore excluding all {} from mapping.",
                            openEhrKey, extractedValueKey);
                    continue;
                }


                fullFlatPath.entrySet().forEach((entry) -> {
                    if (entry.getKey().startsWith(extractedValueKey)) {
                        modifiedJsonObject.add(entry.getKey(), entry.getValue());
                    }
                });
            }

        }
        return modifiedJsonObject;
    }


    /**
     * If a mapping has openehrCondition, then the whole JsonObject representing flatPath Composition needs to be split
     * in a way so that iteration of tha mapping only extracts from the relevant part of the JsonObject
     *
     * @return a split JsonObject if openEhrCondition is not null, otherwise the original fullFlatPath
     */
    public JsonObject splitByOpenEhrCondition(final JsonObject fullFlatPath, final Condition openEhrCondition,
                                              final String firstFlatPath) {
        if (openEhrCondition == null) {
            return fullFlatPath;
        }

        final List<String> narrowingCriteria = narrowingCriteria(openEhrCondition, firstFlatPath, fullFlatPath);

        switch (openEhrCondition.getOperator()) {
            case FhirConnectConst.CONDITION_OPERATOR_ONE_OF -> {
                return handleOneOfOperatorSplit(openEhrCondition,
                                                narrowingCriteria,
                                                fullFlatPath);
            }
            case FhirConnectConst.CONDITION_OPERATOR_EMPTY -> {
                return handleEmptyOperatorSplit(openEhrCondition, narrowingCriteria, fullFlatPath);
            }
        }
        return fullFlatPath;
    }

    private List<String> narrowingCriteria(final Condition openEhrCondition, final String firstFlatPath,
                                           final JsonObject fullFlatPath) {
        final String openEhrPath = openFhirStringUtils.prepareOpenEhrSyntax(openEhrCondition.getTargetRoot(),
                                                                            firstFlatPath);
        final String withRegex = openFhirStringUtils.addRegexPatternToSimplifiedFlatFormat(openEhrPath);
        return openFhirStringUtils.getAllEntriesThatMatch(withRegex, fullFlatPath).stream().distinct().collect(
                Collectors.toList());

    }

}
