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
            ((Extension) toPopulate).setValue((IBaseDatatype) data);
        }
        if (toPopulate instanceof List) {
            final Object lastElement = ((List<?>) toPopulate).get(((List<?>) toPopulate).size() - 1);
            if (lastElement.toString() == null || objectIsEmpty(lastElement)) {
                // you can set on it
                populateElement(lastElement, data);
            } else {
                // add a new entry to the list
                ((List) toPopulate).add(data);
            }
        }

        if (data instanceof Quantity) {
            if (toPopulate instanceof Quantity) {
                ((Quantity) data).copyValues((Quantity) toPopulate);
            }
            if (toPopulate instanceof final Ratio ratioToPopulate) {
                ratioToPopulate.setNumerator(((Quantity) data));
            }
            if (toPopulate instanceof final IntegerType integerTypeToPopulate) {
                integerTypeToPopulate.setValue(((Quantity) data).getValue().intValue());
            }
        } else if (data instanceof DateTimeType) {

            if (toPopulate instanceof DateTimeType) {
                ((DateTimeType) toPopulate).setValue(((DateTimeType) data).getValue());
            }
        } else if (data instanceof TimeType) {

            if (toPopulate instanceof TimeType) {
                ((TimeType) toPopulate).setValue(((TimeType) data).getValue());
            } else if (toPopulate instanceof DateTimeType) {
                final LocalDateTime localDateTime = LocalDateTime.of(LocalDate.now(),
                        LocalTime.of(((TimeType) data).getHour(), ((TimeType) data).getMinute(), (int) ((TimeType) data).getSecond()));
                ((DateTimeType) toPopulate).setValue(Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant()));
            }
        } else if (data instanceof Identifier) {

            if (toPopulate instanceof Identifier) {
                ((Identifier) data).copyValues(((Identifier) toPopulate));
            }
        } else if (data instanceof DateType) {

            if (toPopulate instanceof DateType) {
                ((DateType) toPopulate).setValue(((DateType) data).getValue());
            }
        } else if (data instanceof CodeableConcept) {

            if (toPopulate instanceof CodeableConcept) {
                ((CodeableConcept) data).copyValues(((CodeableConcept) toPopulate));
            }
        } else if (data instanceof Coding) {

            if (toPopulate instanceof Coding) {
                ((Coding) data).copyValues(((Coding) toPopulate));
            } else if (toPopulate instanceof Enumeration<?>) {
                ((Enumeration<?>) toPopulate).setValueAsString(((Coding) data).getCode());
            }
        } else if (data instanceof Attachment) {
            if (toPopulate instanceof Attachment) {
                ((Attachment) data).copyValues(((Attachment) toPopulate));
            }
        } else if (data instanceof StringType) {
            if (toPopulate instanceof Enumeration) {
                ((Enumeration) toPopulate).setValueAsString(((StringType) data).getValueAsString());
            } else if (toPopulate instanceof PrimitiveType<?>) {
                ((PrimitiveType) toPopulate).setValue(((StringType) data).getValue());
            }
        } else if (data instanceof BooleanType) {
            if(toPopulate instanceof BooleanType) {
                ((BooleanType) toPopulate).setValue(((BooleanType) data).getValue());
            }
        }
    }
}
