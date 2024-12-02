package com.fund.stockProject.stock.entity;

import com.fund.stockProject.stock.domain.EXCHANGENUM;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class ExchangeNumConverter implements AttributeConverter<EXCHANGENUM, String> {

    @Override
    public String convertToDatabaseColumn(EXCHANGENUM attribute) {
        return attribute != null ? attribute.getCode() : null;
    }

    @Override
    public EXCHANGENUM convertToEntityAttribute(String dbData) {
        return dbData != null ? EXCHANGENUM.fromCode(dbData) : null;
    }
}