package io.github.accontra.eval.application.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.github.accontra.eval.domain.model.EvalPromptTemplate;
import io.github.accontra.eval.infrastructure.mapper.EvalPromptTemplateMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Prompt 版本管理服务 — A1.2。
 *
 * 从 eval_prompt_template 加载 Prompt，替代硬编码。
 * 支持运行时切换版本 (is_active 字段)。
 */
@Component
public class PromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);
    private final EvalPromptTemplateMapper mapper;

    public PromptTemplateService(EvalPromptTemplateMapper mapper) { this.mapper = mapper; }

    /** 加载当前活跃的 Prompt */
    public EvalPromptTemplate getActive() {
        var qw = new LambdaQueryWrapper<EvalPromptTemplate>()
                .eq(EvalPromptTemplate::getIsActive, 1)
                .last("LIMIT 1");
        return mapper.selectOne(qw);
    }

    /** 加载指定版本 */
    public EvalPromptTemplate getVersion(String promptKey, String version) {
        var qw = new LambdaQueryWrapper<EvalPromptTemplate>()
                .eq(EvalPromptTemplate::getPromptKey, promptKey)
                .eq(EvalPromptTemplate::getVersion, version)
                .last("LIMIT 1");
        return mapper.selectOne(qw);
    }

    /** 列出某 key 的所有版本 */
    public List<EvalPromptTemplate> listVersions(String promptKey) {
        var qw = new LambdaQueryWrapper<EvalPromptTemplate>()
                .eq(EvalPromptTemplate::getPromptKey, promptKey)
                .orderByDesc(EvalPromptTemplate::getVersion);
        return mapper.selectList(qw);
    }

    /** 加载 SYSTEM + USER 两个 Prompt */
    public Map<String, String> loadScoringPrompts(String version) {
        var sys = getVersion("SCORING_SYSTEM", version);
        var usr = getVersion("SCORING_USER", version);
        return Map.of(
                "system", sys != null ? sys.getSystemText() : "",
                "user", usr != null ? usr.getUserText() : "");
    }

    /** 激活版本 (同时禁用同 key 其他版本) */
    @CacheEvict(value = "prompts", allEntries = true)
    public void activate(Long id) {
        var tpl = mapper.selectById(id);
        if (tpl == null) throw new IllegalArgumentException("Prompt 不存在: " + id);
        // 禁用同 key 所有版本
        var qw = new LambdaQueryWrapper<EvalPromptTemplate>()
                .eq(EvalPromptTemplate::getPromptKey, tpl.getPromptKey());
        var all = mapper.selectList(qw);
        for (var a : all) { a.setIsActive(0); mapper.updateById(a); }
        // 激活目标
        tpl.setIsActive(1);
        mapper.updateById(tpl);
        log.info("[Prompt] 激活: {}={}", tpl.getPromptKey(), tpl.getVersion());
    }

    /** 列出所有 Prompt */
    public List<EvalPromptTemplate> listAll() {
        return mapper.selectList(null);
    }

    /** 按版本聚合实验统计 (join eval_ai_experiment) */
    public Map<String, Object> versionStats() {
        var all = mapper.selectList(null);
        var byVersion = all.stream().collect(Collectors.groupingBy(EvalPromptTemplate::getVersion));
        var result = new java.util.LinkedHashMap<String, Object>();
        byVersion.forEach((v, list) -> result.put(v, Map.of("templates", list.size())));
        return result;
    }
}
