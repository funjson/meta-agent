package com.funjson.metaagent.intent.application;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import com.funjson.metaagent.intent.domain.PendingInteractionCandidate;
import com.funjson.metaagent.intent.domain.PendingInteractionMatch;
import com.funjson.metaagent.intent.domain.PendingInteractionMatchType;
import org.springframework.stereotype.Service;

/**
 * 匹配用户新消息是否在回答某个等待交互。
 *
 * <p>v0.1 使用保守规则：明确像回答时才绑定，证据不足时回到新意图或消歧。
 * 该类是后续模型化 Intent Router 的替换点。</p>
 */
@Service
public class PendingInteractionMatcher {

    private static final double MATCH_THRESHOLD = 0.42;
    private static final double TIE_DELTA = 0.12;

    /**
     * 根据用户消息和等待候选给出路由判断。
     *
     * @param userMessage 当前用户消息
     * @param candidates Conversation 下全部等待候选
     * @return 匹配结果
     */
    public PendingInteractionMatch match(
            String userMessage,
            List<PendingInteractionCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return new PendingInteractionMatch(
                    PendingInteractionMatchType.NEW_INTENT,
                    null,
                    1.0,
                    "当前没有等待交互候选，进入常规意图识别。");
        }
        String normalized = normalize(userMessage);
        if (looksLikeExplicitNewIntent(normalized)) {
            return new PendingInteractionMatch(
                    PendingInteractionMatchType.NEW_INTENT,
                    null,
                    0.86,
                    "用户消息显式表达了新的任务或话题，不绑定等待项。");
        }
        List<ScoredCandidate> scored = candidates.stream()
                .map(candidate -> score(normalized, candidate))
                .sorted(Comparator
                        .comparingDouble(ScoredCandidate::score)
                        .reversed())
                .toList();
        ScoredCandidate best = scored.getFirst();
        boolean answerLike = looksLikeAnswer(normalized);

        // 只有一个候选且消息明显像补充材料时，可以安全绑定；这不是“下一条默认绑定”。
        if (candidates.size() == 1
                && answerLike
                && best.score() >= 0.18) {
            return answer(best.candidate().id(), best.score(),
                    "唯一等待项与当前补充内容匹配。");
        }

        if (answerLike
                && candidates.size() > 1
                && best.score() < 0.72) {
            return ambiguous(candidates.size(),
                    "当前消息像补充材料，但无法确定对应哪个等待任务。");
        }

        if (best.score() >= MATCH_THRESHOLD) {
            if (scored.size() > 1
                    && best.score() - scored.get(1).score() < TIE_DELTA) {
                return ambiguous(candidates.size(),
                        "多个等待项与当前消息相似，需要用户指定目标。");
            }
            return answer(best.candidate().id(), best.score(),
                    "当前消息与等待问题的合同词汇匹配。");
        }
        if (answerLike && candidates.size() > 1) {
            return ambiguous(candidates.size(),
                    "当前消息像补充材料，但无法确定对应哪个等待任务。");
        }
        return new PendingInteractionMatch(
                PendingInteractionMatchType.NEW_INTENT,
                null,
                0.64,
                "未发现足够证据将当前消息绑定到等待项。");
    }

    /**
     * 构造命中结果。
     */
    private PendingInteractionMatch answer(
            UUID targetId,
            double confidence,
            String summary) {
        return new PendingInteractionMatch(
                PendingInteractionMatchType.ANSWER_CLARIFICATION,
                targetId,
                Math.min(0.98, Math.max(0.0, confidence)),
                summary);
    }

    /**
     * 构造消歧结果。
     */
    private PendingInteractionMatch ambiguous(
            int candidateCount,
            String summary) {
        return new PendingInteractionMatch(
                PendingInteractionMatchType.AMBIGUOUS,
                null,
                0.5,
                summary + " 当前候选数：" + candidateCount);
    }

    /**
     * 计算用户消息与候选问题之间的轻量相似度。
     */
    private ScoredCandidate score(
            String normalized,
            PendingInteractionCandidate candidate) {
        Set<String> messageTerms = terms(normalized);
        Set<String> candidateTerms = terms(candidate.question()
                + " "
                + candidate.blockingSummary());
        if (messageTerms.isEmpty() || candidateTerms.isEmpty()) {
            return new ScoredCandidate(candidate, 0.0);
        }
        long overlap = messageTerms.stream()
                .filter(candidateTerms::contains)
                .count();
        double coverage = overlap / (double) Math.max(1, candidateTerms.size());
        double answerBonus = looksLikeAnswer(normalized) ? 0.18 : 0.0;
        return new ScoredCandidate(
                candidate,
                Math.min(1.0, coverage + answerBonus));
    }

    /**
     * 判断消息是否明显在开启新任务或切换话题。
     */
    private boolean looksLikeExplicitNewIntent(String value) {
        return value.matches(".*(先不管|另一个|新任务|重新开始|换个话题|算了|不用了).*")
                || value.matches("^(帮我|请你|我要|我想|需要你).*(生成|设计|分析|查询|搜索|创建|写|做|实现).*");
    }

    /**
     * 判断消息是否像对澄清问题的补充回答。
     */
    private boolean looksLikeAnswer(String value) {
        return value.matches(".*(我是|我叫|我的|名字|姓名|用于|用途|面向|风格|长度|字数|目标是|补充|职业|岗位|角色).*")
                || value.matches(".*(求职|面试|应聘|经验|正式|轻松|软件|开发|java|特别要求).*")
                || value.matches(".*(^|[\\s，,；;])\\d+[、，,.．].*")
                || value.length() <= 120 && value.contains("：");
    }

    /**
     * 归一化输入，保留中文语义并统一空白。
     */
    private String normalize(String value) {
        return value == null
                ? ""
                : value.replaceAll("\\s+", " ")
                        .trim()
                        .toLowerCase(Locale.ROOT);
    }

    /**
     * 提取粗粒度关键词，v0.1 避免引入分词器。
     */
    private Set<String> terms(String value) {
        Set<String> result = new HashSet<>();
        String normalized = normalize(value);
        for (String token : normalized.split("[\\s,，。；;：:、.!?？（）()【】\\[\\]-]+")) {
            if (token.length() >= 2) {
                result.add(token);
            }
        }
        for (String keyword : List.of(
                "姓名", "名字", "角色", "职业", "岗位", "用途", "场景",
                "风格", "长度", "字数", "目标", "边界", "输入", "输出",
                "求职", "面试", "经验", "正式", "软件", "开发")) {
            if (normalized.contains(keyword)) {
                result.add(keyword);
            }
        }
        return result;
    }

    /**
     * 候选评分。
     *
     * @param candidate 候选
     * @param score 分数
     */
    private record ScoredCandidate(
            PendingInteractionCandidate candidate,
            double score) {
    }
}
