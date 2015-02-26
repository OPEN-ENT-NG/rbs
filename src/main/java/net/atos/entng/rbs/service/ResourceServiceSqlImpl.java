package net.atos.entng.rbs.service;

import static net.atos.entng.rbs.BookingStatus.CREATED;
import static net.atos.entng.rbs.BookingStatus.VALIDATED;
import static net.atos.entng.rbs.BookingUtils.getLocalAdminScope;
import static org.entcore.common.sql.Sql.parseId;
import static org.entcore.common.sql.SqlResult.parseShared;
import static org.entcore.common.sql.SqlResult.validResultHandler;
import static org.entcore.common.sql.SqlResult.validUniqueResultHandler;

import java.util.List;

import org.entcore.common.service.impl.SqlCrudService;
import org.entcore.common.sql.Sql;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

public class ResourceServiceSqlImpl extends SqlCrudService implements ResourceService {

	public ResourceServiceSqlImpl() {
		super("rbs", "resource");
	}

	@Override
	public void listResources(final List<String> groupsAndUserIds, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		query.append("SELECT r.*,")
			.append(" json_agg(row_to_json(row(rs.member_id,rs.action)::rbs.share_tuple)) as shared,")
			.append(" array_to_json(array_agg(m.group_id)) as groups ")
			.append(" FROM rbs.resource AS r")
			.append(" INNER JOIN rbs.resource_type AS t ON r.type_id = t.id")
			.append(" LEFT JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id")
			.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
			.append(" LEFT JOIN rbs.members AS m ON (rs.member_id = m.id AND m.group_id IS NOT NULL)");

		query.append(" WHERE rs.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()));
		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}

		query.append(" OR r.owner = ? ");
		values.add(user.getUserId());

		query.append(" OR ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()));
		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}

		query.append(" OR t.owner = ?");
		values.add(user.getUserId());

 		// A local administrator of a given school can see all resources of the school's types, even if he is not owner or manager of these types or resources
		List<String> scope = getLocalAdminScope(user);
		if (scope!=null && !scope.isEmpty()) {
			query.append(" OR t.school_id IN ").append(Sql.listPrepared(scope.toArray()));
			for (String schoolId : scope) {
				values.addString(schoolId);
			}
		}

		query.append(" GROUP BY r.id")
			.append(" ORDER BY r.id");

		Sql.getInstance().prepared(query.toString(), values, parseShared(handler));
	}

	@Override
	public void updateResource(final String id, final JsonObject data,
			final Handler<Either<String, JsonObject>> handler) {

		StringBuilder sb = new StringBuilder();
		JsonArray values = new JsonArray();
		for (String attr : data.getFieldNames()) {
			if ("was_available".equals(attr)) {
				continue;
			}
			sb.append(attr).append(" = ?, ");
			values.add(data.getValue(attr));
		}
		unsetFieldIfNull(data, sb, values, "max_delay");
		unsetFieldIfNull(data, sb, values, "min_delay");

		StringBuilder query = new StringBuilder();
		query.append("UPDATE rbs.resource")
			.append(" SET ")
			.append(sb.toString())
			.append("modified = NOW()")
			.append(" WHERE id = ?")
			.append(" RETURNING id, name");
		values.add(parseId(id));

		Sql.getInstance().prepared(query.toString(), values, validUniqueResultHandler(handler));
	}

	private void unsetFieldIfNull(final JsonObject data, final StringBuilder sb,
			final JsonArray values, final String fieldname){

		if(data.getField(fieldname) == null) {
			sb.append(fieldname).append(" = ?, ");
			values.add(null);
		}
	}

	@Override
	public void getBookingOwnersIds(long resourceId, Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		query.append("SELECT DISTINCT owner FROM rbs.booking ")
			.append(" WHERE resource_id = ?")
			.append(" AND status IN (?, ?)")
			.append(" AND start_date >= now()")
			.append(" AND is_periodic = ?");

		JsonArray values = new JsonArray();
		values.add(resourceId)
			.add(CREATED.status())
			.add(VALIDATED.status())
			.add(false);

		Sql.getInstance().prepared(query.toString(), values, validResultHandler(handler));
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void getDelaysAndTypeProperties(long resourceId, Handler<Either<String, JsonObject>> handler) {
		StringBuilder query = new StringBuilder("SELECT r.min_delay, r.max_delay, t.owner, t.school_id,");

		// Subquery to return managers
		query.append(" (SELECT json_agg(DISTINCT ts.member_id) FROM rbs.resource_type_shares AS ts")
			.append(" WHERE ts.resource_id = t.id")
			.append(" AND ts.action = 'net-atos-entng-rbs-controllers-ResourceTypeController|shareJsonSubmit') AS managers");

		query.append(" FROM rbs.resource AS r")
			.append(" INNER JOIN rbs.resource_type AS t ON r.type_id = t.id")
			.append(" WHERE r.id = ?");

		JsonArray values = new JsonArray().add(resourceId);

		Sql.getInstance().prepared(query.toString(), values, validUniqueResultHandler(handler));
	}

}
