package com.lotflow.ai.assistant.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 将版本化 Markdown 平台规则转换为带来源元数据的向量文档。 */
@Component
public class KnowledgeCorpusLoader {

    private static final String KNOWLEDGE_PATTERN = "classpath*:knowledge/v1/*.md";
    private static final String KNOWLEDGE_PATH_PREFIX = "knowledge/v1/";
    private static final String PUBLIC_AUDIENCE = "PUBLIC";
    private static final String PLATFORM_RULE_TYPE = "PLATFORM_RULE";
    private static final String RULE_VERSION = "v1";
    private static final String DIGEST_ALGORITHM = "SHA-256";

    private final RagProperties properties;

    public KnowledgeCorpusLoader(RagProperties properties) {
        this.properties = properties;
    }

    public KnowledgeCorpus load() {
        List<SourceDocument> sources = readSources();
        if (sources.isEmpty()) {
            throw new IllegalStateException("platform knowledge corpus is empty");
        }
        String corpusVersion = corpusVersion(sources);
        TokenTextSplitter splitter = splitter();
        List<Document> documents = new ArrayList<>();
        for (SourceDocument source : sources) {
            appendChunks(documents, splitter, source, corpusVersion);
        }
        if (documents.isEmpty()) {
            throw new IllegalStateException("platform knowledge corpus has no embeddable chunks");
        }
        return new KnowledgeCorpus(corpusVersion, List.copyOf(documents));
    }

    private List<SourceDocument> readSources() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources(KNOWLEDGE_PATTERN);
            List<SourceDocument> sources = new ArrayList<>(resources.length);
            for (Resource resource : List.of(resources).stream()
                    .sorted(Comparator.comparing(item -> filename(item))).toList()) {
                String filename = filename(resource);
                List<Document> loaded = new TextReader(resource).read();
                if (loaded.isEmpty() || loaded.getFirst().getText() == null
                        || loaded.getFirst().getText().isBlank()) {
                    continue;
                }
                String text = loaded.getFirst().getText().strip();
                sources.add(new SourceDocument(sourceId(filename), filename, title(text), text));
            }
            return sources;
        } catch (IOException exception) {
            throw new IllegalStateException("cannot read platform knowledge corpus", exception);
        }
    }

    private void appendChunks(List<Document> documents, TokenTextSplitter splitter,
                              SourceDocument source, String corpusVersion) {
        List<Document> chunks = splitter.apply(List.of(new Document(source.text())));
        for (int index = 0; index < chunks.size(); index++) {
            String chunkText = chunks.get(index).getText();
            if (chunkText == null || chunkText.isBlank()) {
                continue;
            }
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("sourceId", source.sourceId());
            metadata.put("sourcePath", KNOWLEDGE_PATH_PREFIX + source.filename());
            metadata.put("title", source.title());
            metadata.put("corpusVersion", corpusVersion);
            metadata.put("audience", PUBLIC_AUDIENCE);
            metadata.put("documentType", PLATFORM_RULE_TYPE);
            metadata.put("ruleVersion", RULE_VERSION);
            metadata.put("chunkIndex", index);
            String id = chunkId(source, corpusVersion, index);
            String content = source.title() + "\n" + chunkText.strip();
            documents.add(new Document(id, content, metadata));
        }
    }

    private TokenTextSplitter splitter() {
        return TokenTextSplitter.builder()
                .withChunkSize(properties.chunkSize())
                .withMinChunkSizeChars(properties.minChunkSizeChars())
                .withMinChunkLengthToEmbed(properties.minChunkLengthToEmbed())
                .withMaxNumChunks(properties.maxChunksPerFile())
                .withKeepSeparator(true)
                .build();
    }

    private String corpusVersion(List<SourceDocument> sources) {
        MessageDigest digest = digest();
        for (SourceDocument source : sources) {
            digest.update(source.filename().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
            digest.update(source.text().getBytes(StandardCharsets.UTF_8));
            digest.update((byte) 0);
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private String chunkId(SourceDocument source, String corpusVersion, int index) {
        MessageDigest digest = digest();
        String identity = source.sourceId() + ":" + corpusVersion + ":" + index;
        // Milvus 默认 doc_id 长度上限较短；稳定短哈希既能满足约束，也能让同版本重建保持幂等。
        return HexFormat.of().formatHex(digest.digest(identity.getBytes(StandardCharsets.UTF_8)))
                .substring(0, 32);
    }

    private MessageDigest digest() {
        try {
            return MessageDigest.getInstance(DIGEST_ALGORITHM);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String filename(Resource resource) {
        String filename = resource.getFilename();
        return filename == null || filename.isBlank() ? "unknown.md" : filename;
    }

    private String sourceId(String filename) {
        int extension = filename.lastIndexOf('.');
        return extension > 0 ? filename.substring(0, extension) : filename;
    }

    private String title(String text) {
        return text.lines().findFirst().filter(line -> line.startsWith("# "))
                .map(line -> line.substring(2).strip())
                .filter(value -> !value.isBlank())
                .orElse("平台规则");
    }

    private record SourceDocument(String sourceId, String filename, String title, String text) {
    }
}
