package com.medblocks.openfhir.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.medblocks.openfhir.fc.FhirConnectConst;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.List;

import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;

/**
 * Class used for populating openEHR flat path Composition
 */
@Slf4j
@Component
public class OpenEhrPopulator {

    private OpenFhirMapperUtils openFhirMapperUtils;

    @Autowired
    public OpenEhrPopulator(OpenFhirMapperUtils openFhirMapperUtils) {
        this.openFhirMapperUtils = openFhirMapperUtils;
    }

    /**
     * Adds extracted value to the openEHR flat path composition represented with the 'constructingFlat' variable
     *
     * @param openEhrPath      path that should be used in the flat path composition
     * @param extractedValue   value as extracted from a FHIR object
     * @param openEhrType      openEHR type as defined in the fhir connect model mapping
     * @param constructingFlat composition in a flat path format that's being constructed
     */
    public void setFhirPathValue(final String openEhrPath, final Base extractedValue, final String openEhrType, final JsonObject constructingFlat) {
        if (openEhrType == null) {
            addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);
            return;
        }
        if (OPENEHR_TYPE_NONE.equals(openEhrType)) {
            log.warn("Adding nothing on path {} as type is marked as NONE", openEhrPath);
            return;
        }
        if (extractedValue == null) {
            log.warn("Extracted value is null");
            return;
        }
        switch (openEhrType) {
            case FhirConnectConst.DV_MULTIMEDIA:
                if (extractedValue instanceof Attachment) {
                    final Attachment extractedAtt = (Attachment) extractedValue;
                    int size = extractedAtt.getSize();
                    if (size == 0 && extractedAtt.getData() != null) {
                        size = extractedAtt.getData().length;
                    }
                    addToConstructingFlat(openEhrPath + "|size", String.valueOf(size), constructingFlat);
                    addToConstructingFlat(openEhrPath + "|mediatype", extractedAtt.getContentType(), constructingFlat);
                    if (StringUtils.isNotEmpty(extractedAtt.getUrl())) {
                        addToConstructingFlat(openEhrPath + "|url", extractedAtt.getUrl(), constructingFlat); // todo? what if url?
                    } else {
                        addToConstructingFlat(openEhrPath + "|data", Base64.getEncoder().encodeToString(extractedAtt.getData()), constructingFlat); // todo? what if url?
                    }
                    return;
                } else {
                    log.warn("openEhrType is MULTIMEDIA but extracted value is not Attachment; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_QUANTITY:
                if (extractedValue instanceof Quantity) {
                    final Quantity extractedQuantity = (Quantity) extractedValue;
                    if (extractedQuantity.getValue() != null) {
                        addToConstructingFlatDouble(openEhrPath + "|magnitude", extractedQuantity.getValue().doubleValue(), constructingFlat);
                    }
                    addToConstructingFlat(openEhrPath + "|unit", extractedQuantity.getUnit(), constructingFlat);
                    return;
                } else if (extractedValue instanceof Ratio) {
                    final Ratio extractedRatio = (Ratio) extractedValue;
                    setFhirPathValue(openEhrPath, extractedRatio.getNumerator(), openEhrType, constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is DV_QUANTITY but extracted value is not Quantity and not Ratio; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_ORDINAL:
                if (extractedValue instanceof Quantity) {
                    final Quantity extractedQuantity = (Quantity) extractedValue;
                    if (extractedQuantity.getValue() != null) {
                        addToConstructingFlat(openEhrPath + "|ordinal", extractedQuantity.getValue().toPlainString(), constructingFlat);
                    }
                    addToConstructingFlat(openEhrPath + "|value", extractedQuantity.getUnit(), constructingFlat);
                    addToConstructingFlat(openEhrPath + "|code", extractedQuantity.getCode(), constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is DV_ORDINAL but extracted value is not Quantity; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_PROPORTION:
                if (extractedValue instanceof Quantity) {
                    final Quantity extractedQuantity = (Quantity) extractedValue;
                    if ("%".equals(extractedQuantity.getCode())) {
                        // then denominator is 100 and value is as it is
                        addToConstructingFlatDouble(openEhrPath + "|denominator", 100.0, constructingFlat);
                    }
                    if (extractedQuantity.getValue() != null) {
                        addToConstructingFlatDouble(openEhrPath + "|numerator", extractedQuantity.getValue().doubleValue(), constructingFlat);
                    }
                    // todo: denominator? and type? hardcoded?
                    addToConstructingFlat(openEhrPath + "|type", "2", constructingFlat); // hardcoded???

                    return;
                } else {
                    log.warn("openEhrType is DV_PROPORTION but extracted value is not Quantity; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_COUNT:
                if (extractedValue instanceof Quantity) {
                    final Quantity extractedQuantity = (Quantity) extractedValue;
                    if (extractedQuantity.getValue() != null) {
                        addToConstructingFlatInteger(openEhrPath, extractedQuantity.getValue().intValueExact(), constructingFlat);
                    }
                    return;
                } else if (extractedValue instanceof IntegerType) {
                    final IntegerType extractedInteger = (IntegerType) extractedValue;
                    if (extractedInteger.getValue() != null) {
                        addToConstructingFlatInteger(openEhrPath, extractedInteger.getValue(), constructingFlat);
                    }
                    return;
                } else {
                    log.warn("openEhrType is DV_PROPORTION but extracted value is not Quantity and not IntegerType; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_DATE_TIME:
                if (extractedValue instanceof DateTimeType) {
                    log.info("openEhrType is DATE_TIME and extracted value is DateTimeType");
                    final DateTimeType extractedDateTime = (DateTimeType) extractedValue;
                    if (extractedDateTime.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.dateTimeToString(extractedDateTime.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
                if (extractedValue instanceof DateType) {
                    log.info("openEhrType is DATE_TIME and extracted value is DateType");
                    final DateType extractedDate = (DateType) extractedValue;
                    if (extractedDate.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.dateToString(extractedDate.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
                if (extractedValue instanceof TimeType) {
                    log.info("openEhrType is DATE_TIME and extracted value is TimeType");
                    final TimeType extractedTime = (TimeType) extractedValue;
                    if (extractedTime.getValue() != null) {
                        addToConstructingFlat(openEhrPath, extractedTime.getValue(), constructingFlat);
                    }
                    return;
                }
            case FhirConnectConst.DV_DATE:
                if (extractedValue instanceof DateTimeType) {
                    log.info("openEhrType is DV_DATE and extracted value is DateTimeType");
                    final DateTimeType extractedDateTime = (DateTimeType) extractedValue;
                    if (extractedDateTime.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.dateToString(extractedDateTime.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
                if (extractedValue instanceof DateType) {
                    log.info("openEhrType is DV_DATE and extracted value is DateType");
                    final DateType extractedDate = (DateType) extractedValue;
                    if (extractedDate.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.dateToString(extractedDate.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
            case FhirConnectConst.DV_TIME:
                if (extractedValue instanceof DateTimeType) {
                    log.info("openEhrType is DV_TIME and extracted value is DateTimeType");
                    final DateTimeType extractedDateTime = (DateTimeType) extractedValue;
                    if (extractedDateTime.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.timeToString(extractedDateTime.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
                if (extractedValue instanceof DateType) {
                    log.info("openEhrType is DV_TIME and extracted value is DateType");
                    final DateType extractedDate = (DateType) extractedValue;
                    if (extractedDate.getValue() != null) {
                        final String formattedDate = openFhirMapperUtils.timeToString(extractedDate.getValue());
                        addToConstructingFlat(openEhrPath, formattedDate, constructingFlat);
                    }
                    return;
                }
                if (extractedValue instanceof TimeType) {
                    log.info("openEhrType is DV_TIME and extracted value is TimeType");
                    final TimeType extractedTime = (TimeType) extractedValue;
                    if (extractedTime.getValue() != null) {
                        addToConstructingFlat(openEhrPath, extractedTime.getValue(), constructingFlat);
                    }
                    return;
                }
            case FhirConnectConst.DV_CODED_TEXT:
                if (extractedValue instanceof CodeableConcept) {
                    final CodeableConcept extractedCodebleConcept = (CodeableConcept) extractedValue;
                    final List<Coding> codings = extractedCodebleConcept.getCoding();
                    if (!codings.isEmpty()) {
                        final Coding coding = codings.get(0);
                        addToConstructingFlat(openEhrPath + "|code", coding.getCode(), constructingFlat);
                        addToConstructingFlat(openEhrPath + "|terminology", coding.getSystem(), constructingFlat);
                    }
                    addToConstructingFlat(openEhrPath + "|value", extractedCodebleConcept.getText(), constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is DV_CODED_TEXT but extracted value is not CodeableConcept; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.IDENTIFIER:
                if (extractedValue instanceof Identifier) {
                    final Identifier extractedIdentifier = (Identifier) extractedValue;
                    addToConstructingFlat(openEhrPath + "|id", extractedIdentifier.getValue(), constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is IDENTIFIER but extracted value is not Identifier; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.CODE_PHRASE:

                if (extractedValue instanceof Coding) {
                    final Coding extractedCoding = (Coding) extractedValue;
                    addToConstructingFlat(openEhrPath + "|code", extractedCoding.getCode(), constructingFlat);
                    addToConstructingFlat(openEhrPath + "|value", extractedCoding.getCode(), constructingFlat); // todo? why?? not per fhir connect spec, but what if some require it?
                    addToConstructingFlat(openEhrPath + "|terminology", extractedCoding.getSystem(), constructingFlat);
                    return;
                } else if (extractedValue instanceof Extension extractedExtension) {
                    setFhirPathValue(openEhrPath, extractedExtension.getValue(), openEhrType, constructingFlat);
                    return;
                } else if (extractedValue instanceof CodeableConcept extractedCodeable) {
                    setFhirPathValue(openEhrPath, extractedCodeable.getCodingFirstRep(), openEhrType, constructingFlat);
                    return;
                } else if (extractedValue instanceof Enumeration extractedEnum) {
                    addToConstructingFlat(openEhrPath + "|code", extractedEnum.getValueAsString(), constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is CODE_PHRASE but extracted value is not Coding, not CodeableConcept and not Enumeration; is {}", extractedValue.getClass());
                }
            case FhirConnectConst.DV_TEXT:
                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);
                return;
            case FhirConnectConst.DV_BOOL:
                final Base extractedValue1 = extractedValue;
                if (extractedValue1 instanceof StringType && ((StringType) extractedValue1).getValue() != null) {
                    addToConstructingBoolean(openEhrPath, Boolean.valueOf(((StringType) extractedValue1).getValue()), constructingFlat);
                    return;
                } else if (extractedValue1 instanceof BooleanType && ((BooleanType) extractedValue1).getValue() != null) {
                    addToConstructingBoolean(openEhrPath, ((BooleanType) extractedValue1).getValue(), constructingFlat);
                    return;
                } else {
                    log.warn("openEhrType is DV_BOOL but extracted value is not StringType and not BooleanType; is {}", extractedValue1.getClass());
                }
                return;
            default:
                addValuePerFhirType(extractedValue, openEhrPath, constructingFlat);

        }
    }

    private void addValuePerFhirType(final Base fhirValue, final String openEhrPath, final JsonObject constructingFlat) {
        if (fhirValue instanceof Quantity) {
            final Quantity extractedQuantity = (Quantity) fhirValue;
            if (extractedQuantity.getValue() != null) {
                addToConstructingFlat(openEhrPath, extractedQuantity.getValue().toPlainString(), constructingFlat);
            }
        } else if (fhirValue instanceof Coding) {
            final Coding extractedQuantity = (Coding) fhirValue;
            addToConstructingFlat(openEhrPath, extractedQuantity.getCode(), constructingFlat);
        } else if (fhirValue instanceof DateTimeType) {
            final DateTimeType extractedQuantity = (DateTimeType) fhirValue;
            addToConstructingFlat(openEhrPath, extractedQuantity.getValueAsString(), constructingFlat);
        } else if (fhirValue instanceof Annotation) {
            final Annotation extracted = (Annotation) fhirValue;
            addToConstructingFlat(openEhrPath, extracted.getText(), constructingFlat);
        } else if (fhirValue instanceof Address) {
            final Address extracted = (Address) fhirValue;
            addToConstructingFlat(openEhrPath, extracted.getText(), constructingFlat);
        } else if (fhirValue instanceof HumanName) {
            final HumanName extracted = (HumanName) fhirValue;
            addToConstructingFlat(openEhrPath, extracted.getNameAsSingleString(), constructingFlat);
        } else if (fhirValue instanceof Extension) {
            final Extension extracted = (Extension) fhirValue;
            addToConstructingFlat(openEhrPath, extracted.getValue().hasPrimitiveValue() ? extracted.getValue().primitiveValue() : null, constructingFlat);
        } else if (fhirValue.hasPrimitiveValue()) {
            addToConstructingFlat(openEhrPath, fhirValue.primitiveValue(), constructingFlat);
        } else {
            log.error("Unsupported fhir value toString!: {}", fhirValue.toString());
        }
    }

    final void addToConstructingFlat(final String key, final String value, final JsonObject constructingFlat) {
        if (StringUtils.isEmpty(value)) {
            return;
        }
        log.debug("Setting value {} on path {}", value, key);
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    final void addToConstructingBoolean(final String key, final Boolean value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.addProperty(key, (Boolean) value);
    }

    final void addToConstructingFlatDouble(final String key, final Double value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.add(key, new JsonPrimitive(value));
    }

    final void addToConstructingFlatInteger(final String key, final Integer value, final JsonObject constructingFlat) {
        if (value == null) {
            return;
        }
        constructingFlat.add(key, new JsonPrimitive(value));
    }
}
