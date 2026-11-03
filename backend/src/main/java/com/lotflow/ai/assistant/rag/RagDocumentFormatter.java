package com.lotflow.ai.assistant.rag;

import org.springframework.ai.document.Document;

import java.util.List;

/** 给检索片段分配稳定的回答内引用编号。 */
public final class RagDocumentFormatter {

    private RagDocumentFormatter() {
    }

    public static String format(List<Document> documents) {
        StringBuilder context = new StringBuilder();
        for (int index = 0; index < documents.size(); index++) {
            Document document = documents.get(index);
            context.append("[K").append(index + 1).append("] 来源：")
                    .append(metadata(document, "sourcePath")).append("；主题：")
                    .append(metadata(document, "title")).append('\n')
                    .append(document.getText()).append("\n\n");
        }
        return context.toString().strip();
    }

    private static String metadata(Document document, String key) {
        Object value = document.getMetadata().get(key);
        return value == null ? "未标注" : value.toString();
    }
}
