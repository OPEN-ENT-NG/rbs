package fr.wseduc.rbs.service;

import static fr.wseduc.rbs.BookingStatus.CREATED;
import static fr.wseduc.rbs.BookingStatus.VALIDATED;
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
			.append(" json_agg(row_to_json(row(ts.member_id,ts.action)::rbs.share_tuple)) as shared,")
			.append(" array_to_json(array_agg(m.group_id)) as groups ")
			.append(" FROM rbs.resource AS r")
			.append(" LEFT JOIN rbs.resource_shares AS rs ON r.id = rs.resource_id")
			.append(" INNER JOIN rbs.resource_type AS t ON r.type_id = t.id")
			.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
			.append(" LEFT JOIN rbs.members AS m ON (ts.member_id = m.id AND m.group_id IS NOT NULL)");

		query.append(" WHERE rs.member_id IN ")
			.append(Sql.listPrepared(groupsAndUserIds.toArray()));
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

	@Override
	public void getDelays(long resourceId, Handler<Either<String, JsonObject>> handler) {
		String query = "SELECT min_delay, max_delay FROM rbs.resource WHERE id = ?";
		JsonArray values = new JsonArray().add(resourceId);

		Sql.getInstance().prepared(query, values, validUniqueResultHandler(handler));
	}

}
