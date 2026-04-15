package org.example.core;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import org.example.config.AppConfig;
import java.io.IOException;
import java.util.regex.Pattern;

public class JsonInspector {

    // Расширенный паттерн: добавили UNION SELECT, DELETE, и проверки на скрипты
    private static final Pattern SECURITY_PATTERN = Pattern.compile(
            "(\\.\\./|select\\s+|drop\\s+table|delete\\s+from|union\\s+select|<script|javascript:|eval\\()",
            Pattern.CASE_INSENSITIVE
    );

    public static String checkSafety(JsonParser parser, int unusedDepth) throws IOException {
        int totalElements = 0;
        int currentDepth = 0;
        int fieldsInCurrentObject = 0; // Счетчик полей для защиты от "широких" объектов

        var security = AppConfig.getConfig().getSecurity();

        while (parser.nextToken() != null) {
            JsonToken token = parser.getCurrentToken();
            totalElements++;

            // 1. Защита от JSON-бомбы (общее количество элементов)
            if (totalElements > security.getMaxTotalElements()) {
                return "JSON_LIMIT: Too many elements";
            }

            // 2. Глубокий анализ структуры
            if (token == JsonToken.START_OBJECT || token == JsonToken.START_ARRAY) {
                currentDepth++;
                fieldsInCurrentObject = 0; // Сбрасываем при входе в новый объект
            } else if (token == JsonToken.END_OBJECT || token == JsonToken.END_ARRAY) {
                currentDepth--;
            }

            if (currentDepth > security.getMaxDepth()) {
                return "DEPTH_LIMIT: JSON depth too high";
            }

            // 3. Инспекция ключей (FIELD_NAME)
            if (token == JsonToken.FIELD_NAME) {
                fieldsInCurrentObject++;

                // Проверка на "ширину" объекта
                if (fieldsInCurrentObject > security.getMaxObjectSize()) {
                    return "OBJECT_SIZE_LIMIT: Too many fields in one object";
                }

                String fieldName = parser.getCurrentName();

                // Запрещенные административные поля
                if ("admin".equals(fieldName) || "root".equals(fieldName) || "internal".equals(fieldName)) {
                    return "FORBIDDEN_FIELD: " + fieldName;
                }

                if (fieldName.length() > 128) return "FIELD_NAME_LIMIT: Too long";

                // Тяжелая проверка регуляркой
                if (isSuspicious(fieldName)) {
                    return "SUSPICIOUS_KEY: " + fieldName;
                }
            }

            // 4. Инспекция текстовых данных (VALUE_STRING)
            if (token == JsonToken.VALUE_STRING) {
                String text = parser.getText();

                if (text.length() > security.getMaxStringLen()) {
                    return "STRING_LENGTH_LIMIT: Value too long";
                }

                // Самая тяжелая операция для CPU при больших массивах
                if (isSuspicious(text)) {
                    return "SUSPICIOUS_CONTENT: Dangerous payload detected";
                }
            }
        }

        return null; // Проверка пройдена
    }

    private static boolean isSuspicious(String input) {
        if (input == null || input.isEmpty()) return false;
        // Используем эффективный Matcher
        return SECURITY_PATTERN.matcher(input).find();
    }
}
