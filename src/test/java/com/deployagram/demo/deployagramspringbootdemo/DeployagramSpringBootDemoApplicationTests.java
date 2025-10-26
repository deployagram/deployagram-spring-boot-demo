package com.deployagram.demo.deployagramspringbootdemo;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ContextConfiguration;

import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.awaitility.Awaitility.await;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ContextConfiguration(initializers = DeployagramSpringBootDemoApplicationTests.Initializer.class)
class DeployagramSpringBootDemoApplicationTests {

    private static final String SUGGESTIONS_TOPIC = "SuppliedSuggestionsTopic";

    private static WireMockServer wireMockServer = new WireMockServer(options().dynamicPort());

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            wireMockServer.start();
            TestPropertyValues.of(
                    "external.api.base-url=http://localhost:" + wireMockServer.port()
            ).applyTo(context.getEnvironment());
        }
    }

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private TestcontainersConfiguration testcontainersConfiguration;

    @BeforeEach
    void setup() {
        WireMock.configureFor("localhost", wireMockServer.port());

        stubFor(post(urlEqualTo("/Emailer/email/test@deployagram.com"))
                .withHeader("Content-Type", equalTo("application/json"))
                .withHeader("Accept", equalTo("application/json"))
                .withRequestBody(equalToJson("""
                        {
                          "email": "test@deployagram.com",
                          "subject": "Don't forget to check this out!",
                          "body": "We think you've recently considered Refactoring (Fowler, Beck). The 1st edition is available in paperback!"
                        }
                        """))
                .willReturn(
                        aResponse()
                                .withHeader("Content-Type", "application/json")
                                .withStatus(204)
                ));
    }

    @Test
    void messageIsProcessed() {
        whenMessageArrivesOnKafka();

        thenEmailerIsCalled();
    }

    private void whenMessageArrivesOnKafka() {
        String message = """
                {
                  "email": "test@deployagram.com",
                  "suggestion": {
                    "name": "Refactoring",
                    "edition": "1st",
                    "format": "paperback",
                    "authors": [
                      "Fowler Martin",
                      "Beck Kent"
                    ]
                  }
                }
                """;

        kafkaTemplate.send(SUGGESTIONS_TOPIC, message);
        System.out.println("[DEBUG_LOG] Sent message to Kafka: " + message);
    }

    private void thenEmailerIsCalled() {
        await().atMost(5, TimeUnit.SECONDS)
               .untilAsserted(() -> {
                   verify(postRequestedFor(urlPathEqualTo("/Emailer/email"))
                           .withHeader("Content-Type", equalTo("application/json"))
                           .withHeader("Accept", equalTo("application/json"))
                           .withRequestBody(equalToJson("""
                                   {
                                     "email": "test@deployagram.com",
                                     "subject": "Don't forget to check this out!",
                                     "body": "We think you've recently considered Refactoring (Fowler, Beck). The 1st edition is available in paperback!"
                                   }
                                   """))
                   );
               });
    }

    @AfterAll
    static void teardown() {
        wireMockServer.stop();
    }
}
