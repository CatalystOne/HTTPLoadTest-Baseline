package no.cantara.service.loadtest.commands;

import com.github.kevinsawicki.http.HttpRequest;
import no.cantara.service.loadtest.util.TemplateUtil;
import no.cantara.service.model.TestSpecification;

import java.net.URI;
import java.util.Random;

public class CommandPostFromTestSpecification extends MyBaseHttpPostHystrixCommand<String> {

    String contentType;
    static Random r = new Random();

    String httpAuthorizationString;
    String template = "";


    public CommandPostFromTestSpecification(TestSpecification testSpecification) {
        super(URI.create(testSpecification.getCommand_url()),
                "hystrixCommandPostFromTestSpecification_" + r.nextInt(100));
        this.template = TemplateUtil.updateTemplateWithValuesFromMap(testSpecification.getCommand_template(), testSpecification.getCommand_replacement_map());
        this.contentType = testSpecification.getCommand_contenttype();
        this.httpAuthorizationString = testSpecification.getCommand_http_authstring();
    }




    @Override
    protected HttpRequest dealWithRequestBeforeSend(HttpRequest request) {
        super.dealWithRequestBeforeSend(request);
        if (this.httpAuthorizationString != null && this.httpAuthorizationString.length() > 10) {
            log.info("Added authorizarion header: {}", this.httpAuthorizationString);
            return request.authorization(this.httpAuthorizationString).contentType(contentType).send(this.template);
        }

        if (template.contains("soapenv:Envelope")) {
            //request.getConnection().addRequestProperty("SOAPAction", SOAP_ACTION);
        }

        return request.contentType(contentType).send(this.template);
    }

    @Override
    protected String dealWithFailedResponse(String responseBody, int statusCode) {

        if (statusCode < 300 && statusCode >= 200) {
            return responseBody;
        }
        return "StatusCode:" + statusCode + ":" + responseBody;

    }

    @Override
    protected String getTargetPath() {
        return "";
    }


    @Override
    protected String dealWithResponse(String responseBody) {
        //return "200" + ":" + super.dealWithResponse(response);
        return responseBody;
//        return super.dealWithResponse(response);
    }

}
