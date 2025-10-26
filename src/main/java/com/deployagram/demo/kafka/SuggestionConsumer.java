package com.deployagram.demo.kafka;

import com.deployagram.demo.model.EmailRequest;
import com.deployagram.demo.service.EmailerService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class SuggestionConsumer {

    private final EmailerService emailerService;
    private final ObjectMapper objectMapper;

    public SuggestionConsumer(EmailerService emailerService, ObjectMapper objectMapper) {
        this.emailerService = emailerService;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "SuppliedSuggestionsTopic")
    public void consume(String message) {
        System.out.println("[DEBUG_LOG] Received message from Kafka: " + message);
        try {
            SuggestionMessage suggestionMessage = objectMapper.readValue(message, SuggestionMessage.class);
            System.out.println("[DEBUG_LOG] Deserialized message: " + suggestionMessage.getEmail());
            EmailRequest emailRequest = createEmailRequest(suggestionMessage);
            System.out.println("[DEBUG_LOG] Created email request: " + emailRequest.getSubject() + " - " + emailRequest.getBody());
            emailerService.sendEmail(emailRequest);
        } catch (JsonProcessingException e) {
            System.out.println("[DEBUG_LOG] Error processing message: " + e.getMessage());
            throw new RuntimeException("Error processing message", e);
        }
    }

    private EmailRequest createEmailRequest(SuggestionMessage suggestionMessage) {
        String email = suggestionMessage.getEmail();
        String subject = "Don't forget to check this out!";

        SuggestionMessage.Suggestion suggestion = suggestionMessage.getSuggestion();
        String authors = formatAuthorSurnames(suggestion.getAuthors());

        String body = String.format("We think you've recently considered %s (%s). The %s edition is available in %s!",
                suggestion.getName(),
                authors,
                suggestion.getEdition(),
                suggestion.getFormat());

        return new EmailRequest(email, subject, body);
    }

    public String formatAuthorSurnames(List<String> authors) {
        return authors.stream()
                      .map(author -> {
                          String[] parts = author.trim().split("\\s+");
                          return parts.length > 0 ? parts[0] : "";
                      })
                      .collect(Collectors.joining(", "));
    }
}
