/*
 * Copyright © Région Nord Pas de Calais-Picardie,  Département 91, Région Aquitaine-Limousin-Poitou-Charentes, 2016.
 *
 * This file is part of OPEN ENT NG. OPEN ENT NG is a versatile ENT Project based on the JVM and ENT Core Project.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation (version 3 of the License).
 *
 * For the sake of explanation, any module that communicate over native
 * Web protocols, such as HTTP, with OPEN ENT NG is outside the scope of this
 * license and could be license under its own terms. This is merely considered
 * normal use of OPEN ENT NG, and does not fall under the heading of "covered work".
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package net.atos.entng.rbs.events;

import org.entcore.common.sql.Sql;
import org.entcore.common.sql.SqlResult;
import org.entcore.common.sql.SqlStatementsBuilder;
import org.entcore.common.user.RepositoryEvents;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import fr.wseduc.webutils.Either;

import java.util.ArrayList;
import java.util.List;


public class RbsRepositoryEvents implements RepositoryEvents {

	private static final Logger log = LoggerFactory.getLogger(RbsRepositoryEvents.class);

	@Override
	public void exportResources(String exportId, String userId, JsonArray groups, String exportPath, String locale, String host, final Handler<Boolean> handler) {
		// TODO Implement export
		log.error("Event [exportResources] is not implemented");
	}

	@Override
	public void deleteGroups(JsonArray groups) {
		if (groups != null && groups.size() > 0){
			final JsonArray groupsIds = new fr.wseduc.webutils.collections.JsonArray();
			for (Object o : groups) {
				if (!(o instanceof JsonObject)) continue;
				final JsonObject j = (JsonObject) o;
				groupsIds.add(j.getString("group"));
			}

			List<String> listGroupId = new ArrayList<>();
			for (int i = 0; i < groupsIds.size(); i++) {
				listGroupId.add(groupsIds.getString(i));
			}

			if (groupsIds.size() > 0) {
				SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
				statementsBuilder.prepared("DELETE FROM rbs.groups WHERE id IN " + Sql.listPrepared(listGroupId.toArray()), groupsIds);
				Sql.getInstance().transaction(statementsBuilder.build(), SqlResult.validRowsResultHandler(new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> event) {
						if (event.isRight()) {
							log.info("Removed share on ResourceTypes and Resources for groups : " + groupsIds.getList().toString());
						} else {
							log.error("Failed to remove share on ResourceTypes and Resources for groups : " + groupsIds.getList().toString() + " Error message : " + event.left().getValue());
						}
					}
				}));
			}
		}
	}

	@Override
	public void deleteUsers(JsonArray users) {
		//FIXME: anonymization is not relevant
		if (users != null && users.size() > 0){
			final JsonArray userIds = new fr.wseduc.webutils.collections.JsonArray();
			for (Object o : users) {
				if (!(o instanceof JsonObject)) continue;
				final JsonObject j = (JsonObject) o;
				userIds.add(j.getString("id"));
			}

			List<String> listUserId = new ArrayList<>();
			for (int i = 0; i < userIds.size(); i++) {
				listUserId.add(userIds.getString(i));
			}

			if (userIds.size() > 0) {
				SqlStatementsBuilder statementsBuilder = new SqlStatementsBuilder();
				statementsBuilder.prepared("DELETE FROM rbs.members WHERE user_id IN " + Sql.listPrepared(listUserId.toArray()), userIds);
				statementsBuilder.prepared("UPDATE rbs.users SET deleted = TRUE WHERE id IN " + Sql.listPrepared(listUserId.toArray()), userIds);
				Sql.getInstance().transaction(statementsBuilder.build(), SqlResult.validRowsResultHandler(new Handler<Either<String, JsonObject>>() {
					@Override
					public void handle(Either<String, JsonObject> event) {
						if (event.isRight()) {
							log.info("Removed share on ResourceTypes and Resources for users : " + userIds.getList().toString());
						} else {
							log.error("Failed to remove share on ResourceTypes and Resources for users : " + userIds.getList().toString() + " Error message : " + event.left().getValue());
						}
					}
				}));
			}
		}
	}

}
