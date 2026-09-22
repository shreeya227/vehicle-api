package com.shreeya.vehicletitle.api.rest;

import com.jayway.jsonpath.JsonPath;
import com.shreeya.vehicletitle.infrastructure.persistence.OutboxEventRepository;
import com.shreeya.vehicletitle.infrastructure.persistence.TitleTransactionRepository;
import com.shreeya.vehicletitle.infrastructure.persistence.TransactionAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TitleTransactionApiIntegrationTest {

    private static final String API_KEY = "test-api-key";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private TransactionAuditRepository auditRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private TitleTransactionRepository transactionRepository;

    @BeforeEach
    void clearDatabase() {
        auditRepository.deleteAll();
        outboxRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void createsAndReplaysAnIdempotentSubmission() throws Exception {
        var created = mockMvc.perform(post("/api/v1/title-transactions")
                        .header("X-API-Key", API_KEY)
                        .header("Idempotency-Key", "dealer-request-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("18450.00")))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.idempotentReplay").value(false))
                .andExpect(jsonPath("$.transaction.status").value("RECEIVED"))
                .andReturn();
        String transactionId = JsonPath.read(created.getResponse().getContentAsString(),
                "$.transaction.transactionId");

        mockMvc.perform(post("/api/v1/title-transactions")
                        .header("X-API-Key", API_KEY)
                        .header("Idempotency-Key", "dealer-request-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("18450.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotentReplay").value(true))
                .andExpect(jsonPath("$.transaction.transactionId").value(transactionId));

        mockMvc.perform(post("/api/v1/title-transactions")
                        .header("X-API-Key", API_KEY)
                        .header("Idempotency-Key", "dealer-request-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("19000.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Transaction conflict"));
    }

    @Test
    void enforcesLifecycleAndExposesAnAuditTrail() throws Exception {
        var created = mockMvc.perform(post("/api/v1/title-transactions")
                        .header("X-API-Key", API_KEY)
                        .header("Idempotency-Key", "dealer-request-002")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest("18450.00")))
                .andExpect(status().isCreated())
                .andReturn();
        String transactionId = JsonPath.read(created.getResponse().getContentAsString(),
                "$.transaction.transactionId");

        mockMvc.perform(patch("/api/v1/title-transactions/{id}/status", transactionId)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"VALIDATING\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATING"));

        mockMvc.perform(patch("/api/v1/title-transactions/{id}/status", transactionId)
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"SUBMITTED_TO_STATE\","
                                + "\"externalReference\":\"MA-RMV-2026-0091\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED_TO_STATE"));

        mockMvc.perform(get("/api/v1/title-transactions/{id}/audit-events", transactionId)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].eventType").value("TRANSACTION_RECEIVED"))
                .andExpect(jsonPath("$[2].toStatus").value("SUBMITTED_TO_STATE"));
    }

    @Test
    void rejectsRequestsWithoutAnApiKey() throws Exception {
        mockMvc.perform(get("/api/v1/title-transactions/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Unauthorized"));
    }

    private String validRequest(String purchasePrice) {
        return """
                {
                  "vin": "1HGCM82633A004352",
                  "jurisdiction": "MA",
                  "transactionType": "TITLE_TRANSFER",
                  "owner": {
                    "fullName": "Alex Morgan",
                    "email": "alex.morgan@example.com",
                    "addressLine1": "1 Main Street",
                    "city": "Boston",
                    "state": "MA",
                    "postalCode": "02108"
                  },
                  "purchasePrice": %s
                }
                """.formatted(purchasePrice);
    }
}
