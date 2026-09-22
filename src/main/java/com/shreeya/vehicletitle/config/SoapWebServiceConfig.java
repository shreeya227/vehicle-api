package com.shreeya.vehicletitle.config;

import com.shreeya.vehicletitle.application.IdempotencyConflictException;
import com.shreeya.vehicletitle.application.InvalidSubmissionException;
import com.shreeya.vehicletitle.application.TransactionNotFoundException;
import com.shreeya.vehicletitle.domain.InvalidTransitionException;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ws.config.annotation.EnableWs;
import org.springframework.ws.server.EndpointExceptionResolver;
import org.springframework.ws.soap.SoapFaultDefinition;
import org.springframework.ws.soap.server.endpoint.SoapFaultMappingExceptionResolver;
import org.springframework.ws.transport.http.MessageDispatcherServlet;
import org.springframework.ws.wsdl.wsdl11.DefaultWsdl11Definition;
import org.springframework.xml.xsd.SimpleXsdSchema;
import org.springframework.xml.xsd.XsdSchema;

import java.util.Properties;

@EnableWs
@Configuration
public class SoapWebServiceConfig {

    public static final String NAMESPACE =
            "https://github.com/shreeya227/vehicle-title-transaction-api/ws/title/v1";

    @Bean
    ServletRegistrationBean<MessageDispatcherServlet> messageDispatcherServlet(
            ApplicationContext applicationContext) {
        MessageDispatcherServlet servlet = new MessageDispatcherServlet();
        servlet.setApplicationContext(applicationContext);
        servlet.setTransformWsdlLocations(true);
        return new ServletRegistrationBean<>(servlet, "/ws/*");
    }

    @Bean(name = "title-transactions")
    DefaultWsdl11Definition titleTransactionsWsdl(XsdSchema titleTransactionsSchema) {
        DefaultWsdl11Definition definition = new DefaultWsdl11Definition();
        definition.setPortTypeName("TitleTransactionPort");
        definition.setLocationUri("/ws");
        definition.setTargetNamespace(NAMESPACE);
        definition.setSchema(titleTransactionsSchema);
        return definition;
    }

    @Bean
    XsdSchema titleTransactionsSchema() {
        return new SimpleXsdSchema(new ClassPathResource("xsd/title-transactions-v1.xsd"));
    }

    @Bean
    EndpointExceptionResolver soapFaultExceptionResolver() {
        SoapFaultMappingExceptionResolver resolver = new SoapFaultMappingExceptionResolver();
        Properties mappings = new Properties();
        mappings.setProperty(InvalidSubmissionException.class.getName(),
                SoapFaultDefinition.CLIENT.toString());
        mappings.setProperty(TransactionNotFoundException.class.getName(),
                SoapFaultDefinition.CLIENT.toString());
        mappings.setProperty(IdempotencyConflictException.class.getName(),
                SoapFaultDefinition.CLIENT.toString());
        mappings.setProperty(InvalidTransitionException.class.getName(),
                SoapFaultDefinition.CLIENT.toString());
        mappings.setProperty(IllegalArgumentException.class.getName(),
                SoapFaultDefinition.CLIENT.toString());
        resolver.setExceptionMappings(mappings);
        SoapFaultDefinition defaultFault = new SoapFaultDefinition();
        defaultFault.setFaultCode(SoapFaultDefinition.SERVER);
        resolver.setDefaultFault(defaultFault);
        resolver.setOrder(1);
        return resolver;
    }
}
