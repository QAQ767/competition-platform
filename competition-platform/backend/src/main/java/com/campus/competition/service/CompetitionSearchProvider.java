package com.campus.competition.service;

import java.util.List;

/** 可替换的搜索适配器。密钥只在服务端，不进入浏览器。 */
public interface CompetitionSearchProvider {

    String name();

    boolean isConfigured();

    /** 可直接展示给管理员的配置状态，不包含密钥。 */
    String configStatus();

    List<SearchHit> search(String query, int maxResults) throws SearchException;
}
