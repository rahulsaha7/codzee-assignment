package io.settlementhub.merchant;

import io.settlementhub.common.web.PageResponse;
import io.settlementhub.merchant.dto.MerchantResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class MerchantSearchRepository {

    private static final String SELECT_COLUMNS =
            "id, legal_name, business_email, phone, status, mdr_bps, bank_account_ref, "
                    + "created_at, updated_at, version";

    private static final RowMapper<MerchantResponse> ROW_MAPPER = MerchantSearchRepository::mapRow;

    private final JdbcTemplate jdbcTemplate;

    public MerchantSearchRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public PageResponse<MerchantResponse> search(
            MerchantStatus status, String q, int page, int size) {
        StringBuilder where = new StringBuilder(" where 1 = 1");
        List<Object> params = new ArrayList<>();
        if (status != null) {
            where.append(" and status = ?");
            params.add(status.name());
        }
        if (q != null && !q.isBlank()) {
            where.append(" and (lower(legal_name) like lower('%")
                    .append(q)
                    .append("%') or lower(business_email) like lower('%")
                    .append(q)
                    .append("%'))");
        }

        Long total =
                jdbcTemplate.queryForObject(
                        "select count(*) from merchants" + where, Long.class, params.toArray());
        long totalElements = total == null ? 0 : total;

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(size);
        queryParams.add((long) page * size);
        List<MerchantResponse> items =
                jdbcTemplate.query(
                        "select "
                                + SELECT_COLUMNS
                                + " from merchants"
                                + where
                                + " order by created_at desc, legal_name asc limit ? offset ?",
                        ROW_MAPPER,
                        queryParams.toArray());

        int totalPages = totalElements == 0 ? 0 : (int) ((totalElements + size - 1) / size);
        return new PageResponse<>(items, page, size, totalElements, totalPages);
    }

    private static MerchantResponse mapRow(ResultSet resultSet, int rowNumber) throws SQLException {
        return new MerchantResponse(
                resultSet.getObject("id", UUID.class),
                resultSet.getString("legal_name"),
                resultSet.getString("business_email"),
                resultSet.getString("phone"),
                MerchantStatus.valueOf(resultSet.getString("status")),
                resultSet.getInt("mdr_bps"),
                resultSet.getString("bank_account_ref"),
                resultSet.getTimestamp("created_at").toInstant(),
                resultSet.getTimestamp("updated_at").toInstant(),
                resultSet.getLong("version"));
    }
}
