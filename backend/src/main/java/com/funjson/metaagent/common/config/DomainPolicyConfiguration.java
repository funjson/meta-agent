package com.funjson.metaagent.common.config;

import com.funjson.metaagent.clarification.domain.ClarificationPolicy;
import com.funjson.metaagent.intent.domain.ExplicitIntentRuleClassifier;
import com.funjson.metaagent.intent.domain.SafeFallbackIntentClassifier;
import com.funjson.metaagent.job.domain.JobRunStateGuard;
import com.funjson.metaagent.job.domain.TaskGraphValidator;
import com.funjson.metaagent.job.domain.DefaultJobCompletionPolicy;
import com.funjson.metaagent.job.domain.JobCompletionPolicy;
import com.funjson.metaagent.loop.domain.ExecutionDerivationPolicy;
import com.funjson.metaagent.loop.domain.ClarificationNeedDetector;
import com.funjson.metaagent.loop.domain.LoopCompletionJudge;
import com.funjson.metaagent.loop.domain.LoopCompletionPolicy;
import com.funjson.metaagent.loop.domain.LoopCorrectionPolicy;
import com.funjson.metaagent.loop.domain.LoopEvaluator;
import com.funjson.metaagent.loop.domain.LoopNodeStateMachine;
import com.funjson.metaagent.recovery.domain.RecoveryPolicy;
import com.funjson.metaagent.runtime.domain.PolicyResolver;
import com.funjson.metaagent.task.domain.DefaultTaskCompletionPolicy;
import com.funjson.metaagent.task.domain.TaskCompletionPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 在 Bootstrap 边界注册无框架依赖的领域策略对象。
 */
@Configuration
public class DomainPolicyConfiguration {

    /** @return 澄清轮次和重复问题策略 */
    @Bean
    public ClarificationPolicy clarificationPolicy() {
        return new ClarificationPolicy();
    }

    /** @return 显式意图规则分类器 */
    @Bean
    public ExplicitIntentRuleClassifier explicitIntentRuleClassifier() {
        return new ExplicitIntentRuleClassifier();
    }

    /** @return 安全降级意图分类器 */
    @Bean
    public SafeFallbackIntentClassifier safeFallbackIntentClassifier() {
        return new SafeFallbackIntentClassifier();
    }

    /** @return 执行派生边界策略 */
    @Bean
    public ExecutionDerivationPolicy executionDerivationPolicy() {
        return new ExecutionDerivationPolicy();
    }

    /** @return Loop Evaluator */
    @Bean
    public LoopCompletionPolicy loopCompletionPolicy(
            ClarificationNeedDetector clarificationNeedDetector,
            LoopCompletionJudge completionJudge) {
        return new LoopEvaluator(
                clarificationNeedDetector,
                completionJudge);
    }

    /** @return 长任务执行纠偏策略 */
    @Bean
    public LoopCorrectionPolicy loopCorrectionPolicy() {
        return new LoopCorrectionPolicy();
    }

    /** @return 模型输出中的澄清需求检测器 */
    @Bean
    public ClarificationNeedDetector clarificationNeedDetector() {
        return new ClarificationNeedDetector();
    }

    /** @return 默认 Task 验收策略 */
    @Bean
    public TaskCompletionPolicy taskCompletionPolicy() {
        return new DefaultTaskCompletionPolicy();
    }

    /** @return 默认 Job 验收策略 */
    @Bean
    public JobCompletionPolicy jobCompletionPolicy() {
        return new DefaultJobCompletionPolicy();
    }

    /** @return 只能收窄的跨层策略解析器 */
    @Bean
    public PolicyResolver policyResolver() {
        return new PolicyResolver();
    }

    /** @return LoopNode 状态机 */
    @Bean
    public LoopNodeStateMachine loopNodeStateMachine() {
        return new LoopNodeStateMachine();
    }

    /** @return Control 启动状态校验器 */
    @Bean
    public JobRunStateGuard runtimeStateGuard() {
        return new JobRunStateGuard();
    }

    /** @return Task Graph 不变量校验器 */
    @Bean
    public TaskGraphValidator taskGraphValidator() {
        return new TaskGraphValidator();
    }

    /** @return Recovery Policy */
    @Bean
    public RecoveryPolicy recoveryPolicy() {
        return new RecoveryPolicy();
    }
}
