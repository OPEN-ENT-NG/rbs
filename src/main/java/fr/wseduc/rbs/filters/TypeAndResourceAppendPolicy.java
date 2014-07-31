package fr.wseduc.rbs.filters;

import java.util.ArrayList;
import java.util.List;

import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.UserInfos;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.http.Binding;

public class TypeAndResourceAppendPolicy implements ResourcesProvider {
	
	private static final String TYPE_TABLE = "resource_type";
	private static final String TYPE_SHARE_TABLE = "resource_type_shares";
	
	@Override
	public void authorize(final HttpServerRequest request, final Binding binding, final UserInfos user, final Handler<Boolean> handler) {
		SqlConf conf = SqlConfs.getConf(binding.getServiceMethod().substring(0, binding.getServiceMethod().indexOf('|')));
		String id = request.params().get(conf.getResourceIdLabel());

		if (id != null && !id.trim().isEmpty()) {
			// Method
			String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");
			
			// Groups and users
			List<String> gu = new ArrayList<>();
			gu.add(user.getUserId());
			if (user.getProfilGroupsIds() != null) {
				gu.addAll(user.getProfilGroupsIds());
			}
			final Object[] groupsAndUserIds = gu.toArray();
			
			// Query
			StringBuilder query = new StringBuilder();
			JsonArray values = new JsonArray();
			query.append("SELECT count(*)");
			query.append(" FROM " + conf.getSchema() + conf.getTable());
			query.append(" INNER JOIN "	+ conf.getSchema() + TYPE_TABLE);
			query.append(	" ON " 		+ conf.getSchema() + conf.getTable() + ".type_id = "	+ conf.getSchema() + TYPE_TABLE + ".id");
			query.append(" LEFT JOIN "	+ conf.getSchema() + TYPE_SHARE_TABLE);
			query.append(	" ON "		+ conf.getSchema() + TYPE_TABLE + ".id = "				+ conf.getSchema() + TYPE_SHARE_TABLE + ".resource_id");
			query.append(" LEFT JOIN "	+ conf.getSchema() + conf.getShareTable());
			query.append(	" ON "		+ conf.getSchema() + conf.getTable() + ".id = "			+ conf.getSchema() + conf.getShareTable() + ".resource_id");
			query.append(" WHERE ((" + conf.getSchema() + TYPE_SHARE_TABLE + ".member_id IN "	+ Sql.listPrepared(groupsAndUserIds) + " AND " + conf.getSchema() + TYPE_SHARE_TABLE + ".action = ?) ");
			values.add(groupsAndUserIds).add(sharedMethod);
			query.append(" OR (" + conf.getSchema() + conf.getShareTable() + "member_id IN "	+ Sql.listPrepared(groupsAndUserIds) + " AND " + conf.getSchema() + conf.getShareTable() + ".action = ?) ");
			values.add(groupsAndUserIds).add(sharedMethod);
			query.append(" OR (" + conf.getSchema() + TYPE_TABLE + ".owner = ?))");
			values.add(user.getUserId());
			query.append(" OR (" + conf.getSchema() + conf.getTable() + ".owner = ?))");
			values.add(user.getUserId());
			query.append(" AND " + conf.getSchema() + conf.getTable() + ".id = ?");
			values.add(Sql.parseId(id));
			
			// Execute
			Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> message) {
					Long count = SqlResult.countResult(message);
					handler.handle(count != null && count > 0);
				}
			});
		} else {
			handler.handle(false);
		}
	}

}
