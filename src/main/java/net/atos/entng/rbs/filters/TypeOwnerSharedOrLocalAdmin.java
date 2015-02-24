package net.atos.entng.rbs.filters;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.atos.entng.rbs.controllers.ResourceTypeController;

import org.entcore.common.http.filter.ResourcesProvider;
import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlConf;
import org.entcore.common.sql.SqlConfs;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.user.DefaultFunctions;
import org.entcore.common.user.UserInfos;
import org.entcore.common.user.UserInfos.Function;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.http.HttpServerRequest;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import fr.wseduc.webutils.Either;
import fr.wseduc.webutils.http.Binding;

/* Authorize if
 * user is the resourceType's owner,
 * or he has been shared rights,
 * or he is a local administrator for the school_id of the resourceType
 */
public class TypeOwnerSharedOrLocalAdmin implements ResourcesProvider {

	private static final Logger log = LoggerFactory.getLogger(TypeOwnerSharedOrLocalAdmin.class);

	@Override
	public void authorize(final HttpServerRequest request, Binding binding, final UserInfos user,
			final Handler<Boolean> handler) {

		SqlConf conf = SqlConfs.getConf(ResourceTypeController.class.getName());
		String resourceTypeId = request.params().get(conf.getResourceIdLabel());

		if(resourceTypeId == null || resourceTypeId.trim().isEmpty()) {
			handler.handle(false);
		}
		else {
			request.pause();
			String sharedMethod = binding.getServiceMethod().replaceAll("\\.", "-");

			final List<String> groupsAndUserIds = new ArrayList<>();
			groupsAndUserIds.add(user.getUserId());
			if (user.getGroupsIds() != null && !user.getGroupsIds().isEmpty()) {
				groupsAndUserIds.addAll(user.getGroupsIds());
			}

			StringBuilder query = new StringBuilder();
			query.append("SELECT count(*) AS count, ")
				.append(" (SELECT school_id FROM rbs.resource_type where id = ?) AS school_id") // subquery to return school_id even if count = 0
				.append(" FROM rbs.resource_type AS t")
				.append(" LEFT JOIN rbs.resource_type_shares AS ts ON t.id = ts.resource_id")
				.append(" WHERE ((ts.member_id IN ").append(Sql.listPrepared(groupsAndUserIds.toArray()))
				.append("AND ts.action = ?)");
			JsonArray values = new JsonArray().add(Sql.parseId(resourceTypeId));
			for (String groupOrUserId : groupsAndUserIds) {
				values.add(groupOrUserId);
			}
			values.add(sharedMethod);

			query.append(" OR t.owner = ?)")
				.append(" AND t.id = ?");
			values.add(user.getUserId())
				.add(Sql.parseId(resourceTypeId));

			Sql.getInstance().prepared(query.toString(), values, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> message) {
					request.resume();
					Either<String, JsonObject> result = SqlResult.validUniqueResult(message);

					if(result.isLeft()) {
						log.error(result.left());
						handler.handle(false);
					}
					else {
						JsonObject value = result.right().getValue();
						if(value == null) {
							log.error("Error : SQL request in filter TypeOwnerSharedOrLocalAdmin should not return null");
							handler.handle(false);
							return;
						}

						// Authorize if user is the resourceType's owner or if he has been shared rights
						Long count = (Long) value.getNumber("count", 0);
						if(count != null && count > 0) {
							handler.handle(true);
							return;
						}

						// Else authorize if user is a local admin for the resourceType's school_id
						String schoolId = value.getString("school_id", null);
						if(schoolId == null || schoolId.trim().isEmpty()) {
							log.error("school_id not found");
							handler.handle(false);
							return;
						}

						Map<String, UserInfos.Function> functions = user.getFunctions();
						if (functions != null  && functions.containsKey(DefaultFunctions.ADMIN_LOCAL)) {
							Function adminLocal = functions.get(DefaultFunctions.ADMIN_LOCAL);
							if(adminLocal != null && adminLocal.getScope() != null && adminLocal.getScope().contains(schoolId)) {
								handler.handle(true);
								return;
							}
						}

						handler.handle(false);
					}

				}
			});
		}

	}

}
