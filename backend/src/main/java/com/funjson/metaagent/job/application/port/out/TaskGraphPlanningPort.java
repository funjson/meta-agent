package com.funjson.metaagent.job.application.port.out;

import java.util.Optional;

import com.funjson.metaagent.job.domain.TaskGraphPlan;
import com.funjson.metaagent.job.domain.TaskGraphPlanningRequest;

/**
 * 定义 Job 层调用外部智能能力生成复合 TaskGraph 的端口。
 */
public interface TaskGraphPlanningPort {

    /**
     * 生成结构化 Task Graph 候选。
     *
     * @param request 规划请求
     * @return 可解析且通过基础合同的候选图
     */
    Optional<TaskGraphPlan> plan(TaskGraphPlanningRequest request);
}
