package com.medblocks.openfhir.util;

import com.medblocks.openfhir.tofhir.OpenEhrToFhirHelper;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseDatatype;
import org.hl7.fhir.r4.model.*;
import org.springframework.stereotype.Component;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

/**
 * Used for populating a FHIR Base
 */
@Slf4j
@Component
public class FhirInstancePopulator {

    /**
     * data can be anything from OpenEhrToFhir.valueToDataPoint, a limited set of things.
     *
     * Populates an element with the data. Population logic depends on the type of toPopulate object
     */
    public void populateElement(Object toPopulate, final OpenEhrToFhirHelper.DataWithIndex dataH) {
        populateElement(toPopulate, dataH.getData());
    }

    private Boolean objectIsEmpty(final Object lastElement) {
        try {
            return (Boolean) lastElement.getClass().getMethod("isEmpty").invoke(lastElement);
        } catch (InvocationTargetException | NoSuchMethodException | IllegalAccessException e) {
            return false;
        }
    }

    public void populateElement(Object toPopulate, final Base data) {
        if (toPopulate instanceof Extension && data instanceof IBaseDatatype) {
            setExtensionValue((Extension) toPopulate, (IBaseDatatype) data);
            return;
        }

        if (toPopulate instanceof List) {
            populateListElement((List<?>) toPopulate, data);
            return;
        }

        handleSpecificTypePopulation(toPopulate, data);
    }

    private void setExtensionValue(Extension extension, IBaseDatatype data) {
        extension.setValue(data);
    }

    private void populateListElement(List<?> toPopulate, Base data) {
        final Object lastElement = toPopulate.get(toPopulate.size() - 1);
        if (lastElement.toString() == null || objectIsEmpty(lastElement)) {
            populateElement(lastElement, data); // Populate last element if empty
        } else {
            ((List<Object>) toPopulate).add(data); // Otherwise, add new entry
        }
    }

    private void handleSpecificTypePopulation(Object toPopulate, Base data) {
        if (data instanceof Quantity) {
            populateQuantity(toPopulate, (Quantity) data);
        } else if (data instanceof DateTimeType) {
            populateDateTime(toPopulate, (DateTimeType) data);
        } else if (data instanceof TimeType) {
            populateTimeType(toPopulate, (TimeType) data);
        } else if (data instanceof Identifier) {
            populateIdentifier(toPopulate, (Identifier) data);
        } else if (data instanceof DateType) {
            populateDateType(toPopulate, (DateType) data);
        } else if (data instanceof CodeableConcept) {
            populateCodeableConcept(toPopulate, (CodeableConcept) data);
        } else if (data instanceof Coding) {
            populateCoding(toPopulate, (Coding) data);
        } else if (data instanceof Attachment) {
            populateAttachment(toPopulate, (Attachment) data);
        } else if (data instanceof StringType) {
            populateStringType(toPopulate, (StringType) data);
        } else if (data instanceof BooleanType) {
            populateBooleanType(toPopulate, (BooleanType) data);
        }
    }

    private void populateQuantity(Object toPopulate, Quantity data) {
        if (toPopulate instanceof Quantity) {
            data.copyValues((Quantity) toPopulate);
        } else if (toPopulate instanceof Ratio ratioToPopulate) {
            ratioToPopulate.setNumerator(data);
        } else if (toPopulate instanceof IntegerType integerTypeToPopulate) {
            integerTypeToPopulate.setValue(data.getValue().intValue());
        }
    }

    private void populateDateTime(Object toPopulate, DateTimeType data) {
        if (toPopulate instanceof DateTimeType) {
            ((DateTimeType) toPopulate).setValue(data.getValue());
        }
    }

    private void populateTimeType(Object toPopulate, TimeType data) {
        if (toPopulate instanceof TimeType) {
            ((TimeType) toPopulate).setValue(data.getValue());
        } else if (toPopulate instanceof DateTimeType) {
            LocalDateTime localDateTime = LocalDateTime.of(LocalDate.now(),
                    LocalTime.of(data.getHour(), data.getMinute(), (int) data.getSecond()));
            ((DateTimeType) toPopulate).setValue(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()));
        }
    }

    private void populateIdentifier(Object toPopulate, Identifier data) {
        if (toPopulate instanceof Identifier) {
            data.copyValues((Identifier) toPopulate);
        }
    }

    private void populateDateType(Object toPopulate, DateType data) {
        if (toPopulate instanceof DateType) {
            ((DateType) toPopulate).setValue(data.getValue());
        }
    }

    private void populateCodeableConcept(Object toPopulate, CodeableConcept data) {
        if (toPopulate instanceof CodeableConcept) {
            data.copyValues((CodeableConcept) toPopulate);
        }
    }

    private void populateCoding(Object toPopulate, Coding data) {
        if (toPopulate instanceof Coding) {
            data.copyValues((Coding) toPopulate);
        } else if (toPopulate instanceof Enumeration<?>) {
            ((Enumeration<?>) toPopulate).setValueAsString(data.getCode());
        }
    }

    private void populateAttachment(Object toPopulate, Attachment data) {
        if (toPopulate instanceof Attachment) {
            data.copyValues((Attachment) toPopulate);
        }
    }

    private void populateStringType(Object toPopulate, StringType data) {
        if (toPopulate instanceof Enumeration) {
            ((Enumeration<?>) toPopulate).setValueAsString(data.getValueAsString());
        } else if (toPopulate instanceof PrimitiveType<?>) {
            ((PrimitiveType<String>) toPopulate).setValue(data.getValue());
        }
    }

    private void populateBooleanType(Object toPopulate, BooleanType data) {
        if (toPopulate instanceof BooleanType) {
            ((BooleanType) toPopulate).setValue(data.getValue());
        }
    }

}
