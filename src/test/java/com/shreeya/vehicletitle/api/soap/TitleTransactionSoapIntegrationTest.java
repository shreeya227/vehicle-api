package com.shreeya.vehicletitle.api.soap;

import com.shreeya.vehicletitle.config.SoapWebServiceConfig;
import com.shreeya.vehicletitle.infrastructure.persistence.OutboxEventRepository;
import com.shreeya.vehicletitle.infrastructure.persistence.TitleTransactionRepository;
import com.shreeya.vehicletitle.infrastructure.persistence.TransactionAuditRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.ws.test.server.MockWebServiceClient;
import org.springframework.xml.transform.StringSource;

import java.util.Map;

import static org.springframework.ws.test.server.RequestCreators.withPayload;
import static org.springframework.ws.test.server.ResponseMatchers.clientOrSenderFault;
import static org.springframework.ws.test.server.ResponseMatchers.noFault;
import static org.springframework.ws.test.server.ResponseMatchers.xpath;

@SpringBootTest
@ActiveProfiles("test")
class TitleTransactionSoapIntegrationTest {

    private static final Map<String, String> NAMESPACES =
            Map.of("tns", SoapWebServiceConfig.NAMESPACE);

    @Autowired
    private ApplicationContext applicationContext;
    @Autowired
    private TransactionAuditRepository auditRepository;
    @Autowired
    private OutboxEventRepository outboxRepository;
    @Autowired
    private TitleTransactionRepository transactionRepository;

    private MockWebServiceClient client;

    @BeforeEach
    void setUp() {
        client = MockWebServiceClient.createClient(applicationContext);
        auditRepository.deleteAll();
        outboxRepository.deleteAll();
        transactionRepository.deleteAll();
    }

    @Test
    void submitsAContractFirstSoapTransactionAndSupportsSafeReplay() throws Exception {
        StringSource payload = new StringSource(validSoapRequest("soap-request-001"));

        client.sendRequest(withPayload(payload))
                .andExpect(noFault())
                .andExpect(xpath("//tns:idempotentReplay", NAMESPACES).evaluatesTo("false"))
                .andExpect(xpath("//tns:transaction/tns:status", NAMESPACES)
                        .evaluatesTo("RECEIVED"));

        client.sendRequest(withPayload(new StringSource(validSoapRequest("soap-request-001"))))
                .andExpect(noFault())
                .andExpect(xpath("//tns:idempotentReplay", NAMESPACES).evaluatesTo("true"));
    }

    @Test
    void mapsInvalidSoapSubmissionsToClientFaults() throws Exception {
        client.sendRequest(withPayload(new StringSource(validSoapRequest("bad"))))
                .andExpect(clientOrSenderFault());
    }

    private String validSoapRequest(String idempotencyKey) {
        return """
                <tns:SubmitTitleTransactionRequest
                    xmlns:tns="https://github.com/shreeya227/vehicle-title-transaction-api/ws/title/v1">
                  <tns:idempotencyKey>%s</tns:idempotencyKey>
                  <tns:vin>1HGCM82633A004352</tns:vin>
                  <tns:jurisdiction>MA</tns:jurisdiction>
                  <tns:transactionType>TITLE_TRANSFER</tns:transactionType>
                  <tns:owner>
                    <tns:fullName>Alex Morgan</tns:fullName>
                    <tns:email>alex.morgan@example.com</tns:email>
                    <tns:addressLine1>1 Main Street</tns:addressLine1>
                    <tns:city>Boston</tns:city>
                    <tns:state>MA</tns:state>
                    <tns:postalCode>02108</tns:postalCode>
                  </tns:owner>
                  <tns:purchasePrice>18450.00</tns:purchasePrice>
                </tns:SubmitTitleTransactionRequest>
                """.formatted(idempotencyKey);
    }
}
