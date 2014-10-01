package fr.wseduc.rbs.service;

import static org.entcore.common.sql.SqlResult.parseShared;

import java.util.List;

import org.entcore.common.sql.Sql;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;

import fr.wseduc.webutils.Either;

public class ResourceTypeServiceSqlImpl implements ResourceTypeService {

	@Override
	public void list(final List<String> groupsAndUserIds, final UserInfos user,
			final Handler<Either<String, JsonArray>> handler) {

		StringBuilder query = new StringBuilder();
		JsonArray values = new JsonArray();

		query.append("SELECT t.*,")
			.append(" json_agg(row_to_json(row(ts.member_id,ts.action)::rbs.share_tuple)) as shared,")
			.append(" array_to_json(array_agg(m.group_id)) as groups ")
			.append(" FROM rbs.resource_type AS t")
			.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
			.append(" LEFT JOIN rbs.members AS m ON (ts.member_id = m.id AND m.group_id IS NOT NULL)");

		query.append(" WHERE ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()));
		for (String groupOruser : groupsAndUserIds) {
			values.add(groupOruser);
		}
		query.append(" OR t.owner = ?")
			.append(" GROUP BY t.id")
			.append(" ORDER BY t.id");
		values.add(user.getUserId());

		Sql.getInstance().prepared(query.toString(), values, parseShared(handler));
	}

}
