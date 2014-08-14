package fr.wseduc.rbs.filters;

import static org.entcore.common.sql.Sql.parseId;

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

import fr.wseduc.rbs.controllers.ResourceController;
import fr.wseduc.webutils.http.Binding;

public class TypeAndResourceAppendPolicy implements ResourcesProvider {

	@Override
	public void authorize(final HttpServerRequest request, final Binding binding, final UserInfos user, final Handler<Boolean> handler) {
		SqlConf conf = SqlConfs.getConf(ResourceController.class.getName());
		String id = request.params().get(conf.getResourceIdLabel());
		String bookingId = request.params().get("bookingId");
		boolean hasBooking = (bookingId != null && !bookingId.trim().isEmpty());

		if (id != null && !id.trim().isEmpty() && (parseId(id) instanceof Integer)) {
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
			query.append("SELECT count(*) FROM rbs.resource AS r")
					.append(" INNER JOIN rbs.resource_type AS t")
					.append(	" ON r.type_id = t.id");

			if (hasBooking) {
				// Additional join when parameter bookingId is used
				query.append(" INNER JOIN rbs.booking AS b")
					.append(    " ON b.resource_id = r.id ");
			}

			query.append(" LEFT JOIN rbs.resource_type_shares AS ts")
					.append(	" ON t.id = ts.resource_id")
					.append(" LEFT JOIN rbs.resource_shares AS rs")
					.append(	" ON r.id = rs.resource_id")
					.append(" WHERE ((ts.member_id IN ")
					.append(Sql.listPrepared(groupsAndUserIds.toArray()))
					.append(" AND ts.action = ?)");
			for (String groupOruser : groupsAndUserIds) {
				values.add(groupOruser);
			}
			values.add(sharedMethod);
			query.append(" OR (rs.member_id IN ")
				.append(Sql.listPrepared(groupsAndUserIds.toArray()))
				.append(" AND rs.action = ?)");
			for (String groupOruser : groupsAndUserIds) {
				values.add(groupOruser);
			}
			values.add(sharedMethod);
			query.append(" OR (t.owner = ?)");
			values.add(user.getUserId());
			query.append(" OR (r.owner = ?)");
			values.add(user.getUserId());
			query.append(") AND r.id = ?");
			values.add(Sql.parseId(id));
			if (hasBooking) {
				query.append(" AND b.id = ?");
				values.add(Sql.parseId(bookingId));
			}

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
