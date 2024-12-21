package com.medblocks.openfhir.util;

import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.fc.schema.model.Condition;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhrHelper;
import org.apache.commons.lang3.StringUtils;
import org.hl7.fhir.r4.model.Coding;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.medblocks.openfhir.fc.FhirConnectConst.FHIR_ROOT_FC;

@Component
public class OpenFhirStringUtils {

    private final String TYPE_PATTERN = "\\[TYPE:[^]]+]";
    private final String ALL_INDEXES = ":(\\d+)";
    public static final String RESOLVE = "resolve()";
    public static final String WHERE = "where";
    public static final String RECURRING_SYNTAX = "[n]";
    public static final String RECURRING_SYNTAX_ESCAPED = "\\[n]";

    /**
     * Adds regex pattern to the simplified flat path so that we can match all entries in a flat json
     *
     * @param simplifiedFlat simplified path as given in the fhir connect model mapings
     * @return simplified path with regex pattern
     */
    public String addRegexPatternToSimplifiedFlatFormat(final String simplifiedFlat) {
        final String[] parts = simplifiedFlat.split("/");
        final boolean lastOneHasPipe = parts[parts.length - 1].contains("|");
        if (lastOneHasPipe) {
            final String[] partsWithoutLast = Arrays.copyOf(parts, parts.length - 1);
            final String[] lastPart = parts[parts.length - 1].split("\\|");
            return String.join("(:\\d+)?/", partsWithoutLast) + "(:\\d+)?/" + lastPart[0] + "(:\\d+)?\\|" + lastPart[1];
        } else {
            return String.join("(:\\d+)?/", parts) + "(:\\d+)?(\\|.*)?";
        }
    }

    /**
     * When writing simplified openEHR flat path in FHIR Connect mappings, you may need to escape dots if they are a part
     * of the archetype name. Before we adjust openEHR paths to the actual ones based on RM, this needs to be removed
     * which is what this method does.
     */
    public void fixEscapedDotsInOpenEhrPaths(final List<FhirToOpenEhrHelper> flattened) {
        for (FhirToOpenEhrHelper fhirToOpenEhrHelper : flattened) {
            fhirToOpenEhrHelper.setOpenEhrPath(fhirToOpenEhrHelper.getOpenEhrPath().replace("\\.", "."));
            if (fhirToOpenEhrHelper.getFhirToOpenEhrHelpers() != null) {
                fixEscapedDotsInOpenEhrPaths(fhirToOpenEhrHelper.getFhirToOpenEhrHelpers());
            }
        }
    }

    public String endsWithOpenEhrType(final String path) {
        final Set<String> openEhrTypes = new HashSet<>();
        openEhrTypes.add("magnitude");
        openEhrTypes.add("unit");
        openEhrTypes.add("ordinal");
        openEhrTypes.add("value");
        openEhrTypes.add("code");
        openEhrTypes.add("terminology");
        for (String openEhrType : openEhrTypes) {
            if (path.endsWith(openEhrType)) {
                return openEhrType;
            }
        }
        return null;
    }

    public String replaceLastIndexOf(final String string, final String charToReplace, final String replaceWith) {
        int start = string.lastIndexOf(charToReplace);
        return string.substring(0, start) +
                replaceWith +
                string.substring(start + charToReplace.length());
    }

    /**
     * replaces dots in a simplified openEHR path with / and replaces FHIR Connect reference to openEHR archetype with
     * the actual one
     *
     * @param openEhr            simplified openEHR path
     * @param openEhrArchetypeId archetype ID of the mapping archetype
     * @return prepared openehr path
     */
    public String prepareOpenEhrSyntax(final String openEhr, final String openEhrArchetypeId) {
        if (openEhr == null) {
            return null;
        }
        return openEhr
                .replaceAll("(?<!\\\\)\\.", "/") // This is the negative lookbehind. It ensures that the dot (.) is not preceded by two backslashes (\\). The backslashes are escaped, so \\\\ means "two literal backslashes."
                .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC, openEhrArchetypeId);
    }

    /**
     * Returnes last index from the openEHR path, i.e. when passing in a:1/b:1/c/d:3, the '3' will be returned
     *
     * @param path path where we're extracting the index from
     * @return index as Integer extracted from the given openEHR path
     */
    public Integer getLastIndex(final String path) {
        String RIGHT_MOST_INDEX = ":(\\d+)(?!.*:)";
        final String match = getByRegex(path, RIGHT_MOST_INDEX);
        if (match == null) {
            return -1;
        }
        return Integer.valueOf(match);
    }

    public String getCastType(final String path) {
        String CAST_TYPE = "as\\(([^()]*)\\)";
        return getByRegex(path, CAST_TYPE);
    }

    private String getByRegex(final String path,
                              final String regex) {
        final List<String> byRegexAll = getByRegexAll(path, regex);
        if (byRegexAll == null) {
            return null;
        }
        return byRegexAll.get(0);
    }

    private List<String> getByRegexAll(final String path,
                                       final String regex) {
        final Pattern compiledPattern = Pattern.compile(regex);
        final Matcher matcher = compiledPattern.matcher(path);

        final List<String> matches = new ArrayList<>();

        while (matcher.find()) {
            matches.add(matcher.group(1));
        }
        if (matches.isEmpty()) {
            return null;
        }
        return matches;
    }

    /**
     * Returnes first index from the openEHR path, i.e. when passing in a:1/b:2/c/d:3, the '1' will be returned
     *
     * @param path path where we're extracting the index from
     * @return index as Integer extracted from the given openEHR path
     */
    public Integer getFirstIndex(final String path) {
        final String byRegex = getByRegex(path, ALL_INDEXES);
        if (byRegex == null) {
            return null;
        }
        return Integer.valueOf(byRegex);
    }

    public List<Integer> getAllIndexes(final String path) {
        final List<String> matches = getByRegexAll(path, ALL_INDEXES);
        if (matches == null) {
            return Collections.emptyList();
        }
        return matches.stream().map(Integer::parseInt).collect(Collectors.toList());
    }

    public String prepareParentOpenEhrPath(String fullOpenEhrPath,
                                           final String parentOpenEhrPath) {
        // person/personendaten:2/person/geburtsname:1/vollständiger_name
        // person.personendaten.person.geburtsname
        // Step 1: Split the strings by their delimiters
        fullOpenEhrPath = fullOpenEhrPath.replace("/", ".");

        String[] withIndexesParts = parentOpenEhrPath.split("/");
        String[] withoutIndexesParts = fullOpenEhrPath.split("\\.");

        // Step 2: Iterate over the parts and add indexes
        StringBuilder result = new StringBuilder();
        int j = 0;

        for (int i = 0; i < withoutIndexesParts.length; i++) {
            String part = withoutIndexesParts[i];
            if (part.endsWith(RECURRING_SYNTAX)) {
                part = part.replace(RECURRING_SYNTAX, "");
            }
            if (j < withIndexesParts.length && withIndexesParts[j].startsWith(part)) {
                result.append(withIndexesParts[j]);
                j++;
            } else {
                result.append(part);
            }

            if (i < withoutIndexesParts.length - 1) {
                result.append("/");
            }
        }
        return result.toString();
    }

    public String replaceCommonPaths(final String parent, final String toReplace) {
        final List<String> splitParent = List.of(parent.split("\\."));
        final List<String> childSplit = List.of(toReplace.split("\\."));

        final StringJoiner sj = new StringJoiner(".");
        for (int i = 0; i < childSplit.size(); i++) {
            if (i >= splitParent.size()) {
                sj.add(childSplit.get(i));
                continue;
            }
            String parentPath = splitParent.get(i);
            if (!parentPath.equals(childSplit.get(i))) {
                sj.add(childSplit.get(i));
            }
        }
        return sj.toString();
    }

    public String fixOpenEhrPath(final String openEhrPath,
                                 final String mainOpenEhrPath) {
        return openEhrPath
                .replace(FhirConnectConst.REFERENCE + "/", "")
                .replace(FhirConnectConst.OPENEHR_ARCHETYPE_FC + "/", mainOpenEhrPath + "/");
    }

    public String fixFhirPath(final String fhirPath) {
        return fhirPath.replace("." + FHIR_ROOT_FC, "");
    }

    /**
     * fixes fhirPath casting, as BooleanType is not a valid FHIR path, but boolean is.. similar to StringType > String, ..
     *
     * @param originalFhirPath path as it exists up until now
     * @return fhir path with casting
     */
    public String fixFhirPathCasting(final String originalFhirPath) {
        final String replacedCasting = replaceCasting(originalFhirPath);
        // now check if resolve() was preceeded with a case to a specific Resource; if that has happened, it needs to be
        // removed because it's not handled properly by fhirPath evaluation engine
        final String[] splitPath = replacedCasting.split("\\.");
        final StringJoiner building = new StringJoiner(".");
        for (int i = 0; i < splitPath.length; i++) {
            final String firstPath = splitPath[i];
            if (splitPath.length - 1 == i) {
                building.add(firstPath);
                break;
            }
            final String secondPath = splitPath[i + 1];
            if (firstPath.startsWith("as(") && secondPath.equals(RESOLVE)) {
                i++;
                building.add(secondPath);
            } else {
                building.add(firstPath);
            }
        }
        return building.toString();
    }

    private String replaceCasting(final String originalFhirPath) {
        return originalFhirPath.replace("as(BooleanType)", "as(Boolean)")
                .replace("as(DateTimeType)", "as(DateTime)")
                .replace("as(TimeType)", "as(Time)")
                .replace("as(StringType)", "as(String)");
    }

    /**
     * FHIR path amended in a way that condition becomes a part of it
     *
     * @param originalFhirPath original fhir path without conditions as it exists within a model mapper
     * @param conditions       conditions defined within a model mapper
     * @param resource         fhir resource being used as a base
     * @return fhir path with condition elemenets included in the fhir path itself
     * deprecated: use getFhirPathWithConditions instead! this method should be removed as soon as possible to clear up
     * the code base and remove redundant ones
     */
    public String amendFhirPath(final String originalFhirPath, final List<Condition> conditions, final String resource) {
        String fhirPath = originalFhirPath.replace(FhirConnectConst.FHIR_RESOURCE_FC, resource);
        if (fhirPath.contains(FhirConnectConst.FHIR_ROOT_FC)) {
            fhirPath = fhirPath.replace("." + FhirConnectConst.FHIR_ROOT_FC, "")
                    .replace(FhirConnectConst.FHIR_ROOT_FC, "");
        }
        if (conditions == null || conditions.isEmpty() || conditions.stream().allMatch(Objects::isNull)) {
            return fhirPath;
        }
        final StringJoiner stringJoiner = new StringJoiner(" and ");
        for (Condition condition : conditions) {
            if (condition == null) {
                continue;
            }
            final String targetAttribute = condition.getTargetAttribute();

            if (condition.getTargetRoot().startsWith(FhirConnectConst.FHIR_RESOURCE_FC)) {
                condition.setTargetRoot(condition.getTargetRoot().replace(FhirConnectConst.FHIR_RESOURCE_FC, resource));
            }
            // add condition in there within the fhirpath itself
            final String base;
            if (condition.getTargetRoot().startsWith(fhirPath)) {
                base = condition.getTargetRoot();
            } else {
                base = fhirPath;
            }
            stringJoiner.add(base
                    .replace(condition.getTargetRoot(), condition.getTargetRoot() + ".where(" + targetAttribute + ".toString().contains('" + getStringFromCriteria(condition.getCriteria()).getCode() + "'))").replace(FhirConnectConst.FHIR_RESOURCE_FC, resource));

        }
        return stringJoiner.toString();
    }

    public String getFhirPathWithoutConditions(final String originalFhirPath, final Condition condition, final String resource) {
        if (condition == null || condition.getTargetAttribute() == null) {
            return originalFhirPath.replace(FhirConnectConst.FHIR_RESOURCE_FC, resource);
        }
        final String targetAttribute = condition.getTargetAttribute();
        return originalFhirPath.replace(FhirConnectConst.FHIR_RESOURCE_FC, resource) + "." + targetAttribute;
    }

    public String extractWhereCondition(final String path) {
        return extractWhereCondition(path, false);

    }

    public String extractWhereCondition(final String path, final boolean last) {
        String start = "where(";  // We start after 'where('
        int startIndex = last ? path.lastIndexOf(start) : path.indexOf(start);

        if (startIndex == -1) {
            return null; // No match found
        }

        int openParenthesisCount = 1;  // Start counting after 'where('
        int endIndex = startIndex + start.length();  // Start looking from the character after 'where('

        // Traverse the string and count parentheses
        while (endIndex < path.length()) {
            char currentChar = path.charAt(endIndex);

            if (currentChar == '(') {
                openParenthesisCount++;
            } else if (currentChar == ')') {
                openParenthesisCount--;

                // If the count reaches 0, we've found the matching closing parenthesis
                if (openParenthesisCount == 0) {
                    break;
                }
            }
            endIndex++;
        }

        if (openParenthesisCount != 0) {
            return null; // Parentheses weren't balanced
        }

        // Return the matched string
        return path.substring(startIndex, endIndex + 1); // Include the closing parenthesis

    }

    public String constructFhirPathNoConditions(final String originalFhirPath,
                                                final String parentPath) {
        // only make sure parent's where path is added to the child
        if (StringUtils.isEmpty(parentPath)) {
            return originalFhirPath;
        }
        final String parentsWhereCondition = extractWhereCondition(parentPath);
        if (StringUtils.isEmpty(parentsWhereCondition)) {
            return originalFhirPath;
        } else {
            // find the correct place within children's path to add parent's where
            if (originalFhirPath.contains(parentPath)) {
                // all is done already
                return originalFhirPath;
            } else {
                if (originalFhirPath.startsWith(parentPath.replace(parentsWhereCondition, ""))) {
                    return setParentsWherePathToTheCorrectPlace(originalFhirPath, parentPath);
                } else {
                    final String remainingItemsFromParent = originalFhirPath.replace(setParentsWherePathToTheCorrectPlace(parentPath, originalFhirPath), "");
                    return setParentsWherePathToTheCorrectPlace(originalFhirPath, parentPath) + remainingItemsFromParent;
                }
            }
        }
    }

    public String constructFhirPathWithConditions(final String originalFhirPath,
                                                  final String parentPath,
                                                  final Condition condition,
                                                  final String resource) {
        // append parent's where path first
        String withParentsWhereInPlace;
        final String remainingItems;
        final String actualConditionTargetRoot = condition.getTargetRoot().replace(FhirConnectConst.FHIR_RESOURCE_FC, resource);
        if (originalFhirPath.startsWith(actualConditionTargetRoot)) {
            // then we use target root as the base path
            withParentsWhereInPlace = setParentsWherePathToTheCorrectPlace(actualConditionTargetRoot, parentPath);
            final String addedWhere = parentPath == null ? "" : extractWhereCondition(parentPath, true);
            final String remainingFromCondition = actualConditionTargetRoot.replace(withParentsWhereInPlace.replace("." + addedWhere, ""), "");
            if (!withParentsWhereInPlace.equals(remainingFromCondition)) {
                withParentsWhereInPlace += remainingFromCondition;
            }
            remainingItems = originalFhirPath.replace(actualConditionTargetRoot, "");
        } else {
            withParentsWhereInPlace = setParentsWherePathToTheCorrectPlace(originalFhirPath, parentPath);
            remainingItems = "";
        }

        if (actualConditionTargetRoot.startsWith(resource) && withParentsWhereInPlace.equals(originalFhirPath)) {
            // find the right place first
            final String commonPath = setParentsWherePathToTheCorrectPlace(originalFhirPath, actualConditionTargetRoot); // path right before the condition should start
            final String remainingToEndUpInWhere = actualConditionTargetRoot
                    .replace(commonPath + ".", "")
                    .replace(commonPath, "");
            final String remainingToAdd = StringUtils.isBlank(remainingToEndUpInWhere) ? "" : (remainingToEndUpInWhere + ".");
            final String whereClause = ".where(" + remainingToAdd + condition.getTargetAttribute() + ".toString().contains('" + getStringFromCriteria(condition.getCriteria()).getCode() + "'))";
            final String remainingItemsFromParent = originalFhirPath.replace(commonPath, "");
            return commonPath + whereClause + remainingItemsFromParent;
        } else {
            // then do your own where path
            final String whereClause = ".where(" + condition.getTargetAttribute() + ".toString().contains('" + getStringFromCriteria(condition.getCriteria()).getCode() + "'))";
            // then suffix with whatever is left from the children's path
            return withParentsWhereInPlace + whereClause + (StringUtils.isBlank(remainingItems) ? "" : (remainingItems.startsWith(".") ? remainingItems : ("." + remainingItems)));
        }
    }

    /**
     * Return originalFhirPath amended with the actual condition .where elements. This method will construct a fhir
     * path from Condition and add that to the original fhir path
     *
     * @param originalFhirPath original fhir path that will be amended with conditions
     * @param condition        condition we'll use when constructing a .where clause
     * @param resource         resource type
     * @param parentPath       parent fhir path, if one exists
     * @return fhir path amended with the .where clause as constructed from the given Condition
     */
    public String getFhirPathWithConditions(String originalFhirPath,
                                            final Condition condition,
                                            final String resource,
                                            final String parentPath) {
        originalFhirPath = originalFhirPath.replace(FhirConnectConst.FHIR_RESOURCE_FC, resource);
        if (condition == null || condition.getTargetAttribute() == null) {
            // only make sure parent's where path is added to the child
            return constructFhirPathNoConditions(originalFhirPath, parentPath);
        } else {
            return constructFhirPathWithConditions(originalFhirPath, parentPath, condition, resource);
        }
    }

    public String setParentsWherePathToTheCorrectPlace(final String child,
                                                       final String parent) {
        if (StringUtils.isEmpty(parent)) {
            return child;
        }
        StringJoiner childPathJoiner = new StringJoiner(".");
        final String[] parents = parent.split("\\.");
        final String[] children = child.split("\\.");

        int parentSubstringCount = 0;
        int parentIndex = 0;
        for (int i = 0; i < children.length; i++) {
            final String childPath = children[i];
            if (parentIndex >= parents.length || childPath.equals(parents[parentIndex])) {
                childPathJoiner.add(childPath);
                parentIndex++;
                if (parentIndex < parents.length) {
                    parentSubstringCount += parents[parentIndex].length();
                }
            } else {
                final String string = parents[parentIndex];
                if (string.startsWith(WHERE)) {
                    // a where follows
                    final String firstWhereCondition = extractWhereCondition(parent.substring(parentSubstringCount - 1));
                    childPathJoiner.add(firstWhereCondition);
                    childPathJoiner.add(childPath);
                    parentIndex += (int) (firstWhereCondition.chars().filter(ch -> ch == '.').count() + 1);
                }
            }
        }

        return childPathJoiner.toString();
    }


    /**
     * [$snomed.1104341000000101]"
     */
    public Coding getStringFromCriteria(final String criteria) {
        if (criteria == null) {
            return null;
        }
        final String[] criterias = criteria.replace("[", "") // todo: crazy stuff in the FHIR Connect spec...... criteria is a string array, $loinc, ...
                .replace("]", "").split(",");
        // todo: should be an OR inbetween these separate criterias.. right now it just takes the first
        final String codingCode = criterias[0]
                .replace("$loinc.", "")
                .replace("$snomed.", "");
        final String system;
        if (criteria.contains("$loinc")) {
            system = "http://loinc.org";
        } else if (criteria.contains("$snomed")) {
            system = "http://snomed.info/sct";
        } else {
            system = criteria.replace("[", "") // // todo: crazy stuff in the FHIR Connect spec...... criteria is a string array, $loinc, ...
                    .replace("]", "").split("\\.")[0];
        }
        return new Coding(system, codingCode, null);
    }

    /**
     * removes TYPE from openEhr path,
     * i.e. medication_order/medication_order[TYPE:INSTRUCTION]/order[n][TYPE:ACTIVITY]/medication_item[TYPE:ELEMENT]
     * should become
     * medication_order/medication_order/order[n]/medication_item
     */
    public String removeTypes(final String openEhrPath) {
        if (StringUtils.isEmpty(openEhrPath)) {
            return openEhrPath;
        }
        return openEhrPath.replaceAll(TYPE_PATTERN, "");
    }

    public String getLastType(final String openEhrPath) {
        final Pattern compiledPattern = Pattern.compile(TYPE_PATTERN);
        final Matcher matcher = compiledPattern.matcher(openEhrPath);

        final List<String> matches = new ArrayList<>();

        while (matcher.find()) {
            matches.add(matcher.group());
        }
        if (matches.isEmpty()) {
            return null;
        }
        return matches.get(matches.size() - 1).replace("[TYPE:", "").replace("]", "");
    }

    public Set<String> getPossibleRmTypeValue(final String val) {
        if (val == null) {
            return null;
        }
        return switch (val) {
            case "QUANTITY" ->
                    new HashSet<>(Arrays.asList(FhirConnectConst.DV_QUANTITY, FhirConnectConst.DV_COUNT, FhirConnectConst.DV_ORDINAL, FhirConnectConst.DV_PROPORTION));
            case "DATETIME" -> Collections.singleton(FhirConnectConst.DV_DATE_TIME);
            case "TIME" -> Collections.singleton(FhirConnectConst.DV_TIME);
            case "DATE" -> Collections.singleton(FhirConnectConst.DV_DATE);
            case "CODEABLECONCEPT" -> Collections.singleton(FhirConnectConst.DV_CODED_TEXT);
            case "CODING" -> Collections.singleton(FhirConnectConst.CODE_PHRASE);
            case "STRING" -> Collections.singleton(FhirConnectConst.DV_TEXT);
            case "BOOL" -> Collections.singleton(FhirConnectConst.DV_BOOL);
            case "IDENTIFIER" -> Collections.singleton(FhirConnectConst.IDENTIFIER);
            case "MEDIA" -> Collections.singleton(FhirConnectConst.DV_MULTIMEDIA);
            case "PROPORTION" -> Collections.singleton(FhirConnectConst.DV_PROPORTION);
            default -> Collections.singleton(val);
        };
    }

    /**
     * Replaces parts of the original string with parts from the replacement string, based on specific patterns.
     * <p>
     * The original and replacement strings are split by "/" and processed part by part. The following rules are applied:
     * - If a replacement part contains a numeric suffix in the format "part:number", the corresponding part from the replacement is used.
     * - If a replacement part contains a suffix in the format "part[n]", the original structure of the part is retained.
     * - If no special pattern is found, the replacement part is used, unless it's significantly different from the original part, in which case the original part is kept.
     * - The method returns the new string with appropriate replacements and maintains the "/" as the separator.
     *
     * @param original    the original string to be processed (parts separated by "/")
     * @param replacement the replacement string to be used (parts separated by "/")
     * @return a new string where parts from the original are replaced with parts from the replacement
     */
    public String replacePattern(String original, String replacement) {
        // Split the original and replacement strings into parts based on "/"
        String[] originalParts = original.split("/");
        String[] replacementParts = replacement.split("/");

        StringBuilder result = new StringBuilder();

        // Iterate through the parts and replace the parts from the original with the replacement, when needed
        for (int i = 0; i < originalParts.length; i++) {
            if (i < replacementParts.length && replacementParts[i].matches(".*:\\d+")) {
                // If replacement part has a numeric suffix, use it
                result.append(replacementParts[i]);
            } else if (i < replacementParts.length && replacementParts[i].matches(".*\\[\\d*]")) {
                // If the replacement part has a [n] suffix, use the original structure
                result.append(originalParts[i]);
            } else if (i < replacementParts.length) {
                // Use the original part
                final String orig = originalParts[i].contains(RECURRING_SYNTAX) ? replaceLastIndexOf(originalParts[i], RECURRING_SYNTAX, "") : originalParts[i];
                final String repl = replacementParts[i].contains(":") ? replacementParts[i].replace(":", "").replace(String.valueOf(getLastIndex(replacementParts[i])), "") : replacementParts[i];
                if (!orig.startsWith(repl)) { // means it's a completely different one, need to take original
                    result.append(originalParts[i]);
                } else {
                    result.append(replacementParts[i]);
                }
            } else {
                // If no matching replacement, use the original part
                result.append(originalParts[i]);
            }

            // Add the separator
            if (i < originalParts.length - 1) {
                result.append("/");
            }
        }
        return result.toString();
    }

    /**
     * Return true if child path starts with a parent path, i.e. if a parent openEHR flat path is direct
     * variation of a parent openEHR path
     */
    public boolean childStartsWithParent(final String child, final String parent) {
        final List<String> childSplit = Arrays.asList(child.split("/"));
        final List<String> parentSplit = Arrays.asList(parent.split("/"));

        for (int i = 0; i < childSplit.size(); i++) {
            String childPath = childSplit.get(i);
            if (i >= parentSplit.size()) {
                return true;
            }
            if (childPath.contains("|")) {
                childPath = childPath.split("\\|")[0];
            }
            if (!childPath.equals(parentSplit.get(i))) {
                return false;
            }
        }
        return true;
    }
}
