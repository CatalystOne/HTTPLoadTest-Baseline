package no.cantara.service.loadtest.commands;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import no.cantara.service.commands.oauth2.CommandVerifyTokenTest;
import no.cantara.service.loadtest.util.HTTPResultUtil;
import no.cantara.service.loadtest.util.TemplateUtil;
import no.cantara.service.model.TestSpecification;
import no.cantara.service.testsupport.TestServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;

public class HystrixPostFromTestSpecificationTest {

    private final static Logger log = LoggerFactory.getLogger(CommandVerifyTokenTest.class);

    private static final ObjectMapper mapper = new ObjectMapper();


    private final Map<String, String> replacementMap = new HashMap<String, String>();

    private TestServer testServer;

    @BeforeClass
    public void startServer() throws Exception {
        testServer = new TestServer(getClass());
        testServer.start();
    }

    @AfterClass
    public void stop() {
        testServer.stop();
    }

    @Test
    public void testSOAPTemplateReplacement() {
        String soaptemplate = "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:aut=\"http://dbonline.no/webservices/xsd/Autorisasjon\" xmlns:per=\"http://dbonline.no/webservices/xsd/PersonInfo\">\n" +
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
        Map<String, String> replacements = new HashMap<>();
        replacements.put("#BrukerID", "TestBruker");
        replacements.put("#Passord", "TestPassord");
        assertTrue(new TemplateUtil(replacements).updateTemplateWithValuesFromMap(soaptemplate).contains("TestBruker"));
        assertTrue(new TemplateUtil(replacements).updateTemplateWithValuesFromMap(soaptemplate).contains("TestPassord"));

    }

    @Test
    public void testCommandPostFromTestSpecification() throws Exception {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("loadtest_setup/specifications/read/TestReadConfigReadTestSpecification.json").getFile());
        List<TestSpecification> readTestSpec = mapper.readValue(file, new TypeReference<List<TestSpecification>>() {
        });
        readTestSpec.forEach(ts -> ts.setCommand_url(ts.getCommand_url().replace("http://localhost:8086/HTTPLoadTest-baseline", testServer.getUrl())));
        Map<String, String> resolvedResultVariables = new HashMap<>();

        int n = 1;
        for (TestSpecification testSpecificationo : readTestSpec) {
            TestSpecification testSpecification = testSpecificationo.clone();
            testSpecification.resolveVariables(null, null, resolvedResultVariables);
            assertTrue(testSpecification.getCommand_url().length() > 0);
            log.trace("{} Calling {}", n, testSpecification.getCommand_url());
            String result;
            if (testSpecification.isCommand_http_post()) {
                HystrixPostCommand commandPostFromTestSpecification = new HystrixPostCommand(testSpecification, new AtomicInteger());
                result = commandPostFromTestSpecification.execute();
            } else {
                HystrixGetCommand commandGetFromTestSpecification = new HystrixGetCommand(testSpecification, new AtomicInteger());
                result = commandGetFromTestSpecification.execute();
            }
            resolvedResultVariables = HTTPResultUtil.parse(result, testSpecification.getCommand_response_map());

            log.debug("{} Returned result: {}", n, result);
            n++;
        }

    }

    @Test
    public void testDefaultConfigCommandPostFromTestSpecification() throws Exception {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("loadtest_setup/specifications/read/DefaultReadTestSpecification.json").getFile());
        List<TestSpecification> readTestSpec = new ArrayList<>();
        readTestSpec = mapper.readValue(file, new TypeReference<List<TestSpecification>>() {
        });
        readTestSpec.forEach(ts -> ts.setCommand_url(ts.getCommand_url().replace("http://localhost:8086/HTTPLoadTest-baseline", testServer.getUrl())));
        Map<String, String> resolvedResultVariables = new HashMap<>();
        for (TestSpecification testSpecificationo : readTestSpec) {
            TestSpecification testSpecification = testSpecificationo.clone();
            testSpecification.resolveVariables(null, null, resolvedResultVariables);
            assertTrue(testSpecification.getCommand_url().length() > 0);
            log.trace("Calling {}", testSpecification.getCommand_url());
            String result;
            if (testSpecification.isCommand_http_post()) {
                result = new HystrixPostCommand(testSpecification, new AtomicInteger()).execute();
            } else {
                result = new HystrixGetCommand(testSpecification, new AtomicInteger()).execute();
            }
            log.debug("Returned result: " + result);
            //assertTrue(result.length() > 0);
            resolvedResultVariables = HTTPResultUtil.parse(result, testSpecification.getCommand_response_map());
        }

    }

    @Test
    public void testDefaultConfigCommandPostFromStringTestSpecification() throws Exception {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("loadtest_setup/specifications/write/DefaultWriteTestSpecification.json").getFile());
        List<TestSpecification> readTestSpec = new ArrayList<>();
        readTestSpec = mapper.readValue(file, new TypeReference<List<TestSpecification>>() {
        });
        readTestSpec.forEach(ts -> ts.setCommand_url(ts.getCommand_url().replace("http://localhost:8086/HTTPLoadTest-baseline", testServer.getUrl())));
        Map<String, String> resolvedResultVariables = new HashMap<>();
        for (TestSpecification testSpecificationo : readTestSpec) {
            TestSpecification testSpecification = testSpecificationo.clone();
            testSpecification.resolveVariables(null, null, resolvedResultVariables);
            assertTrue(testSpecification.getCommand_url().length() > 0);
            log.trace("Calling {}", testSpecification.getCommand_url());
            String result;
            if (testSpecification.isCommand_http_post()) {
                HystrixPostCommand commandPostFromTestSpecification = new HystrixPostCommand(testSpecification, new AtomicInteger());
                result = commandPostFromTestSpecification.execute();
                log.trace("" + commandPostFromTestSpecification.isCircuitBreakerOpen());
            } else {
                result = new HystrixGetCommand(testSpecification, new AtomicInteger()).execute();
            }
            log.debug("Returned result: " + result);
            //assertTrue(result.length() > 0);
            resolvedResultVariables = HTTPResultUtil.parse(result, testSpecification.getCommand_response_map());
        }
    }

}