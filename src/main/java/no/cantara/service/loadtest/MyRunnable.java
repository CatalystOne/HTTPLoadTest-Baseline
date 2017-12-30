package no.cantara.service.loadtest;

import no.cantara.service.LoadTestResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URL;

public class MyRunnable implements Runnable {
    private final String url;
    private final LoadTestResult loadTestResult;
    private static final Logger log = LoggerFactory.getLogger(LoadTestResource.class);

    MyRunnable(String url, LoadTestResult loadTestResult) {
        this.url = url;
        this.loadTestResult = loadTestResult;
    }

    @Override
    public void run() {
        long startTime = System.currentTimeMillis();

        logTimedCode(startTime, loadTestResult.getTest_run_no() + " - starting processing!");

        String result = "";
        int code = 200;
        try {
            URL siteURL = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) siteURL
                    .openConnection();
            connection.setRequestMethod("GET");
            connection.connect();

            code = connection.getResponseCode();
            if (code == 200) {
                result = "Green\t";
                loadTestResult.setTest_success(true);
            }
        } catch (Exception e) {
            result = "->Red<-\t";
            loadTestResult.setTest_deviation_flag(true);
        }
        System.out.println(url + "\t\tStatus:" + result);
        logTimedCode(startTime, loadTestResult.getTest_run_no() + " - processing completed!");
        LoadTestExecutorService.addResult(loadTestResult);

    }

    private static void logTimedCode(long startTime, String msg) {
        long elapsedSeconds = (System.currentTimeMillis() - startTime);
        log.trace("{}ms [{}] {}\n", elapsedSeconds, Thread.currentThread().getName(), msg);
    }

}
