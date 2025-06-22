package com.fund.stockProject.preference.domain;

import lombok.Getter;

@Getter
public enum PreferenceType {
    BOOKMARK("북마크"),
    NEVER_SHOW("다신 보지 않음");

    private final String description;

    PreferenceType(String description) {
        this.description = description;
    }

}
