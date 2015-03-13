package net.atos.entng.rbs.events;

import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.RepositoryEvents;
import org.vertx.java.core.Handler;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;
import org.vertx.java.core.logging.Logger;
import org.vertx.java.core.logging.impl.LoggerFactory;

import fr.wseduc.webutils.Either;


public class RbsRepositoryEvents implements RepositoryEvents {

	private static final Logger log = LoggerFactory.getLogger(RbsRepositoryEvents.class);
	private final boolean shareOldGroupsToUsers;

	public RbsRepositoryEvents(boolean shareOldGroupsToUsers) {
		this.shareOldGroupsToUsers = shareOldGroupsToUsers;
	}

	@Override
	public void exportResources(String exportId, String userId, JsonArray groups, String exportPath, String locale, String host, final Handler<Boolean> handler) {
		// TODO Implement export
		log.error("Event [exportResources] is not implemented");
	}

	@Override
	public void deleteGroups(JsonArray groups) {
		if (shareOldGroupsToUsers) {
			// TODO Implement shareOldGroupsToUsers
			log.error("Case [shareOldGroupsToUsers] for Event [deleteGroups] is not implemented");
			return;
		}

		if (groups != null && groups.size() > 0){
			final JsonArray groupsIds = new JsonArray();
			for (Object o : groups) {
				if (!(o instanceof JsonObject)) continue;
				final JsonObject j = (JsonObject) o;
				groupsIds.add(j.getString("group"));
			}
			if (groupsIds.size() > 0) {
				SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
				statementsBuilder.prepared("DELETE FROM rbs.groups WHERE id IN " + Sql.listPrepared(groupsIds.toArray()), groupsIds);
				Sql.getInstance().transaction(statementsBuilder.build(), SqlResult.validRowsResultHandler(new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> event) {
						if (event.isRight()) {
							log.info("Removed share on ResourceTypes and Resources for groups : " + groupsIds.toList().toString());
						} else {
							log.error("Failed to remove share on ResourceTypes and Resources for groups : " + groupsIds.toList().toString() + " Error message : " + event.left().getValue());
						}
					}
				}));
			}
		}
	}

	@Override
	public void deleteUsers(JsonArray users) {
		//TODO: Implement anonymization
		if (users != null && users.size() > 0){
			final JsonArray userIds = new JsonArray();
			for (Object o : users) {
				if (!(o instanceof JsonObject)) continue;
				final JsonObject j = (JsonObject) o;
				userIds.add(j.getString("id"));
			}
			if (userIds.size() > 0) {
				SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
				statementsBuilder.prepared("DELETE FROM rbs.members WHERE user_id IN " + Sql.listPrepared(userIds.toArray()), userIds);
				statementsBuilder.prepared("UPDATE rbs.users SET deleted = TRUE WHERE id IN " + Sql.listPrepared(userIds.toArray()), userIds);
				Sql.getInstance().transaction(statementsBuilder.build(), SqlResult.validRowsResultHandler(new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> event) {
						if (event.isRight()) {
							log.info("Removed share on ResourceTypes and Resources for users : " + userIds.toList().toString());
						} else {
							log.error("Failed to remove share on ResourceTypes and Resources for users : " + userIds.toList().toString() + " Error message : " + event.left().getValue());
						}
					}
				}));
			}
		}
	}

}
