package no.cantara.simulator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import no.cantara.service.loadtest.LoadTestExecutorService;
import no.cantara.service.loadtest.util.TemplateUtil;
import no.cantara.service.model.TestSpecification;
import nu.validator.messages.MessageEmitter;
import nu.validator.messages.MessageEmitterAdapter;
import nu.validator.messages.TextMessageEmitter;
import nu.validator.servlet.imagereview.ImageCollector;
import nu.validator.source.SourceCode;
import nu.validator.validation.SimpleDocumentValidator;
import nu.validator.xml.SystemErrErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.RequestBody;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import org.json.JSONObject;

import javax.ws.rs.*;
import javax.ws.rs.core.*;
import javax.xml.parsers.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static no.cantara.util.Configuration.loadFromDiskByName;

@Path(RestTestResource.REST_PATH)
public class RestTestResource {
    public static final String REST_PATH = "/resttest";
    private static final Logger log = LoggerFactory.getLogger(RestTestResource.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Autowired
    public RestTestResource() {
    }

    @GET
    @Path("/printLogs")
    public Response printLogs() {
        log.info("Invoked printLogs test");

        File debugFile = getLatestDebugFile();
//        File debugFile = getDebugFileWithinAMinute();

        String debugLog = buildDebugLog(debugFile);
        String response = String.format("<h3>Print printLogs:</h3> <br> %s", debugLog);

        return Response.ok(response).build();
    }

    @POST
    @Path("/debug")
    public Response debug(@FormParam("jsonConfig") String resource) {
        log.info("Invoked debug test specification with testSpecifiaction file {}", resource);

        if (resource.startsWith("FILE:")) {
            resource = loadFromDiskByName(resource.substring(5, resource.length()));
        }

        List<TestSpecification> testSpecifications = new ArrayList<>();
        try {
            testSpecifications = mapper.readValue(resource, new TypeReference<List<TestSpecification>>() {});
            LoadTestExecutorService.setReadTestSpecificationList(testSpecifications);
        } catch (Exception e) {
            log.warn("Could not convert to Json {},\n {e}", resource, e);
        }

        File file = new File(System.getProperty("user.dir") + "/logs/debug.log");
        writeToFile(file, "Input resource:\n " + resource + "\n", false);

        for (TestSpecification testSpecification : testSpecifications) {
            testSpecification.setCommand_url(TemplateUtil.updateTemplateWithValuesFromMap(testSpecification.getCommand_url(), testSpecification.getCommand_replacement_map()));
            testSpecification.setCommand_template(TemplateUtil.updateTemplateWithValuesFromMap(testSpecification.getCommand_template(), testSpecification.getCommand_replacement_map()));
            testSpecification.setCommand_http_authstring(TemplateUtil.updateTemplateWithValuesFromMap(testSpecification.getCommand_http_authstring(), testSpecification.getCommand_replacement_map()));
            writeToFile(file, "TestSpecification replaced variables: " + testSpecification.toLongString() + "\n", true);

            if (testSpecification.isCommand_http_post()) {
                String subType = testSpecification.getCommand_contenttype().split("/")[1];
                if (validateInput(subType, testSpecification.getCommand_template())) {
                    String response = String.format("{ \"entity\": %s }", testSpecification.getCommand_template());
                    writeToFile(file, response + "\n", true);
                } else {
                    log.error("Failed to validate request: {}", testSpecification.getCommand_template());
                }
            } else {
                String subPath = testSpecification.getCommand_url()
                        .substring(testSpecification.getCommand_url().indexOf(REST_PATH) + REST_PATH.length());
                subPath = subPath.isEmpty() ? "emptyPath" : "";
                String response = String.format("{ \"path\": %s }", subPath);
                writeToFile(file, response + "\n", true);
            }
        }

        String debugLog = buildDebugLog(file);
        String response = String.format("<h3>Debug:</h3> %s", debugLog);

        return Response.ok(response).build();
    }

    @POST
    @Path("{path : .*}")
    public Response postRequest(@Context HttpHeaders headers, @RequestBody String input) {
        log.info("Invoked postRequest with input: {} and headers: {}", input, headers.getRequestHeaders());

        if (validateInput(headers.getMediaType().getSubtype(), input)) {
            String response = String.format("{ \"entity\": %s }", input);
            return Response.ok(response).type(headers.getMediaType()).build();
        }

        log.error("Failed to validate request: {}", input);
        return Response.status(Response.Status.BAD_REQUEST).build();
    }

    @GET
    @Path("{path : .*}")
    public Response getRequest(@Context HttpHeaders headers, @PathParam("path") String path) {
        log.info("Invoked getRequest with path: {} and headers: {}", path, headers.getRequestHeaders());

        if (path.isEmpty()) {
            path = "emptyPath";
        }
        String response = String.format("{ \"path\": %s }", path);

        return Response.ok(response).build();
    }

    private void writeToFile(File file, String s, boolean append) {
        try {
            if (append) {
                Files.write(file.toPath(), s.getBytes(), StandardOpenOption.APPEND);
            } else {
                Files.write(file.toPath(), s.getBytes());
            }
        } catch (IOException e) {
            log.error("writeToFile error: {}", e);
        }
    }

    private String buildDebugLog(File file) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            String line;
            while ((line = br.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            log.error("Failed to read lines. {}", e);
        }

        String[] htmlTags = new String[]{"<html>", "<body>", "<a", "</body>", "</html>"};
        StringBuilder builder = new StringBuilder();
        for (String s : lines) {
            if (Arrays.stream(htmlTags).anyMatch(s::contains)) {
                s = s.replace("<", "&lt;").replace(">", "&gt;");
            }
            s = s + "<br>";
            builder.append(s);
        }

        return builder.toString();
    }

    private File getLatestDebugFile() {
        Pattern FILENAME_DATE_PATTERN = Pattern.compile(".*_.*_(.*?_.*?)\\..*");
        File debugFile = new File(System.getProperty("user.dir") + "/logs/debug_file.log");

        File folder = new File(System.getProperty("user.dir") + "/logs/");
        FilenameFilter beginsWith = (directory, filename) -> filename.startsWith("debug_file");

        File[] files = folder.listFiles(beginsWith);
        if (files == null) {
            throw new NotFoundException("No printLogs files found, that starts with: " + beginsWith);
        }
        List<String> fileDates = new ArrayList<>();
        for (File f: files) {
            Matcher matcher = FILENAME_DATE_PATTERN.matcher(f.getName());
            if (matcher.matches()) {
                fileDates.add(matcher.group(1));
            }
        }

        String lastDate = fileDates.stream().max(Comparator.naturalOrder()).orElse("");
        for (File f : files) {
            if (f.getName().contains(lastDate)) {
                debugFile = f;
            }
        }

        log.info("Debugging file: {}", debugFile);
        return debugFile;
    }

    private File getDebugFileWithinAMinute() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH:mm");
        File debugFile;
        String prefixPath = "/logs/debug_file_";
        if (new File(System.getProperty("user.dir") + prefixPath + LocalDateTime.now().format(formatter) + ".log").exists()) {
            debugFile = new File(System.getProperty("user.dir") + prefixPath + LocalDateTime.now().format(formatter) + ".log");
            log.info("Debugging file path timeNow: {}", debugFile.getPath());
        } else if (new File(System.getProperty("user.dir") + prefixPath + LocalDateTime.now().plusMinutes(1).format(formatter) + ".log").exists()) {
            debugFile = new File(System.getProperty("user.dir") + prefixPath + LocalDateTime.now().plusMinutes(1).format(formatter) + ".log");
            log.info("Debugging file path timePlusOne: {}", debugFile.getPath());
        } else if (new File(System.getProperty("user.dir") + prefixPath + LocalDateTime.now().minusMinutes(1).format(formatter) + ".log").exists()) {
            debugFile = new File(System.getProperty("user.dir") + prefixPath + LocalDateTime.now().minusMinutes(1).format(formatter) + ".log");
            log.info("Debugging file path timeMinusOne: {}", debugFile.getPath());
        } else {
            debugFile = new File(System.getProperty("user.dir") + "/logs/debug_file.log");
            log.info("Debugging file path default: {}", debugFile.getPath());
        }

        return debugFile;
    }

    private boolean validateInput(String type, String input) {
        switch (type) {
            case "json":
                return validateJson(input);
            case "xml":
                return validateXML(input);
            case "html":
                return validateHtml(input);
            case "plain":
                return true;
            default:
                log.warn("Invalid input type {}", type);
                return false;
        }
    }

    private boolean validateJson(String input) {
        JSONParser parser = new JSONParser();
        try {
            Object parsedObject = parser.parse(input);
            if (parsedObject instanceof JSONObject) {
                return true;
            }
        } catch (ParseException e) {
            log.error("Failed to parse json. " + e);
        }

        return false;
    }

    private boolean validateXML(String xml) {
        DefaultHandler handler = new DefaultHandler();
        try {
            SAXParser saxParser = SAXParserFactory.newInstance().newSAXParser();
            InputStream stream = new ByteArrayInputStream(xml.getBytes("UTF-8"));
            saxParser.parse(stream, handler);
            return true;
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.error("Failed to parse xml. " + e);
            return false;
        }
    }

    private boolean validateHtml( String htmlContent ) {
        InputStream in;
        MessageEmitterAdapter errorHandler;
        try {
            in = new ByteArrayInputStream( htmlContent.getBytes( "UTF-8" ));
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            SourceCode sourceCode = new SourceCode();
            ImageCollector imageCollector = new ImageCollector(sourceCode);
            boolean showSource = false;
            MessageEmitter emitter = new TextMessageEmitter( out, false );
            errorHandler = new MessageEmitterAdapter(sourceCode, showSource, imageCollector, 0, false, emitter );
            errorHandler.setErrorsOnly( true );

            SimpleDocumentValidator validator = new SimpleDocumentValidator();
            validator.setUpMainSchema( "http://s.validator.nu/html5-rdfalite.rnc", new SystemErrErrorHandler());
            validator.setUpValidatorAndParsers( errorHandler, true, false );
            validator.checkHtmlInputSource( new InputSource( in ));

            return 0 == errorHandler.getErrors();
        } catch (Exception e) {
            log.error("Failed to parse html with exception {}", e);
            return false;
        }
    }
}
