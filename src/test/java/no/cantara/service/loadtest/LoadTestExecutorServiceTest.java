package no.cantara.service.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import no.cantara.service.loadtest.util.LoadTestResultUtil;
import no.cantara.service.model.LoadTestConfig;
import no.cantara.service.model.LoadTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import java.io.File;
import java.util.List;

import static org.testng.Assert.assertTrue;

public class LoadTestExecutorServiceTest {

    private static final Logger log = LoggerFactory.getLogger(LoadTestExecutorServiceTest.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    @Test(priority = 99, enabled = false)
    public void executeTestConfigFromFile() throws Exception {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("loadtest_setup/configurations/LoadTestConfig.json").getFile());
        LoadTestConfig fileLoadtest = mapper.readValue(file, LoadTestConfig.class);
        assertTrue(fileLoadtest.getTest_id().equalsIgnoreCase("TestID"));
        long startTime = System.currentTimeMillis();

        LoadTestExecutorService.executeLoadTest(fileLoadtest, false);
        long endTime = System.currentTimeMillis();

        Thread.sleep(200 * fileLoadtest.getTest_no_of_threads());

        List<LoadTestResult> resultList = LoadTestExecutorService.getResultListSnapshot();
        log.info("Results from tests:" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultList));

        log.info(LoadTestResultUtil.printStats(resultList, true));
        log.info("Run-time: {} ms, configured run-time: {}", endTime - startTime, fileLoadtest.getTest_duration_in_seconds() * 1000);

    }

    @Test(priority = 99, enabled = true)
    public void executeAsyncTestConfigFromFile() throws Exception {

        ClassLoader classLoader = getClass().getClassLoader();
        File file = new File(classLoader.getResource("loadtest_setup/configurations/LoadTestConfig.json").getFile());
        LoadTestConfig fileLoadtest = mapper.readValue(file, LoadTestConfig.class);
        assertTrue(fileLoadtest.getTest_id().equalsIgnoreCase("TestID"));
        long startTime = System.currentTimeMillis();

        LoadTestExecutorService.executeLoadTest(fileLoadtest, true);
        long endTime = System.currentTimeMillis();

        Thread.sleep(2000);

        List<LoadTestResult> resultList = LoadTestExecutorService.getResultListSnapshot();
//        log.info("Results from tests:" + mapper.writerWithDefaultPrettyPrinter().writeValueAsString(resultList));

        log.info(LoadTestResultUtil.printStats(resultList, true));
        log.info("Run-time: {} ms, configured run-time: {}", endTime - startTime, fileLoadtest.getTest_duration_in_seconds() * 1000);

    }

}