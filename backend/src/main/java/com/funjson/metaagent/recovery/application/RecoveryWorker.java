package com.funjson.metaagent.recovery.application;

import java.util.UUID;

import com.funjson.metaagent.recovery.application.port.out.RecoveryStore;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 后台扫描可自动恢复的 TaskRun，并委托 ResumeExecutor 执行。
 */
@Service
public class RecoveryWorker {

    private static final int BATCH_SIZE = 8;

    private final RecoveryStore recoveryStore;
    private final TaskRunResumeExecutor resumeExecutor;

    /**
     * 创建恢复 Worker。
     *
     * @param recoveryStore Recovery Store
     * @param resumeExecutor 恢复执行器
     */
    public RecoveryWorker(
            RecoveryStore recoveryStore,
            TaskRunResumeExecutor resumeExecutor) {
        this.recoveryStore = recoveryStore;
        this.resumeExecutor = resumeExecutor;
    }

    /**
     * 周期扫描租约过期或等待子 Job 回传的可恢复 TaskRun。
     */
    @Scheduled(fixedDelayString = "${meta-agent.recovery.scan-delay-ms:30000}")
    public void scan() {
        for (UUID taskRunId
                : recoveryStore.findAutoRecoveryCandidates(BATCH_SIZE)) {
            try {
                // ResumeExecutor 会重新做策略判定和租约竞争。
                resumeExecutor.resume(taskRunId);
            } catch (RuntimeException ignored) {
                // 单个恢复失败不能阻塞后续候选；失败已由 ResumeExecutor 审计。
            }
        }
    }
}
