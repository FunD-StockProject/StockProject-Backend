package com.fund.stockProject.user.entity;

import com.fund.stockProject.user.domain.PROVIDER;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true) // 이 컨버터가 Provider 타입에 대해 자동으로 적용되도록 설정
public class ProviderConverter implements AttributeConverter<PROVIDER, String> {

    @Override
    public String convertToDatabaseColumn(PROVIDER attribute) {
        // Provider Enum -> DB에 저장할 String 값으로 변환
        if (attribute == null) {
            return null;
        }
        return attribute.getProvider();
    }

    @Override
    public PROVIDER convertToEntityAttribute(String dbData) {
        // DB에서 읽어온 String 값 -> Provider Enum으로 변환
        if (dbData == null) {
            return null;
        }
        return PROVIDER.fromString(dbData); // 위에서 정의한 fromValue 메소드 사용
    }
}