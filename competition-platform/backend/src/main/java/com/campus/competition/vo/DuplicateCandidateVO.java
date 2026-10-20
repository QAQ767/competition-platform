package com.campus.competition.vo;

/** 疑似重复的现有赛事/草稿，供管理员核对差异。 */
public class DuplicateCandidateVO {

    private String type;
    private Long id;
    private String name;
    private String organizer;
    private Integer year;
    private String edition;
    private String track;
    private String sourceUrl;
    private int similarity;
    private String reason;

    public DuplicateCandidateVO() { }

    public DuplicateCandidateVO(String type, Long id, String name, String organizer,
                                Integer year, String track, String sourceUrl, int similarity, String reason) {
        this(type, id, name, organizer, year, null, track, sourceUrl, similarity, reason);
    }

    public DuplicateCandidateVO(String type, Long id, String name, String organizer,
                                Integer year, String edition, String track, String sourceUrl,
                                int similarity, String reason) {
        this.type = type;
        this.id = id;
        this.name = name;
        this.organizer = organizer;
        this.year = year;
        this.edition = edition;
        this.track = track;
        this.sourceUrl = sourceUrl;
        this.similarity = similarity;
        this.reason = reason;
    }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getOrganizer() { return organizer; }
    public void setOrganizer(String organizer) { this.organizer = organizer; }
    public Integer getYear() { return year; }
    public void setYear(Integer year) { this.year = year; }
    public String getEdition() { return edition; }
    public void setEdition(String edition) { this.edition = edition; }
    public String getTrack() { return track; }
    public void setTrack(String track) { this.track = track; }
    public String getSourceUrl() { return sourceUrl; }
    public void setSourceUrl(String sourceUrl) { this.sourceUrl = sourceUrl; }
    public int getSimilarity() { return similarity; }
    public void setSimilarity(int similarity) { this.similarity = similarity; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}
