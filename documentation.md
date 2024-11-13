# Specification of FHIR Connect mapping files

## Purpose

This mapping file specification lays the foundation for

- bi-directional
- and reusable

mappings between openEHR and FHIR data.

Its goal is it to create one common definition, to enable a community driven effort to collaboratively

- build
- discuss and maintain
- create a globally shared library of

mappings for commonly used modeling artifacts.

This document specifies the concepts, structure and properties of such mappings.

A reference implementation was created and is maintained by [Better](https://better.care).

## Content

<!-- auto generated, manually refresh after changing content -->
<!-- TOC -->
* [Specification of FHIR Connect mapping files](#specification-of-fhir-connect-mapping-files)
    * [Purpose](#purpose)
    * [Content](#content)
    * [1. Model Mappings](#1-model-mappings)
        * [Foundations](#foundations)
        * [Paths](#paths)
        * [Variables](#variables)
        * [Data Types](#data-types)
        * [Condition](#condition)
        * [Slots](#slots)
        * [Dependent Mappings](#dependent-mappings)
            * [Slot Targets](#slot-targets)
            * [Class Mappings](#class-mappings)
            * [FHIR Reference Mapping](#fhir-reference-mapping)
        * [Structure](#structure)
        * [Format](#format)
    * [2. Contextual Mappings](#2-contextual-mappings)
        * [Foundations](#foundations)
            * [Coming from openEHR](#coming-from-openehr)
            * [Coming from FHIR](#coming-from-fhir)
        * [Overview](#overview)
        * [Format](#format)
    * [Examples & Schema Test Suite](#examples--schema-test-suite)
    * [Implementation](#implementation)
    * [Limitations](#limitations)
    * [Future Work](#future-work)
* [Appendix](#appendix)
    * [Tooling](#tooling)
    * [Changelog](#changelog)
        * [v0.1.0](#v010)
            * [Added](#added)
            * [Changed](#changed)
        * [Removed](#removed)
        * [v0.0.3](#v003)
            * [Added](#added)
<!-- TOC -->

--------------------------------------------------------

Note: As a general specification decision, the following supported features, cases, types and so on are kept to a minimal set on purpose.
The idea is to add support step-by-step and maintain backwards-compatibility.

There are two kinds of mapping files:

1. Model Mappings
2. Contextual Mappings

## 1. Model Mappings

The Model Mappings are the core mappings, aiming at providing a translation from and to core artifacts of openEHR and FHIR.

### Foundations

Due to openEHR`s maximal modeling approach and the fact that openEHR data carries more metadata, the Model Mappings are written in the direction
"from FHIR to openEHR". The backwards direction is implicitly included.

The mapping processing application is supposed to have access to the openEHR template, with the embedded Archetype definitions.
Thus, the scope's openEHR modeling information is available and mappings can be simpler than technically necessary (for instance, see [Path](#paths)).

### Paths

Pointing to source and result attributes happens with *paths*.
On the root level of a Model Mapping, paths are preceded with a context variable like `$fhirResource` and `$openEhrArchetype`.
Paths are always terminated with the node *before* the actual data type attribute,
i.e. pointing to the `DV_QUANTITY` container, rather than the `magnitude` attribute.

The following paths are defined in
- openEHR: Custom path, derived by simplifying the FLAT format and using the common separator (`.`)
    - Example: `"$openEhrArchetype.blood_pressure.any_event.systolic"`
- FHIR: Simple [FHIRPath](https://hl7.org/fhirpath/#path-selection) compatible selection paths
    - Example: `"$fhirResource.component.value"`

openEHR paths omit cardinality information to simplify the mapping data.
For instance, the following valid FLAT path `xyz.blood_pressure/any_event:0/systolic|magnitude`
will be simplified to `xyz.blood_pressure.any_event.systolic`.
This simplification makes use of having the modeling information (in form of Templates) available at runtime.
(Additionally, this example makes use of Data Type simplification, as explained in [Data Types](#data-types)).

The path element separator is always a single, simple `.`.

### Variables

Modeling variables/references are required to create reusable paths:

| Variable            | Description                                                                              |
|---------------------|------------------------------------------------------------------------------------------|
| `$fhirResource`     | FHIR object at root, if Resource                                                         |
| `$fhirRoot`         | FHIR object at root, if no Resource                                                      |
| `$fhirParentPath`   | The path from where the slotArchetype or the profile append is called                    |
| `$openEhrArchetype` | openEHR placeholder marking the beginning of the context's Archetype                     |
| `$composition`      | path of the composition                                                                  |
| `$reference`        | Helper to indicate a skipped path, due to a [Reference Mapping](#fhir-reference-mapping) |

It is also possible to ask the implementation to provide further information from the execution context.
Here, the openEHR execution environment - with `$openEhrContext` as root and available with each of the following attached after a separator:

| openEHR Context | Description                 |
|-----------------|-----------------------------|
| `$ehr`          | EHR ID of request's context |

Context variables are meant to be of primitive type only, i.e. natively transformable to String, to avoid cross-type compatibility problems.
Additionally, due to overlapping data types (see [Data Types](#data-types)), context variables can only have non-overlapping types. Because they have no openEHR modeling information to infer the specific type from.

Future versions of this spec could reflect support for further data like: language, committer, subject and so on.

Terminologies and coding systems can be referenced by:

| System | Identifier |
|--------|------------|
| SNOMED | `$snomed`  |
| LOINC  | `$loinc`   |

Currently, no algorithmic difference is made by this choice. This information aids the authoring of the mappings only, as of now.
This will be needed for the advanced terminology support though (see [Future Work](#future-work)).

### Data Types

Data types, such as `DV_QUANTITY`, `DV_TEXT` and so on, and their FHIR counterparts (`Quantity`, `string`, ...) are handled implicitly,
**except** the need to set the type for a mapping with `with.type`.

The internal mapping of data types:

| Type ID / FHIR  | openEHR       | Primitive | "FLAT / FHIR" Attributes Pairs                                                                    |
|-----------------|---------------|-----------|---------------------------------------------------------------------------------------------------|
| NONE            | NONE          | false     | /                                                                                                 |
| QUANTITY        | DV_QUANTITY   | true      | magnitude / value <br/> unit / unit                                                               |
| QUANTITY        | DV_ORDINAL    | true      | ordinal / value <br/> value / unit <br/> code / code                                              |
| QUANTITY        | DV_COUNT      | true      | (direct)                                                                                          |
| CODEABLECONCEPT | DV_CODED_TEXT | false     | **_nested_** / coding <br/> value / text                                                          |
| DATETIME        | DV_DATE_TIME  | true      | (direct) will also map onSet times into DateTime                                                  |
| CODING          | CODE_PHRASE   | true      | code / code <br/> terminology / system                                                            |
| STRING          | DV_TEXT       | true      | (direct)                                                                                          |
| DOSAGE          | NONE          | false     | (special)                                                                                         |
| IDENTIFIER      | DV_IDENTIFIER | false     | identifier value to DV_ID value, type to type,  Organization name /code -> DV_IDENTIFIER assigner |
                               
The `nested` keyword indicates a non-primitive case, where the final resulting structure is a nested structure, composed of multiple types.

A simple `QUANTITY`/`DV_QUANTITY` example is illustrated in the following code block.
It implicitly maps the matching attributes (openEHR FLAT: `magnitude`, `value`; FHIR: `value`, `unit`) too.

```yaml
- name: "height"
  with:
    fhir: "$fhirResource.value"
    openehr: "$openEhrArchetype.height_length.any_event.height_length"
    type: "QUANTITY"
```

The Type ID `QUANTITY` also shows overlapping types.
This is construct is necessary, because FHIR and openEHR have different paradigms on base data types.
In Model Mappings, usage of `type: "QUANTITY"` would refer to either of the linked openEHR types (as per table above).
Using the openEHR modeling information from the Template, the mapping engine can infer the correct type at the given path.
It will select the matching supported type.
For instance, in the case of `QUANTITY`, either one of `DV_QUANTITY`, `DV_ORDINAL`, or `DV_COUNT`.

`DOSAGE` is a one of the few ["special purpose data types"](http://hl7.org/fhir/datatypes.html) in FHIR and needs separate handling.
Currently, it is the first supported custom class data type.
As special type it needs to be declared with one extra step, to allow the mappings of its attributes (see formal description at [Class Mappings](#class-mappings)).
It can be utilized in the following way:

```yaml
mappings:
  - name: "dosage"
    with:
      fhir: "$fhirRoot"
      openehr: "$openEhrArchetype"
      type: "DOSAGE"
    followedBy:
      mappings:
        - name: "doseQuantityValue"
          with:
            fhir: "$fhirRoot.doseAndRate.dose"
            openehr: "$openEhrArchetype.dose_amount.quantity_value"
            type: "QUANTITY"
```

### Condition

The `condition` structure is used to find the correct node within ambiguous lists. It also is used to set one condition to `identifying: true`, which allows
to define the Condition to use in the evaluation of which Model Mapping matches the given FHIR Resource.
(openEHR data has the Archetype ID and needs no further evaluation.)

Having one *identifying* `condition` for one Model Mapping is required.
This is not the case for [Dependent Mappings](#dependent-mappings).

The condition's `targetRoot` path points to the root element, which this evaluation should find (matching the condition).
It most cases this root is a list.

In contrast, the `targetAttribute` paths points to the attribute of the root objects the evaluation will be executed with.
This path is always a direct child-path of the root.

The chosen `operator` defines the algorithmic behavior, when evaluating this condition.
Depending on the operator, a specific criteria can be set.
The available options are:

| Name     | Description                                                   | Criteria                                  |
|----------|---------------------------------------------------------------|-------------------------------------------|
| "one of" | Checks for existence of either one of the criteria.           | Comma separated list, enclosed by `[...]` |
| "not in" | Checks for non existence of either one of the criteria.       | Comma separated list, enclosed by `[...]` |
| "empty"  | Checks for not existence of a path                            | Comma separated list, enclosed by `[...]` |
| "type"   | Checks for the type of the path provided (DV_IDENTIFIER ....) | Comma separated list, enclosed by `[...]` |



**Example**:

```yaml
condition:
  targetRoot: "$fhirResource.component"           # Scope of object for 'condition'
  targetAttribute: "code.coding.code"             # Actual attribute to check for 'condition'
  operator: "one of"
  criteria: "[$loinc.8480-6, $snomed.271649006]"
  identifying: true                               # The *one* condition able to identify FHIR input to match this mapping
```

A FHIR Resource like Observation can have multiple components, containing different data points.
This condition is used to look into each component, checking its coding, finally finding the matching one.

Further, Conditions are only supported for Observations right now.
(While technically more types can be supported, this is a specification decision to add support step-by-step and keep backwards-compatibility.)

### Slots

In openEHR Slots are commonly used. A model mapping can utilize `slotArchetype` to configure a Slot.
The following example shows how a Slot can be described in a mapping.
The `type` on the root level of a Slot mapping needs to be `NONE`.
It is necessary to pipe down a FHIR object as the root object for the following step (the linked Archetype's mapping) with `with.fhir`.
In contrast, the openEHR path is basically predefined by the model and points to the actual place of the Slot.

```yaml
# openEHR data is linking to another Archetype (Cluster Slot)
- name: "therapeutic-direction"
  with:
    fhir: "$fhirResource" # Only forwarding the root resource, as attributes will be handled on following mappings.
    openehr: "$openEhrArchetype.medication_order.order.therapeutic_direction"
    type: "NONE"
  slotArchetype: "openEHR-EHR-CLUSTER.therapeutic_direction.v1"   # acts as link to Model Mapping of given Archetype
```

### Dependent Mappings

Sometimes the given openEHR model structure utilizes Archetypes with no direct FHIR Resource equivalent.
In those cases a "dependent" Model Mapping is required, which can never be a root level mapping itself.
They are identified by **not** having a `fhirConfig` section.

Dependent mappings never can have a `condition`.

The following **concrete** Dependent Mapping subclasses exist:

#### Slot Targets

A Slot mapping can point to a normal, independent Model Mapping of the matching Archetype.
But it can also point to a *Slot Target*.
The following example illustrates a case, where the Archetype is used as intermediate link and forwards the mapping chain to another Slot mapping.

```yaml
format: "0.1.0"
version: "0.0.1"

# No fhirConfig, because this model has no direct Resource equivalent in FHIR. It can only be used in openEHR Archetype slots.

openEhrConfig:
  archetype: "openEHR-EHR-CLUSTER.therapeutic_direction.v1"

mappings:
  - name: "dosage"
    with:
      fhir: "$fhirResource.dosageInstruction"
      openehr: "$openEhrArchetype.dosage"
      type: "NONE"
    slotArchetype: "openEHR-EHR-CLUSTER.dosage.v1"                   # acts as link to Model Mapping of given Archetype
```

Note: `with.fhir` utilized the Resource-scope `$fhirResource` variable, because the resource is piped down here, see [Slots](#slots).

#### Class Mappings

The Class Mapping is a another subclass of the Dependent Mapping.
It has no direct FHIR Resource equivalent, because it maps to a non-Resource-level FHIR class.
One example (as explained in the ["Data Types"](#data-types) section) is `DOSAGE`.

The mapping is facilitated by defining a shadow root mapping with the special case class as type.
It is required to indicate the custom type at `type`.
Further, a semantically correct variable is available to reference the object's root: `$fhirRoot`
(in contrast to `$fhirResource`, because by definition this is not a FHIR Resource in this case).

The following data points are mapped using `followedBy`.
The listed mappings are directly populating the class' attributes.
They never can contain a non-trivial mapping, like another Slot or Class mapping.

A simple `DOSAGE` example would look like this:

```yaml
format: "0.1.0"
version: "0.0.1"

# No fhirConfig, because this model has no direct Resource equivalent in FHIR. It can only be used in openEHR Archetype slots or as FHIR Resource attribute.

openEhrConfig:
  archetype: "openEHR-EHR-CLUSTER.dosage.v1"

mappings:
  - name: "dosage"
    with:
      fhir: "$fhirRoot"
      openehr: "$openEhrArchetype"
      type: "DOSAGE"    # Special FHIR type class
    followedBy:
      mappings:
        - name: "doseQuantityValue"
          with:
            fhir: "doseAndRate.dose"
            openehr: "$openEhrArchetype.dose_amount.quantity_value"
            type: "QUANTITY"
```

#### FHIR Reference Mapping

FHIR models often make use of [Resource References](https://www.hl7.org/fhir/references.html).
They can be mapped with the `reference` property.
Here, the `with.fhir` path indicates the path of the Reference in the root Resource.
In contrast, `with.openehr` is virtually ignored, because the path of the following Reference mapping will be considered.
To help the editorial process, a `$reference` helper can be used here.

The `reference` structure contains the explicit Resource typing and a list of mappings.
Only one of those sub-mappings is allowed, to cancel out any ambiguity.
This structure results in a separate sub-Resource, which will be added to the Bundle and reference by the root Resource.
For convenience, the `alwaysBundled` property is used to set this Model Mapping's result as a Bundle, by definition.
(Also see [2. Contextual Mappings](#2-contextual-mappings) for more info on configuring the result scope.)

```yaml
fhirConfig:
  # For this Resource: http://hl7.org/fhir/medicationrequest.html
  resource: "MedicationRequest"
  # Convenience property to indicate at least one mapping directly works with a FHIR reference -> the resulting FHIR has to be a Bundle.
  alwaysBundled: true
openEhrConfig:
  # For this Archetype: https://ckm.openehr.org/ckm/archetypes/1013.1.5946
  archetype: "openEHR-EHR-INSTRUCTION.medication_order.v2"  # Note: Is actually at v3 but the use-case has v2

mappings:
  - name: "medication"
    with:
      fhir: "$fhirResource.medication"
      openehr: "$reference"   # the path of the following reference mapping will be used
      type: "NONE"
    # States the FHIR target/source is a Reference.
    reference:
      # Implicitly is a "direct" reference, i.e. creates ad-hoc sub-resource. It will be added to the Bundle and referenced in the root Resource.
      resourceType: "Medication"
      mappings:
        - name: "medication-item"
          with:
            fhir: "code.text"
            openehr: "$openEhrArchetype.medication_order.order.medication_item"
            type: "STRING"
```

### Structure

The exact format is defined in a JSON schema (see below). This section will give a descriptive overview of the structure.

- `format` containing the current format's version.
- `version` the version of this particular mapping.
- `fhirConfig` defines the target and source FHIR Resource type.
- `openEhrConfig` defines the target and source openEHR Archetype.
- `mappings` is a list of mappings.

Mapping instances consist of:

- `name` is a descriptor for documentation purposes.
- `with` sets the target/source attributes for both openEHR and FHIR, as well as type.
- `condition` can contain a set of properties to describe the condition on which this mapping should be applied on.
- `followedBy` can contain a nested (recursive) list of further mappings, which shall be applied in the same context as the root one, without the need to
  re-evaluate the condition again. Only applicable in the case of a special data type.
- `reference` can be used to define a reference mapping. It allows to map attributes to a separate FHIR resource, which will be referenced by the root resource.
  (And vice versa)
- `slotArchetype` can specify the Slot Archetype.

### Format

[Model Mapping JSON Schema](src/test/resources/mapping-files-spec/v0-2-0/model-mapping.schema.json)

File extension: `*.model.yml` or `*.model.yaml`

--------------------------------------------------------

## 2. Contextual Mappings

The main purpose of the Contextual Mapping is to make use of the Model Mappings in certain contexts.

### Foundations

#### Coming from openEHR

The usual openEHR request to create a Composition carries all relevant (meta) data.
This includes the used Template and other useful information like the target EHR ID.

The `templateId` property will be used to evaluate the matching Contextual Mapping.
Therefore, the payload doesn't have to match all the listed Archetypes (but in compliance with the Template constraints).

#### Coming from FHIR

There's a lack of metadata. In consequence, it is not directly known, for instance, what kind of clinical context a set of Resources in a Bundle represents.

The list of `archetypes` will be used to evaluate the matching Contextual Mapping. In consequence, the FHIR input payload need to 100% match the given list.

### Overview

The exact format is defined in a JSON schema (see below). This section will give a descriptive overview of the structure.

- `format` containing the current format's version.
- `openEHR` configures the openEHR context, i.e. Template and Archetypes.
- `fhir` configures the FHIR context, i.e. Resource type or Bundle.

The `openEHR` configuration contains this context's Template ID and the list of root level Archetypes to consider.

The `fhir` configuration sets the resulting type. This can either be a `Bundle` or any of the following single Resources:

| Value               | Resource Type     |
|---------------------|-------------------|
| `Observation`       | Observation       |
| `MedicationRequest` | MedicationRequest |
| `Medication`        | Medication        |


### Format

[Contextual Mapping JSON Schema](src/test/resources/mapping-files-spec/v0-2-0/contextual-mapping.schema.json)

File extension: `*.context.yml` or `*.context.yaml`

--------------------------------------------------------

## Examples & Schema Test Suite

Please explore the test code and resources in this repository.
It contains a testing suite, evaluating multiple examples with the given schema definition - both for Model and Contextual Mappings.

--------------------------------------------------------

## Implementation

On top of the formal definition, further properties are expected by implementations:

- Mapped attributes, which aren't present in the input, should be ignored.
  If at least one mapping has an input value a valid result artifact is expected as output.
  (Bidirectional, and for missing input for each direct mappings or data type parameters.)
- An implementation needs the functionality to load three kind of resources into its runtime:
    - Model Mappings
    - Context Mappings
    - openEHR Templates

--------------------------------------------------------

## Limitations

- Cardinalities of path objects should be covered by structural and semantic context (e.g. Condition).
  In cases of doubt the first element will be used.

- Final attributes of data types (say, unit of Quantity) will be handled without any transformation in the primitive type (continuing with unit: String).
  The input therefore has to provide the correct representation for the target.
  For instance, a unit `mmHG` might be provided, while the target system (either FHIR or openEHR) might only work with `mm[Hg]`.
  In those cases the input has to reflect the expected output already, for now.

## Future Work

- More data types: Depending on usage and community requirements, more data types should be supported and internal mappings added.
- Profiles and Implementation Guides: FHIR specialization artifacts will need to integration to some degree.
  They carry more modeling- and meta-data. They should allow more fine-grained mappings and more simplifications.
  The current spec covers vanilla Resources and thus specifies mapping for the use-cases with *less* additional data.
- Units: Units needs alignment, possibly as per UCUM.
- Terminology: Currently, the terminology/coding system information is only aiding the authoring of mappings.
- Community library: Kick-off community library of global and reusable mappings.

--------------------------------------------------------

# Appendix

## Tooling

To utilize JSON schema validation and autocompletion within YAML files check out tooling like:

- https://github.com/redhat-developer/vscode-yaml
- https://www.jetbrains.com/help/idea/json.html#ws_json_schema_add_custom

## Changelog

### v0.2.0

#### Added

- DV_ORDINAL and DV_COUNT support

#### Changed

- Data types can be overlapping now
- Context variables need non-overlapping types
- Model Mapping schema: improved validation

### v0.1.0

#### Added

- Complete data type handling, based on `with.type`
- Simplification of condition FHIR paths

#### Changed

- Paths are now omitting data type attributes and instead point to parent node
- FHIRPath spec for the FHIR paths in the mappings

### Removed

- General purpose `followedBy`

### v0.0.3

#### Added

Model Mapping:

- `version` 1..1
- `fhirConfig.alwaysBundled` 0..1
- `mappings[{reference}]` 0..1
- With `reference` having `.resourceType` and `.mappings`
- `mappings[{slotArchetype}]` 0..1
- `with.type` 1..1