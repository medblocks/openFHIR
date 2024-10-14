package com.medblocks.openfhir.kds.ehrbase;

import com.nedap.archie.rm.composition.Composition;
import lombok.extern.slf4j.Slf4j;
import org.ehrbase.openehr.sdk.serialisation.jsonencoding.CanonicalJson;
import org.springframework.http.*;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;

@Slf4j
public class EhrBaseTestClient {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String username;
    private final String password;

    public EhrBaseTestClient(final String baseUrl,
                             final String username,
                             final String password) {
        this.restTemplate = new RestTemplate();
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
    }

    public ResponseEntity<String> createComposition(final Composition composition,
                                                    final String serializedOperationalTemplate) {
        createOpt(serializedOperationalTemplate);

        final String url = baseUrl + "/ehrbase/rest/openehr/v1/ehr/" + createEhrId() + "/composition";

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        addAuthorizationHeader(headers);

        final HttpEntity<String> requestEntity = new HttpEntity<>(new CanonicalJson().marshal(composition), headers);

        try {
            return restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        } catch (HttpStatusCodeException e) {
            return ResponseEntity.status(e.getStatusCode()).body(e.getResponseBodyAsString());
        } catch (RestClientException e) {
            throw new RuntimeException(e);
        }
    }

    public void createOpt(final String opt) {
        final String url = baseUrl + "/ehrbase/rest/openehr/v1/definition/template/adl1.4";

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_XML);
        addAuthorizationHeader(headers);

        final HttpEntity<String> requestEntity = new HttpEntity<>(opt, headers);

        try {
            restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        } catch (HttpStatusCodeException e) {
            if (e.getStatusCode().value() == 409) {
                log.info("Received 409 from ehrBase, OPT probably already exists.");
            } else {
                throw new RuntimeException(e);
            }
        } catch (RestClientException e) {
            throw new RuntimeException(e);
        }
    }

    public String createEhrId() {
        final String url = baseUrl + "/ehrbase/rest/openehr/v1/ehr";

        final HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        addAuthorizationHeader(headers);

        final HttpEntity<String> requestEntity = new HttpEntity<>(headers);

        final ResponseEntity<String> exchange = restTemplate.exchange(url, HttpMethod.POST, requestEntity, String.class);
        return exchange.getHeaders().get("ETag").get(0).replace("\"", "");
    }

    private void addAuthorizationHeader(final HttpHeaders headers) {
        final byte[] encodede = Base64.getEncoder().encode(String.format("%s:%s", username, password).getBytes(StandardCharsets.UTF_8));
        headers.set("Authorization", "Basic " + new String(encodede));
    }
}
