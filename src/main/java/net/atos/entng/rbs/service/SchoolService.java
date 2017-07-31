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
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import java.util.HashMap;
import java.util.Map;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;


public class SchoolService {
	private final EventBus eb;
	private static final String DIRECTORY_ADDRESS = "directory";

	public SchoolService(EventBus eb) {
		this.eb = eb;
	}

	public void getSchoolNames(final Handler<Either<String, Map<String, String>>> handler) {
		JsonObject action = new JsonObject()
				.putString("action", "list-structures");

		eb.send(DIRECTORY_ADDRESS, action, validResultHandler(new Handler<Either<String, JsonArray>>() {
					@Override
					public void handle(Either<String, JsonArray> event) {
						if (event.isRight()) {
							HashMap<String, String> schoolNamesByIds = new HashMap<>();
							JsonArray value = event.right().getValue();
							if (value != null) {
								for (Object o : value) {
									if (!(o instanceof JsonObject)) continue;
									JsonObject school = (JsonObject) o;
									String schoolId = school.getString("id");
									String schoolName = school.getString("name");
									schoolNamesByIds.put(schoolId, schoolName);
								}
							}
							handler.handle(new Either.Right<String, Map<String, String>>(schoolNamesByIds));
						} else {
							handler.handle(new Either.Left<String, Map<String, String>>(event.left().getValue()));
						}
					}
				})
		);
	}

}
