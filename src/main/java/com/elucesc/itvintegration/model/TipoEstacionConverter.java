package com.elucesc.itvintegration.model;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TipoEstacionConverter implements AttributeConverter<TipoEstacion, String> {

    @Override
    public String convertToDatabaseColumn(TipoEstacion tipo) {
        if (tipo == null) return null;
        return tipo.getValor(); // <-- usa "Estación_fija", etc.
    }

    @Override
    public TipoEstacion convertToEntityAttribute(String dbData) {
        return TipoEstacion.fromString(dbData);
    }
}