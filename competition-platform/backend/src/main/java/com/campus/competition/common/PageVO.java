package com.campus.competition.common;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.Collections;
import java.util.List;

/**
 * 通用分页响应
 * data: { records, total, page, size, pages }
 */
public class PageVO<T> {

    private List<T> records;
    private long total;
    private long page;
    private long size;
    private long pages;

    public PageVO() {
    }

    public PageVO(List<T> records, long total, long page, long size, long pages) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = pages;
    }

    /** 从 MyBatis-Plus Page 转换 */
    public static <T> PageVO<T> from(Page<T> p) {
        return new PageVO<>(p.getRecords(), p.getTotal(), p.getCurrent(), p.getSize(), p.getPages());
    }

    /** 从内存 List 分页切片（用于已缓存列表） */
    public static <T> PageVO<T> of(long page, long size, List<T> list) {
        if (list == null) {
            list = Collections.emptyList();
        }
        long total = list.size();
        long pages = size <= 0 ? (total == 0 ? 0 : 1) : (total + size - 1) / size;
        long from = (page - 1) * size;
        List<T> records;
        if (from >= total) {
            records = Collections.emptyList();
        } else {
            records = list.subList((int) from, (int) Math.min(total, from + size));
        }
        return new PageVO<>(records, total, page, size, pages);
    }

    public List<T> getRecords() {
        return records;
    }

    public void setRecords(List<T> records) {
        this.records = records;
    }

    public long getTotal() {
        return total;
    }

    public void setTotal(long total) {
        this.total = total;
    }

    public long getPage() {
        return page;
    }

    public void setPage(long page) {
        this.page = page;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public long getPages() {
        return pages;
    }

    public void setPages(long pages) {
        this.pages = pages;
    }
}
