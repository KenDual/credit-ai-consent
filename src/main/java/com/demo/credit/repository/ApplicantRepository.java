package com.demo.credit.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.SqlOutParameter;
import org.springframework.jdbc.core.SqlParameter;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Types;
import java.util.Map;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class ApplicantRepository {

    private final DataSource dataSource;

    public UUID createApplicant(String fullName, String email, String phone, String nationalId, String address) {
        SimpleJdbcCall call = new SimpleJdbcCall(dataSource)
                .withSchemaName("core")
                .withProcedureName("sp_CreateApplicant")
                .declareParameters(
                        new SqlParameter("full_name", Types.NVARCHAR),
                        new SqlParameter("email", Types.NVARCHAR),
                        new SqlParameter("phone", Types.NVARCHAR),
                        new SqlParameter("national_id", Types.NVARCHAR),
                        new SqlParameter("address", Types.NVARCHAR),
                        // uniqueidentifier → lấy dạng String rồi UUID.fromString
                        new SqlOutParameter("applicant_id", Types.VARCHAR)
                );

        Map<String, Object> out = call.execute(Map.of(
                "full_name", fullName,
                "email", email,
                "phone", phone,
                "national_id", nationalId,
                "address", address
        ));
        String id = String.valueOf(out.get("applicant_id"));
        return UUID.fromString(id);
    }
}
