package com.medblocks.openfhir;

import com.medblocks.openfhir.fc.FhirConnectConst;
import com.medblocks.openfhir.toopenehr.FhirToOpenEhrHelper;
import com.medblocks.openfhir.util.OpenFhirStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplate;
import org.ehrbase.openehr.sdk.webtemplate.model.WebTemplateNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

import static com.medblocks.openfhir.fc.FhirConnectConst.OPENEHR_TYPE_NONE;

@Component
public class OpenEhrRmWorker {

    private OpenFhirStringUtils openFhirStringUtils;

    @Autowired
    public OpenEhrRmWorker(OpenFhirStringUtils openFhirStringUtils) {
        this.openFhirStringUtils = openFhirStringUtils;
    }

    public void fixFlatWithOccurrences(final List<FhirToOpenEhrHelper> flattened, final WebTemplate webTemplate) {
        for (final FhirToOpenEhrHelper fhirToOpenEhrHelper : flattened) {
            final String openEhrKey = fhirToOpenEhrHelper.getOpenEhrPath();

            final Set<String> forcedTypes = openFhirStringUtils.getPossibleRmTypeValue(fhirToOpenEhrHelper.getOpenEhrType());

            final boolean hasSuffix = openEhrKey.contains("|");
            final String suffix = hasSuffix ? openEhrKey.substring(openEhrKey.indexOf("|")) : null;
            final String flat = hasSuffix ? openEhrKey.substring(0, openEhrKey.indexOf("|")) : openEhrKey;
            final String[] split = flat.substring(flat.indexOf("/") + 1).split("/"); // we want to remove the first path, as it's the template itself
            final WebTemplateNode tree = webTemplate.getTree();

            final StringJoiner constructing = new StringJoiner("/");
            walkThroughNodes(tree.getChildren(), String.join("/", split), constructing, forcedTypes, fhirToOpenEhrHelper);


            fhirToOpenEhrHelper.setOpenEhrPath(tree.getId() + "/" + fhirToOpenEhrHelper.getOpenEhrPath() + (hasSuffix ? suffix : ""));

            // we compare so that we can see if if was found within the template; if not, we don't want for it to end up in the flat json
            final String initialOpenEhrPathWithProperTreeId = tree.getId() + "/" + flat.substring(flat.indexOf("/") + 1);
            if(fhirToOpenEhrHelper.getOpenEhrPath().length() < initialOpenEhrPathWithProperTreeId.length()) {
                // means it didn't find it fully.. so it probably doesn't exist
                if(!FhirConnectConst.DV_MULTIMEDIA.equals(fhirToOpenEhrHelper.getOpenEhrType())) { // multimedia and its 'content' is a tad bit special...
                    fhirToOpenEhrHelper.setOpenEhrType(OPENEHR_TYPE_NONE);
                }
            }

            if (fhirToOpenEhrHelper.getFhirToOpenEhrHelpers() != null) {
                fixFlatWithOccurrences(fhirToOpenEhrHelper.getFhirToOpenEhrHelpers(), webTemplate);
            }
        }
    }

    public void walkThroughNodes(final List<WebTemplateNode> webTemplateNodes, final String path, final StringJoiner constructing, final Set<String> forcedTypes, final FhirToOpenEhrHelper fhirToOpenEhrHelper) {
        if (StringUtils.isBlank(path)) {
            // everything has been resolved
            fhirToOpenEhrHelper.setOpenEhrPath(constructing.toString());
            return;
        }
        final List<String> remainingPaths;
        final String[] splitOpenEhrPath = path.split("/");
        final String pathToFind = splitOpenEhrPath[0];
        if (pathToFind.startsWith("_")) {
            // we don't bother with this, can't be multiple occurrences
            constructing.add(pathToFind);
            remainingPaths = Arrays.asList(splitOpenEhrPath).subList(1, splitOpenEhrPath.length);
            walkThroughNodes(webTemplateNodes, remainingPaths.stream().collect(Collectors.joining("/")),
                    constructing, forcedTypes, fhirToOpenEhrHelper);
            return;
        }


        final WebTemplateNode findingTheOne = webTemplateNodes.stream()
                .filter(ch -> pathToFind.equals(ch.getId()))
                .findAny()
                .orElse(null);
        if (findingTheOne == null) {
            for (WebTemplateNode itemTree : webTemplateNodes) {
                walkThroughNodes(itemTree.getChildren(), path, constructing, forcedTypes, fhirToOpenEhrHelper);
            }
            fhirToOpenEhrHelper.setOpenEhrPath(constructing.toString());
            return;
        }
        remainingPaths = Arrays.asList(splitOpenEhrPath).subList(1, splitOpenEhrPath.length);
        if (findingTheOne.isMulti()) {
            // is multiple occurrences
            constructing.add(pathToFind + "[n]");
            fhirToOpenEhrHelper.setOpenEhrType(forcedTypes == null ? findingTheOne.getRmType() : getCorrectOpenEhrType(forcedTypes, findingTheOne));
        } else {
            constructing.add(pathToFind);
            fhirToOpenEhrHelper.setOpenEhrType(forcedTypes == null ? findingTheOne.getRmType() : getCorrectOpenEhrType(forcedTypes, findingTheOne));
        }

        walkThroughNodes(findingTheOne.getChildren(), remainingPaths.stream().collect(Collectors.joining("/")),
                constructing, forcedTypes, fhirToOpenEhrHelper);
    }

    private String getCorrectOpenEhrType(final Set<String> forcedTypes,
                                         final WebTemplateNode relevantTemplateNode) {
        if (forcedTypes.size() == 1) {
            return new ArrayList<>(forcedTypes).get(0);
        }
        if (relevantTemplateNode.getRmType().equals("ELEMENT")) { // need to look deeper
            return relevantTemplateNode.getChildren().stream()
                    .filter(el -> "value".equals(el.getId()))
                    .map(WebTemplateNode::getRmType)
                    .findAny()
                    .orElse(null);
        }
        final List<String> matchedByType = forcedTypes.stream()
                .filter(ft -> ft.equals(relevantTemplateNode.getRmType())).collect(Collectors.toList());
        return matchedByType.isEmpty() ? new ArrayList<>(forcedTypes).get(0) : matchedByType.get(0); // is this ok??
    }
}
