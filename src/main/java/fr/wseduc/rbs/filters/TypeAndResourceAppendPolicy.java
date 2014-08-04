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

import fr.wseduc.rbs.Rbs;
import fr.wseduc.rbs.controllers.ResourceController;
import fr.wseduc.webutils.http.Binding;

public class TypeAndResourceAppendPolicy implements ResourcesProvider {
	
	@Override
	public void authorize(final HttpServerRequest request, final Binding binding, final UserInfos user, final Handler<Boolean> handler) {
		SqlConf conf = SqlConfs.getConf(ResourceController.class.getName());
		String id = request.params().get(conf.getResourceIdLabel());

		if (id != null && !id.trim().isEmpty()) {
			request.pause();
			// Method
			String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");
			
			// Groups and users
			final List<String> groupsAndUserIds = new ArrayList<>();
			groupsAndUserIds.add(user.getUserId());
			if (user.getProfilGroupsIds() != null) {
				groupsAndUserIds.addAll(user.getProfilGroupsIds());
			}
			
			// Query
			StringBuilder query = new StringBuilder();
			JsonArray values = new JsonArray();
			query.append("SELECT count(*)");
			query.append(" FROM " + conf.getSchema() + conf.getTable());
			query.append(" INNER JOIN "	+ conf.getSchema() + Rbs.RESOURCE_TYPE_TABLE);
			query.append(	" ON " 		+ conf.getSchema() + conf.getTable() + ".type_id = "	+ conf.getSchema() + Rbs.RESOURCE_TYPE_TABLE + ".id");
			query.append(" LEFT JOIN "	+ conf.getSchema() + Rbs.RESOURCE_TYPE_SHARE_TABLE);
			query.append(	" ON "		+ conf.getSchema() + Rbs.RESOURCE_TYPE_TABLE + ".id = "	+ conf.getSchema() + Rbs.RESOURCE_TYPE_SHARE_TABLE + ".resource_id");
			query.append(" LEFT JOIN "	+ conf.getSchema() + conf.getShareTable());
			query.append(	" ON "		+ conf.getSchema() + conf.getTable() + ".id = "			+ conf.getSchema() + conf.getShareTable() + ".resource_id");
			query.append(" WHERE ((" 	+ conf.getSchema() + Rbs.RESOURCE_TYPE_SHARE_TABLE	+ ".member_id IN "	+ Sql.listPrepared(groupsAndUserIds.toArray()) + " AND " + conf.getSchema() + Rbs.RESOURCE_TYPE_SHARE_TABLE + ".action = ?)");
			for (String groupOruser : groupsAndUserIds) {
				values.add(groupOruser);
			}
			values.add(sharedMethod);
			query.append(" OR (" 		+ conf.getSchema() + conf.getShareTable()			+ ".member_id IN "	+ Sql.listPrepared(groupsAndUserIds.toArray()) + " AND " + conf.getSchema() + conf.getShareTable() + ".action = ?)");
			for (String groupOruser : groupsAndUserIds) {
				values.add(groupOruser);
			}
			values.add(sharedMethod);
			query.append(" OR (" 		+ conf.getSchema() + Rbs.RESOURCE_TYPE_TABLE + ".owner = ?)");
			values.add(user.getUserId());
			query.append(" OR (" 		+ conf.getSchema() + conf.getTable() + ".owner = ?)");
			values.add(user.getUserId());
			query.append(") AND " 		+ conf.getSchema() + conf.getTable() + ".id = ?");
			values.add(Sql.parseId(id));
			
			// Execute
			Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> message) {
					request.resume();
					Long count = SqlResult.countResult(message);
					handler.handle(count != null && count > 0);
				}
			});
		} else {
			handler.handle(false);
		}
	}

}
