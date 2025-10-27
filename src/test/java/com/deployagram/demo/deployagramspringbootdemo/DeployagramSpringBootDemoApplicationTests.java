package com.deployagram.demo.deployagramspringbootdemo;

import com.github.deployagram.annotations.junit5.Deployagram;
import com.github.deployagram.annotations.junit5.DeployagramConfig;
import com.github.deployagram.annotations.junit5.DeployagramConfigEntry;
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
@Deployagram(startEnvironment = true, shareHostPorts = {DeployagramSpringBootDemoApplicationTests.WIREMOCK_PORT_NUMBER}, proxyPort = DeployagramSpringBootDemoApplicationTests.DEPLOYAGRAM_HTTP_PROXY_PORT)
@DeployagramConfig({
        @DeployagramConfigEntry(key = "proxy.namesOfSourceApps.Emailer", value = "DontForget"),
        @DeployagramConfigEntry(key = "proxy.proxiedAppNames.Emailer", value = "Emailer"),
        @DeployagramConfigEntry(key = "proxy.proxiedApps.Emailer", value = "http://host.testcontainers.internal:8085/Emailer"),
})
class DeployagramSpringBootDemoApplicationTests {

    private static final String SUGGESTIONS_TOPIC = "SuppliedSuggestionsTopic";
    static final int WIREMOCK_PORT_NUMBER = 8085;
    static final int DEPLOYAGRAM_HTTP_PROXY_PORT = 9090;

    private static WireMockServer wireMockServer = new WireMockServer(options().port(WIREMOCK_PORT_NUMBER));

    static class Initializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext context) {
            wireMockServer.start();
            TestPropertyValues.of(
                    "external.api.base-url=http://localhost:" + DEPLOYAGRAM_HTTP_PROXY_PORT
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

        stubFor(post(urlEqualTo("/Emailer/email"))
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
