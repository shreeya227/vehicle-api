package com.shreeya.vehicletitle.api.soap;

import com.shreeya.vehicletitle.application.OwnerCommand;
import com.shreeya.vehicletitle.application.SubmitTitleCommand;
import com.shreeya.vehicletitle.application.TitleTransactionService;
import com.shreeya.vehicletitle.config.SoapWebServiceConfig;
import com.shreeya.vehicletitle.domain.SubmissionChannel;
import com.shreeya.vehicletitle.domain.TitleTransaction;
import com.shreeya.vehicletitle.soap.schema.GetTitleTransactionRequest;
import com.shreeya.vehicletitle.soap.schema.GetTitleTransactionResponse;
import com.shreeya.vehicletitle.soap.schema.Owner;
import com.shreeya.vehicletitle.soap.schema.SubmitTitleTransactionRequest;
import com.shreeya.vehicletitle.soap.schema.SubmitTitleTransactionResponse;
import com.shreeya.vehicletitle.soap.schema.TitleTransactionRecord;
import org.springframework.ws.server.endpoint.annotation.Endpoint;
import org.springframework.ws.server.endpoint.annotation.PayloadRoot;
import org.springframework.ws.server.endpoint.annotation.RequestPayload;
import org.springframework.ws.server.endpoint.annotation.ResponsePayload;

import javax.xml.datatype.DatatypeConfigurationException;
import javax.xml.datatype.DatatypeFactory;
import javax.xml.datatype.XMLGregorianCalendar;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.GregorianCalendar;
import java.util.UUID;

@Endpoint
public class TitleTransactionSoapEndpoint {

    private final TitleTransactionService service;
    private final DatatypeFactory datatypeFactory;

    public TitleTransactionSoapEndpoint(TitleTransactionService service) {
        this.service = service;
        try {
            this.datatypeFactory = DatatypeFactory.newInstance();
        } catch (DatatypeConfigurationException exception) {
            throw new IllegalStateException("XML date support is unavailable", exception);
        }
    }

    @PayloadRoot(namespace = SoapWebServiceConfig.NAMESPACE,
            localPart = "SubmitTitleTransactionRequest")
    @ResponsePayload
    public SubmitTitleTransactionResponse submit(
            @RequestPayload SubmitTitleTransactionRequest request) {
        Owner submittedOwner = request.getOwner();
        OwnerCommand owner = new OwnerCommand(submittedOwner.getFullName(), submittedOwner.getEmail(),
                submittedOwner.getAddressLine1(), submittedOwner.getCity(), submittedOwner.getState(),
                submittedOwner.getPostalCode());
        SubmitTitleCommand command = new SubmitTitleCommand(request.getVin(), request.getJurisdiction(),
                com.shreeya.vehicletitle.domain.TransactionType.valueOf(
                        request.getTransactionType().value()), owner, request.getPurchasePrice());
        var result = service.submit(request.getIdempotencyKey(), command, SubmissionChannel.SOAP,
                "soap-integration-partner");

        SubmitTitleTransactionResponse response = new SubmitTitleTransactionResponse();
        response.setTransaction(toSoapRecord(result.transaction()));
        response.setIdempotentReplay(result.idempotentReplay());
        return response;
    }

    @PayloadRoot(namespace = SoapWebServiceConfig.NAMESPACE,
            localPart = "GetTitleTransactionRequest")
    @ResponsePayload
    public GetTitleTransactionResponse get(
            @RequestPayload GetTitleTransactionRequest request) {
        TitleTransaction transaction = service.get(UUID.fromString(request.getTransactionId()));
        GetTitleTransactionResponse response = new GetTitleTransactionResponse();
        response.setTransaction(toSoapRecord(transaction));
        return response;
    }

    private TitleTransactionRecord toSoapRecord(TitleTransaction transaction) {
        TitleTransactionRecord record = new TitleTransactionRecord();
        record.setTransactionId(transaction.getId().toString());
        record.setVin(transaction.getVin());
        record.setJurisdiction(transaction.getJurisdiction());
        record.setTransactionType(com.shreeya.vehicletitle.soap.schema.TransactionType
                .fromValue(transaction.getTransactionType().name()));

        Owner owner = new Owner();
        owner.setFullName(transaction.getOwner().getFullName());
        owner.setEmail(transaction.getOwner().getEmail());
        owner.setAddressLine1(transaction.getOwner().getAddressLine1());
        owner.setCity(transaction.getOwner().getCity());
        owner.setState(transaction.getOwner().getState());
        owner.setPostalCode(transaction.getOwner().getPostalCode());
        record.setOwner(owner);

        record.setPurchasePrice(transaction.getPurchasePrice());
        record.setStatus(com.shreeya.vehicletitle.soap.schema.TransactionStatus
                .fromValue(transaction.getStatus().name()));
        record.setExternalReference(transaction.getExternalReference());
        record.setRejectionReason(transaction.getRejectionReason());
        record.setCreatedAt(xmlDate(transaction.getCreatedAt().atZone(ZoneOffset.UTC)));
        record.setUpdatedAt(xmlDate(transaction.getUpdatedAt().atZone(ZoneOffset.UTC)));
        return record;
    }

    private XMLGregorianCalendar xmlDate(ZonedDateTime dateTime) {
        return datatypeFactory.newXMLGregorianCalendar(GregorianCalendar.from(dateTime));
    }
}
