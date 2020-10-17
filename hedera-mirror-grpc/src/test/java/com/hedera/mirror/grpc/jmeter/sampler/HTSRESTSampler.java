package com.hedera.mirror.grpc.jmeter.sampler;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@Log4j2
@RequiredArgsConstructor
public class HTSRESTSampler {
    private final String restBaseUrl;
    private final List<String> formattedTransactionIds;
    private final RestTemplate restTemplate = new RestTemplate();

    public void retrieveTokenTransactions() {
        //TODO Optimize, actually gather metrics, and error handling
        HttpHeaders headers = new HttpHeaders();
        headers.setAccept(Arrays.asList(MediaType.APPLICATION_JSON));
        HttpEntity<String> entity = new HttpEntity<>(headers);
        for (String transactionId : formattedTransactionIds) {
            String url = "http://" + restBaseUrl + "/api/v1/transactions/" + transactionId;
            log.info("Requesting transaction {}", url);
            ResponseEntity responseEntity = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
        }
    }
}
