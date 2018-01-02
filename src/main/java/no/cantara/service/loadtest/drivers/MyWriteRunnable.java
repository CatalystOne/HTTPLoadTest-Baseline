package no.cantara.service.loadtest.drivers;

import no.cantara.commands.CommandGetURLWithTemplate;
import no.cantara.commands.CommandPostURLWithTemplate;
import no.cantara.service.LoadTestConfig;
import no.cantara.service.LoadTestResult;
import no.cantara.service.TestSpecification;
import no.cantara.service.loadtest.LoadTestExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Random;

public class MyWriteRunnable implements Runnable {
    private final List<TestSpecification> testSpecificationList;
    private static Random r = new Random();
    private final LoadTestResult loadTestResult;
    private final LoadTestConfig loadTestConfig;
    private static final Logger log = LoggerFactory.getLogger(MyWriteRunnable.class);

    public MyWriteRunnable(List<TestSpecification> testSpecificationList, LoadTestConfig loadTestConfig, LoadTestResult loadTestResult) {
        this.testSpecificationList = testSpecificationList;
        this.loadTestResult = loadTestResult;
        this.loadTestConfig = loadTestConfig;
        this.loadTestResult.setTest_tags("testSpecificationList: " + testSpecificationList);
    }

    @Override
    public void run() {
        long sleeptime = 0L + loadTestConfig.getTest_sleep_in_ms();
        // Check if we should randomize sleeptime
        if (loadTestConfig.isTest_randomize_sleeptime()) {
            int chance = r.nextInt(100);
            sleeptime = 0L + loadTestConfig.getTest_sleep_in_ms() * chance / 100;
        }
        try {
            //log.trace("Sleeping {} ms before test as configured in the loadTestConfig", sleeptime);
            Thread.sleep(sleeptime);
        } catch (Exception e) {
            log.warn("Thread interrupted in wait sleep", e);
        }
        long startTime = System.currentTimeMillis();

        logTimedCode(startTime, loadTestResult.getTest_run_no() + " - starting processing!");

        for (TestSpecification testSpecification : testSpecificationList) {
            if (testSpecification.getCommand_url().length() > 0) {

            }
            log.trace("Calling {}", testSpecification.getCommand_url());
            loadTestResult.setTest_success(true);
            String result;
            if (testSpecification.isCommand_http_post()) {
                CommandPostURLWithTemplate command = new CommandPostURLWithTemplate(testSpecification);
                result = command.execute();
                if (!command.isSuccessfulExecution()) {
                    loadTestResult.setTest_success(false);
                }
                if (command.isResponseRejected()) {
                    loadTestResult.setTest_deviation_flag(true);
                }
            } else {
                CommandGetURLWithTemplate command = new CommandGetURLWithTemplate(testSpecification);
                result = command.execute();
                if (!command.isSuccessfulExecution()) {
                    loadTestResult.setTest_success(false);
                }
                if (command.isResponseRejected()) {
                    loadTestResult.setTest_deviation_flag(true);
                }
            }
//            log.debug("Returned result: " + result);
        }
        loadTestResult.setTest_duration(Long.valueOf(System.currentTimeMillis() - startTime));
        logTimedCode(startTime, loadTestResult.getTest_run_no() + " - processing completed!");

        LoadTestExecutorService.addResult(loadTestResult);

    }

    private static void logTimedCode(long startTime, String msg) {
        long elapsedSeconds = (System.currentTimeMillis() - startTime);
        // log.trace("{}ms [{}] {}\n", elapsedSeconds, Thread.currentThread().getName(), msg);
    }

}