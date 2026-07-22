package io.github.accontra.eval.api.response;

import io.github.accontra.eval.application.service.CalibrationService.CalibrationDetail;

import java.util.List;

/**
 * 标定响应 — 逐条标定结果汇总。
 */
public class CalibrationResponse {
    private int total;
    private int success;
    private int failed;
    private List<CalibrationDetail> details;

    public CalibrationResponse() {}

    public CalibrationResponse(int total, int success, int failed, List<CalibrationDetail> details) {
        this.total = total;
        this.success = success;
        this.failed = failed;
        this.details = details;
    }

    public int getTotal() { return total; }
    public void setTotal(int total) { this.total = total; }

    public int getSuccess() { return success; }
    public void setSuccess(int success) { this.success = success; }

    public int getFailed() { return failed; }
    public void setFailed(int failed) { this.failed = failed; }

    public List<CalibrationDetail> getDetails() { return details; }
    public void setDetails(List<CalibrationDetail> details) { this.details = details; }
}
