package com.example.korrag.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Repository
public class VectorStoreRepository {

    private static final Logger log = LoggerFactory.getLogger(VectorStoreRepository.class);
    private final JdbcTemplate jdbcTemplate;

    public VectorStoreRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void upsertVector(String acceptNo, String name, String essayType,
                             int chunkIndex, String content, float[] embedding) {
        String vectorString = floatArrayToVectorString(embedding);

        jdbcTemplate.update("""
                INSERT INTO test.essay_vectors (accept_no, name, essay_type, chunk_index, content, embedding)
                VALUES (?, ?, ?, ?, ?, ?::vector)
                ON CONFLICT (accept_no, essay_type, chunk_index)
                DO UPDATE SET content = EXCLUDED.content,
                              embedding = EXCLUDED.embedding,
                              name = EXCLUDED.name,
                              created_at = NOW()
                """,
                acceptNo, name, essayType, chunkIndex, content, vectorString);
    }

    public boolean existsVector(String acceptNo, String essayType) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM test.essay_vectors WHERE accept_no = ? AND essay_type = ?",
                Integer.class, acceptNo, essayType);
        return count != null && count > 0;
    }

    public List<Map<String, Object>> searchSimilar(float[] queryEmbedding, int topK, double threshold) {
        String vectorString = floatArrayToVectorString(queryEmbedding);

        return jdbcTemplate.queryForList("""
                SELECT accept_no,
                       name,
                       essay_type,
                       chunk_index,
                       content,
                       1 - (embedding <=> ?::vector) AS similarity
                FROM test.essay_vectors
                WHERE 1 - (embedding <=> ?::vector) >= ?
                ORDER BY embedding <=> ?::vector
                LIMIT ?
                """,
                vectorString, vectorString, threshold, vectorString, topK);
    }

    private String floatArrayToVectorString(float[] embedding) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < embedding.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(embedding[i]);
        }
        sb.append("]");
        return sb.toString();
    }
}
