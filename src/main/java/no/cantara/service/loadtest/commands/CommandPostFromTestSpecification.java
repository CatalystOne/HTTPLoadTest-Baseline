package no.cantara.service.loadtest.commands;

import com.github.kevinsawicki.http.HttpRequest;
import no.cantara.base.command.BaseHttpPostHystrixCommand;
import no.cantara.service.model.TestSpecification;

import java.net.URI;
import java.util.Map;

public class CommandPostFromTestSpecification extends BaseHttpPostHystrixCommand<String> {

    String uri;
    String contentType = "text/xml;charset=UTF-8";
    String template = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:aut=\"http://dbonline.no/webservices/xsd/Autorisasjon\" xmlns:per=\"http://dbonline.no/webservices/xsd/PersonInfo\">\n" +
            "   <soapenv:Header>\n" +
            "      <aut:UserAuthorization>\n" +
            "         <UserID>#BrukerID</UserID>\n" +
            "         <Passord>#Passord</Passord>\n" +
            "         <EndUser>MyEndUserName</EndUser>\n" +
            "         <Versjon>v1-1-0</Versjon>\n" +
            "     </aut:UserAuthorization>\n" +
            "   </soapenv:Header>\n" +
            "   <soapenv:Body>\n" +
            "      <per:GetPerson>\n" +
            "         <Internref>XYZXYZXYZXYZ</Internref>\n" +
            "         <NameAddress>1</NameAddress>\n" +
            "         <InterestCode>1</InterestCode>\n" +
            "         <Beta>Detaljer</Beta>\n" +
            "      </per:GetPerson>\n" +
            "   </soapenv:Body>\n" +
            "</soapenv:Envelope>\n";


    public CommandPostFromTestSpecification(TestSpecification testSpecification) {
        super(URI.create(testSpecification.getCommand_url()), "hystrixGroupKey");
        this.template = updateTemplateWithvaluesFromMap(testSpecification.getCommand_template(), testSpecification.getCommand_replacement_map());
        this.contentType = testSpecification.getCommand_contenttype();
        this.uri = testSpecification.getCommand_url();
    }



    public static String updateTemplateWithvaluesFromMap(String template, Map<String, String> templatereplacementMap) {
        if (template == null) {
            return "";
        }
        if (templatereplacementMap == null) {
            return template;
        }
        for (String key : templatereplacementMap.keySet()) {
            if (template.contains(key)) {
                template = template.replaceAll(key, templatereplacementMap.get(key));
            }
        }
        return template;
    }

    @Override
    protected HttpRequest dealWithRequestBeforeSend(HttpRequest request) {
        super.dealWithRequestBeforeSend(request);
        if (template.contains("soapenv:Envelope")) {
            //request.getConnection().addRequestProperty("SOAPAction", SOAP_ACTION);
        }
        request.contentType(contentType).send(this.template);
        return request;
    }


    @Override
    protected String getTargetPath() {
        return "";
    }
}