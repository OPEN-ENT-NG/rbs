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

package net.atos.entng.rbs.service;

import fr.wseduc.webutils.Either;
import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.eventbus.Message;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;

public class UserServiceDirectoryImpl implements UserService {

	private final EventBus eb;
	private static final String DIRECTORY_ADDRESS = "directory";

	public UserServiceDirectoryImpl(EventBus eb) {
		this.eb = eb;
	}

	@Override
	public void getUsers(final JsonArray userIds, final JsonArray groupIds,
	                     final Handler<Either<String, JsonArray>> handler) {

		JsonObject action = new JsonObject()
				.putString("action", "list-users")
				.putArray("userIds", userIds)
				.putArray("groupIds", groupIds);

		eb.send(DIRECTORY_ADDRESS, action, validResultHandler(handler));
	}


	@Override
	public void getUserMails(final Set<String> userIds,
	                         final Handler<Map<String, String>> handler) {

		final Map<String, String> userMailById = new HashMap<>();
		for (final String userId : userIds) {

			JsonObject action = new JsonObject()
					.putString("action", "getUser")
					.putString("userId", userId);

			eb.send(DIRECTORY_ADDRESS, action, new Handler<Message<JsonObject>>() {
				@Override
				public void handle(Message<JsonObject> res) {
					if ("ok".equals(res.body().getString("status"))) {
						JsonObject user = res.body().getObject("result", new JsonObject());
						String email = user.getString("email");
						userMailById.put(userId, email);
					} else {
						userMailById.put(userId, "");
					}
					// to be improved once VertX 3 available...
					if (userMailById.size() == userIds.size()) {
						handler.handle(userMailById);
					}
				}
			});



					/*new Handler<Either<String, JsonObject>>() {
				@Override
				public void handle(Either<String, JsonObject> event) {
					if (event.isRight() && event.right().getValue() != null) {
						JsonObject value = event.right().getValue();
						String email = value.getString("email");
						userMailById.put(userId, email);
					} else {
						userMailById.put(userId, "");
					}
					// to be improved once VertX 3 available...
					if (userMailById.size() == userIds.size()) {
						handler.handle(userMailById);
					}
				}
			}));*/
		}
	}

}
