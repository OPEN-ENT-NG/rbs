package net.atos.entng.rbs.service;

import static org.entcore.common.neo4j.Neo4jResult.validResultHandler;

import org.vertx.java.core.Handler;
import org.vertx.java.core.eventbus.EventBus;
import org.vertx.java.core.json.JsonArray;
import org.vertx.java.core.json.JsonObject;

import fr.wseduc.webutils.Either;

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

}
