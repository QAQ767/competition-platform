package com.campus.competition.service;

/** 网页抓取抽象，便于测试注入固定响应。 */
public interface WebContentFetcher {

    FetchedPage fetch(String url) throws FetchException;
}
