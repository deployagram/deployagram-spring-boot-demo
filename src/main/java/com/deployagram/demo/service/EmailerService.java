package com.deployagram.demo.service;

import com.deployagram.demo.model.EmailRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service
public class EmailerService {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public EmailerService(RestTemplate restTemplate, @Value("${external.api.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    public void sendEmail(EmailRequest emailRequest) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(List.of(MediaType.APPLICATION_JSON));
        HttpEntity<EmailRequest> request = new HttpEntity<>(emailRequest, headers);

        String url = baseUrl + "/Emailer/email";

        try {
            System.out.println("[DEBUG_LOG] Sending email to URL: " + url);
            System.out.println("[DEBUG_LOG] Email request body: " + emailRequest.getSubject() + " - " + emailRequest.getBody());
            restTemplate.postForEntity(url, request, Void.class);
        } catch (Exception e) {
            System.out.println("[DEBUG_LOG] Error sending email: " + e.getMessage());
            throw new RuntimeException("Error sending email to " + emailRequest.getEmail(), e);
        }
    }
}
