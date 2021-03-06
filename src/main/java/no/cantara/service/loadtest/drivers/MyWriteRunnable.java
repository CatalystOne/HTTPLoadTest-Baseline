package no.cantara.service.loadtest.drivers;

import no.cantara.service.loadtest.commands.Command;
import no.cantara.service.loadtest.commands.CommandFactory;
import no.cantara.service.loadtest.util.HTTPResultUtil;
import no.cantara.service.model.LoadTestConfig;
import no.cantara.service.model.LoadTestResult;
import no.cantara.service.model.TestSpecification;
import no.cantara.util.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static no.cantara.service.loadtest.util.HTTPResultUtil.first50;
import static no.cantara.service.loadtest.util.HTTPResultUtil.firstX;

public class MyWriteRunnable implements Callable<LoadTestResult> {
    private final List<TestSpecification> testSpecificationList;
    private static Random r = new Random();
    private final LoadTestResult loadTestResult;
    private final LoadTestConfig loadTestConfig;
    private final LoadTestExecutionContext loadTestExecutionContext;
    private static final boolean BREAK_ON_FAILURE = Configuration.getBoolean("loadtest.breakflowonfailure");
    private static final Logger log = LoggerFactory.getLogger(MyWriteRunnable.class);

    public MyWriteRunnable(List<TestSpecification> testSpecificationList, LoadTestConfig loadTestConfig, LoadTestResult loadTestResult, LoadTestExecutionContext loadTestExecutionContext) {
        this.testSpecificationList = testSpecificationList;
        this.loadTestResult = loadTestResult;
        this.loadTestConfig = loadTestConfig;
        this.loadTestExecutionContext = loadTestExecutionContext;
        //this.loadTestResult.setTest_tags("testSpecificationList: " + testSpecificationList);
        this.loadTestResult.setTest_tags("LoadTestId: " + loadTestConfig.getTest_id());
    }

    @Override
    public LoadTestResult call() {
        if (loadTestExecutionContext.stopped()) {
            return null;
        }

        long startNanoTime = System.nanoTime();

        logTimedCode(startNanoTime, loadTestResult.getTest_run_no() + " - starting processing!");
        Map<String, String> resolvedResultVariables = new HashMap<>();
        Map<String, String> inheritedVariables = loadTestConfig.getTest_global_variables_map();

        long commandDurationMicroSeconds = 0;
        int writeCommandNo = 1;
        for (TestSpecification testSpecificationo : testSpecificationList) {
            try {
                TestSpecification testSpecification = testSpecificationo.clone();

                testSpecification.resolveVariables(loadTestConfig.getTest_global_variables_map(), inheritedVariables, resolvedResultVariables);
                inheritedVariables.putAll(testSpecification.getCommand_replacement_map());

                if (testSpecification.getCommand_url().length() > 0) {
                    log.trace("Calling {} \n- template:{}", testSpecification.getCommand_url(), testSpecification.getCommand_template());
                    loadTestResult.setTest_success(false);
                    loadTestResult.setTest_tags(loadTestResult.getTest_tags() + " - (Write-URL:" + writeCommandNo++ + "/" +
                            Thread.currentThread().getName() + " " + testSpecification.getCommand_url() + ")");
                    String result;
                    if (testSpecification.isCommand_http_post()) {
                        Command command = CommandFactory.createPostCommand(testSpecification, loadTestExecutionContext.commandConcurrencyDegree());
                        result = command.execute();
                        loadTestResult.setCommand_concurrency_degree(command.getCommandConcurrencyDegreeOnEntry());
                        commandDurationMicroSeconds = commandDurationMicroSeconds + command.getRequestDurationMicroSeconds();
                        log.trace("{} returned response: {}", testSpecification.getCommand_url(), result);
                        if (!command.isSuccessfulExecution()) {
                            loadTestResult.setTest_success(false);
                            loadTestResult.setTest_tags(loadTestResult.getTest_tags() + ":F(" + firstX(result, 250) + ") + Req:( -" + testSpecification.toLongString() + ") - ");
                        }
                        if (command.isResponseRejected()) {
                            loadTestResult.setTest_deviation_flag(true);
                            loadTestResult.setTest_tags(loadTestResult.getTest_tags() + ":D(" + first50(result) + ") -");
                        }
                    } else {
                        Command command = CommandFactory.createGetCommand(testSpecification, loadTestExecutionContext.commandConcurrencyDegree());
                        result = command.execute();
                        loadTestResult.setCommand_concurrency_degree(command.getCommandConcurrencyDegreeOnEntry());
                        commandDurationMicroSeconds = commandDurationMicroSeconds + command.getRequestDurationMicroSeconds();
                        log.trace("{} returned response: {}", testSpecification.getCommand_url(), result);
                        if (!command.isSuccessfulExecution()) {
                            loadTestResult.setTest_success(false);
                            loadTestResult.setTest_tags(loadTestResult.getTest_tags() + ":F(" + firstX(result, 250) + ") + Req:( -" + testSpecification.toLongString() + ") - ");
                        }
                        if (command.isResponseRejected()) {
                            loadTestResult.setTest_deviation_flag(true);
                            loadTestResult.setTest_tags(loadTestResult.getTest_tags() + ":D(" + first50(result) + ") -");
                        }
                    }
                    log.trace("Returned result: W-{}.{} - {} ", loadTestResult.getTest_run_no(), writeCommandNo, result);
                    if (result == null || result.startsWith("StatusCode:")) {
                        loadTestResult.setTest_success(false);
                        loadTestResult.setTest_tags(loadTestResult.getTest_tags() + ":F(" + firstX(result, 250) + ") + Req:( -" + testSpecification.toLongString() + ") - ");
                    } else {
                        loadTestResult.setTest_success(true);
                        resolvedResultVariables = HTTPResultUtil.parse(result, testSpecification.getCommand_response_map());
                        loadTestResult.setTest_tags(loadTestResult.getTest_tags() + ":S(" + first50(result) + ") -:Vars(" + resolvedResultVariables + ") - ");
                        log.trace("{} Resolved variables: {} result: {} from command_response_map: {}", testSpecification.getCommand_url(), resolvedResultVariables, result, testSpecification.getCommand_response_map());
                    }
                }
                // We break the flow if one step fail
                if (!loadTestResult.isTest_success() && BREAK_ON_FAILURE) {
                    break;
                }
            } catch (Exception e) {
                log.error("Unable to clone TestSpecification", e);
            }


        }

        if (commandDurationMicroSeconds <= 0) {
            log.warn("commandDuration: {}, using fallback", commandDurationMicroSeconds);
            // fallback to include test-bench processing overhead as part of measured duration
            commandDurationMicroSeconds = TimeUnit.NANOSECONDS.toMicros(System.nanoTime() - startNanoTime);
        }

        loadTestResult.setTest_duration(commandDurationMicroSeconds / 1000.0);
        logTimedCode(startNanoTime, loadTestResult.getTest_run_no() + " - processing completed!");

        return loadTestResult;

    }

    private static void logTimedCode(long startNanoTime, String msg) {
        // long elapsedMilliseconds = Math.round((System.nanoTime() - startNanoTime) / 1000000.0);
        // log.trace("{}ms [{}] {}\n", elapsedMilliseconds, Thread.currentThread().getName(), msg);
    }

}
